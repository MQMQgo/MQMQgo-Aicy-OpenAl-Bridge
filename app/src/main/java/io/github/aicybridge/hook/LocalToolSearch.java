package io.github.aicybridge.hook;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import io.github.libxposed.api.XposedInterface.ExceptionMode;

/** Local metadata recall for the verified Aicy release. Does not execute or authorize tools. */
public final class LocalToolSearch {
    private static final String PREFIX = "com.flyme.agentos.agent.";
    private static final int MAX_QUERY_CHARS = 2048;
    private static final int MAX_METADATA_CHARS = 8192;
    private static final Set<String> STOP_WORDS = stopWords();

    private LocalToolSearch() { }

    /** Install only after the custom API configuration has been validated and enabled. */
    public static void install(BridgeModule module, ClassLoader loader) throws Throwable {
        Class<?> config = Class.forName(PREFIX + "domain.tool.recall.y", false, loader);
        Class<?> strategy = Class.forName(PREFIX + "domain.tool.recall.ExceptionStrategy", false, loader);
        Class<?> context = Class.forName(PREFIX + "core.tool.J", false, loader);
        Class<?> tool = Class.forName(PREFIX + "core.tool.S", false, loader);
        Class<?> matcher = Class.forName(PREFIX + "domain.tool.recall.p", false, loader);

        // Exact 13.1.1 ABI, including the R8-retained Kotlin default-argument mask.
        Constructor<?> configConstructor = config.getDeclaredConstructor(
                boolean.class, boolean.class, boolean.class, boolean.class, boolean.class,
                boolean.class, boolean.class, String.class, int.class, int.class, int.class,
                float.class, int.class, int.class, float.class, int.class, float.class,
                int.class, int.class, int.class, String.class, float.class, int.class,
                strategy, boolean.class, String.class, int.class);
        Method match = matcher.getDeclaredMethod("i", context, ArrayList.class);
        if (!Set.class.isAssignableFrom(match.getReturnType())) {
            throw new NoSuchMethodException("Verified rule matcher must return Set");
        }
        Method query = context.getDeclaredMethod("b");
        Field maxCount = context.getDeclaredField("j");
        if (query.getReturnType() != String.class || maxCount.getType() != int.class) {
            throw new NoSuchFieldException("Verified recall context ABI changed");
        }
        maxCount.setAccessible(true);
        Method getName = tool.getMethod("getName");
        Method getDescription = tool.getMethod("getDescription");
        Method keywords = tool.getMethod("u");
        Method examples = tool.getMethod("o");

        // Validate every reflected member before installing either hook.
        // Registered hooks stay inert if any later registration fails.
        AtomicBoolean installed = new AtomicBoolean(false);
        module.hook(match).setId("aicybridge.local-tool-metadata").intercept(chain -> {
            if (!installed.get()) return chain.proceed();
            Object original = chain.proceed();
            if (!(original instanceof Set)) throw new IllegalStateException("Rule matcher returned a non-Set");
            Object ctx = chain.getArg(0);
            Object candidates = chain.getArg(1);
            if (!(candidates instanceof ArrayList)) throw new IllegalStateException("Rule candidates must be ArrayList");
            int maximum = Math.max(0, maxCount.getInt(ctx));
            LinkedHashSet<Object> result = new LinkedHashSet<>((Set<?>) original);
            int remaining = Math.max(0, maximum - result.size());
            String text = (String) query.invoke(ctx);
            if (remaining == 0 || text == null || text.trim().isEmpty()) return result;
            String normalizedQuery = normalize(text, MAX_QUERY_CHARS);
            Set<String> queryTerms = terms(normalizedQuery);
            if (queryTerms.isEmpty()) return result;
            ArrayList<Hit> hits = new ArrayList<>();
            int order = 0;
            for (Object candidate : (ArrayList<?>) candidates) {
                int position = order++;
                if (result.contains(candidate)) continue;
                String name = normalize((String) getName.invoke(candidate), MAX_METADATA_CHARS);
                String description = normalize((String) getDescription.invoke(candidate), MAX_METADATA_CHARS);
                Collection<?> keywordValues = collection(keywords.invoke(candidate));
                Collection<?> exampleValues = collection(examples.invoke(candidate));
                double score = score(normalizedQuery, queryTerms, name, description,
                        keywordValues, exampleValues);
                if (score > 0) hits.add(new Hit(candidate, score, position));
            }
            Collections.sort(hits, Comparator.comparingDouble((Hit hit) -> hit.score).reversed()
                    .thenComparingInt(hit -> hit.order));
            for (int i = 0; i < Math.min(remaining, hits.size()); i++) result.add(hits.get(i).tool);
            return result;
        });

        module.hook(configConstructor).setExceptionMode(ExceptionMode.PASSTHROUGH).setId("aicybridge.local-recall-config").intercept(chain -> {
            if (!installed.get()) return chain.proceed();
            Object[] args = chain.getArgs().toArray();
            args[1] = true;  // ruleEnabled
            args[3] = false; // vectorEnabled
            args[4] = false; // rerankEnabled
            args[6] = false; // queryRewriteEnabled (the model-based second pass)
            // Otherwise Kotlin defaults can override the values, notably queryRewriteEnabled=true.
            args[26] = ((Integer) args[26]) & ~(2 | 8 | 16 | 64);
            return chain.proceed(args);
        });
        installed.set(true);
    }

    private static Collection<?> collection(Object value) {
        if (!(value instanceof Collection)) throw new IllegalStateException("Tool metadata must be a collection");
        return (Collection<?>) value;
    }

    private static double score(String query, Set<String> queryTerms, String name,
                                String description, Collection<?> keywords, Collection<?> examples) {
        double score = overlap(queryTerms, name) * 8.0 + overlap(queryTerms, description);
        if (name.length() >= 2 && query.contains(name)) score += 30;
        int count = 0;
        for (Object value : keywords) {
            if (!(value instanceof String)) throw new IllegalStateException("Tool keyword must be a string");
            if (count++ >= 64) break;
            String keyword = normalize((String) value, MAX_METADATA_CHARS);
            if (keyword.length() >= 2 && query.contains(keyword)) score += 20;
            score += overlap(queryTerms, keyword) * 4;
        }
        double bestExample = 0;
        count = 0;
        for (Object value : examples) {
            if (!(value instanceof String)) throw new IllegalStateException("Tool example must be a string");
            if (count++ >= 32) break;
            String example = normalize((String) value, MAX_METADATA_CHARS);
            bestExample = Math.max(bestExample, overlap(queryTerms, example) * 3);
        }
        return score + bestExample;
    }

    private static int overlap(Set<String> queryTerms, String metadata) {
        if (metadata.isEmpty()) return 0;
        Set<String> metadataTerms = terms(metadata);
        int count = 0;
        for (String term : queryTerms) if (metadataTerms.contains(term)) count++;
        return count;
    }

    private static String normalize(String value, int maximum) {
        if (value == null) return "";
        String bounded = value.substring(0, Math.min(value.length(), maximum));
        return Normalizer.normalize(bounded, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replace('_', ' ').replace('-', ' ');
    }

    /** Latin words and Chinese character bigrams avoid matching every tool on a generic single 字. */
    private static Set<String> terms(String text) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (latin(c)) {
                int start = i++;
                while (i < text.length() && latin(text.charAt(i))) i++;
                String token = text.substring(start, i);
                if (token.length() > 1 && !STOP_WORDS.contains(token)) result.add(token);
            } else if (han(c)) {
                int start = i++;
                while (i < text.length() && han(text.charAt(i))) i++;
                if (i - start == 1) {
                    result.add(text.substring(start, i));
                } else {
                    for (int j = start; j + 1 < i; j++) {
                        String token = text.substring(j, j + 2);
                        if (!STOP_WORDS.contains(token)) result.add(token);
                    }
                }
            } else { i++; }
        }
        return result;
    }

    private static boolean latin(char value) {
        return value >= 'a' && value <= 'z' || value >= '0' && value <= '9';
    }

    private static boolean han(char value) {
        return value >= '\u3400' && value <= '\u9fff';
    }

    private static Set<String> stopWords() {
        HashSet<String> result = new HashSet<>();
        Collections.addAll(result, "the", "an", "to", "for", "of", "in", "on", "off", "and",
                "or", "with", "please", "help", "me", "my", "turn", "open", "close",
                "帮我", "给我", "一下", "请帮", "打开", "关闭", "一下", "一个", "什么", "如何", "怎么");
        return Collections.unmodifiableSet(result);
    }

    private static final class Hit {
        final Object tool;
        final double score;
        final int order;
        Hit(Object tool, double score, int order) {
            this.tool = tool; this.score = score; this.order = order;
        }
    }
}
