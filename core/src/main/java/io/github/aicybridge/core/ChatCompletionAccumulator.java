package io.github.aicybridge.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Accumulates Chat Completions deltas; tool argument fragments are not parsed prematurely. */
public final class ChatCompletionAccumulator {
    public static final class ToolCall {
        public final int index;
        public final String id;
        public final String name;
        public final String arguments;
        ToolCall(ToolState state) {
            index = state.index; id = state.id.toString();
            name = state.name.toString(); arguments = state.arguments.toString();
        }
    }

    public static final class Choice {
        public final int index;
        public final String role;
        public final String content;
        public final String refusal;
        public final String finishReason;
        public final List<ToolCall> toolCalls;
        Choice(ChoiceState state) {
            index = state.index; role = state.role; content = state.content.toString();
            refusal = state.refusal.toString(); finishReason = state.finishReason;
            List<ToolCall> tools = new ArrayList<>();
            for (ToolState tool : state.tools.values()) tools.add(new ToolCall(tool));
            toolCalls = Collections.unmodifiableList(tools);
        }
    }

    public static final class Snapshot {
        public final List<Choice> choices;
        public final boolean done;
        Snapshot(Map<Integer, ChoiceState> states, boolean done) {
            List<Choice> result = new ArrayList<>();
            for (ChoiceState choice : states.values()) result.add(new Choice(choice));
            choices = Collections.unmodifiableList(result);
            this.done = done;
        }
    }

    private static final class ToolState {
        final int index;
        final StringBuilder id = new StringBuilder();
        final StringBuilder name = new StringBuilder();
        final StringBuilder arguments = new StringBuilder();
        ToolState(int index) { this.index = index; }
    }

    private static final class ChoiceState {
        final int index;
        String role;
        String finishReason;
        final StringBuilder content = new StringBuilder();
        final StringBuilder refusal = new StringBuilder();
        final Map<Integer, ToolState> tools = new TreeMap<>();
        ChoiceState(int index) { this.index = index; }
    }

    private final Map<Integer, ChoiceState> choices = new TreeMap<>();
    private final long maxAccumulatedChars;
    private long accumulatedChars;
    private boolean done;

    public ChatCompletionAccumulator() { this(8L * 1024 * 1024); }

    public ChatCompletionAccumulator(long maxAccumulatedChars) {
        if (maxAccumulatedChars < 1) throw new IllegalArgumentException("Limit must be positive");
        this.maxAccumulatedChars = maxAccumulatedChars;
    }

    public Snapshot acceptData(String data) throws ProtocolException, OpenAiException {
        try { return accept(new JSONObject(data)); }
        catch (JSONException e) { throw new ProtocolException("Invalid JSON in SSE data", e); }
    }

    public Snapshot accept(JSONObject chunk) throws ProtocolException, OpenAiException {
        if (done) throw new ProtocolException("Received a chunk after stream completion");
        if (chunk == null) throw new ProtocolException("Missing completion chunk");
        if (chunk.has("error") && !chunk.isNull("error")) throw OpenAiException.fromJson(0, chunk);
        Object rawChoices = chunk.opt("choices");
        if (!(rawChoices instanceof JSONArray)) throw new ProtocolException("Chunk choices must be an array");
        JSONArray items = (JSONArray) rawChoices;
        for (int i = 0; i < items.length(); i++) {
            Object item = items.opt(i);
            if (!(item instanceof JSONObject)) throw new ProtocolException("Choice must be an object");
            JSONObject value = (JSONObject) item;
            int index = index(value, "index");
            ChoiceState state = choices.get(index);
            if (state == null) {
                if (choices.size() >= 64) throw new ProtocolException("Too many completion choices");
                state = new ChoiceState(index);
                choices.put(index, state);
            }
            JSONObject delta = value.optJSONObject("delta");
            if (delta == null) throw new ProtocolException("Choice delta must be an object");
            if (state.finishReason != null && delta.length() > 0) {
                throw new ProtocolException("Received delta after choice finished");
            }
            String role = optionalString(delta, "role");
            if (role != null) {
                if (state.role != null && !state.role.equals(role)) throw new ProtocolException("Choice role changed");
                state.role = role;
            }
            append(state.content, optionalString(delta, "content"));
            append(state.refusal, optionalString(delta, "refusal"));
            if (delta.has("function_call") && !delta.isNull("function_call")) {
                throw new ProtocolException("Legacy function_call is unsupported; use tool_calls");
            }
            if (delta.has("tool_calls") && !delta.isNull("tool_calls")) {
                Object tools = delta.opt("tool_calls");
                if (!(tools instanceof JSONArray)) throw new ProtocolException("tool_calls must be an array");
                acceptTools(state, (JSONArray) tools);
            }
            String finish = optionalString(value, "finish_reason");
            if (finish != null) {
                if (state.finishReason != null && !state.finishReason.equals(finish)) {
                    throw new ProtocolException("Choice finish_reason changed");
                }
                state.finishReason = finish;
            }
        }
        return snapshot();
    }

    private void acceptTools(ChoiceState state, JSONArray tools) throws ProtocolException {
        for (int i = 0; i < tools.length(); i++) {
            Object rawTool = tools.opt(i);
            if (!(rawTool instanceof JSONObject)) throw new ProtocolException("Tool call must be an object");
            JSONObject value = (JSONObject) rawTool;
            int index = index(value, "index");
            ToolState tool = state.tools.get(index);
            if (tool == null) {
                if (state.tools.size() >= 64) throw new ProtocolException("Too many tool calls");
                tool = new ToolState(index);
                state.tools.put(index, tool);
            }
            String type = optionalString(value, "type");
            if (type != null && !"function".equals(type)) throw new ProtocolException("Unsupported tool call type");
            append(tool.id, optionalString(value, "id"));
            if (value.has("function") && !value.isNull("function")) {
                JSONObject function = value.optJSONObject("function");
                if (function == null) throw new ProtocolException("Tool function must be an object");
                append(tool.name, optionalString(function, "name"));
                append(tool.arguments, optionalString(function, "arguments"));
            }
        }
    }

    public Snapshot complete() throws ProtocolException {
        if (done) throw new ProtocolException("Duplicate stream completion");
        if (choices.isEmpty()) throw new ProtocolException("Stream completed without choices");
        for (ChoiceState state : choices.values()) {
            if (state.finishReason == null) throw new ProtocolException("Stream completed without finish_reason");
            for (ToolState tool : state.tools.values()) {
                if (tool.id.length() == 0 || tool.name.length() == 0) {
                    throw new ProtocolException("Stream completed with an incomplete tool call");
                }
            }
        }
        done = true;
        return snapshot();
    }

    public Snapshot snapshot() { return new Snapshot(choices, done); }

    private void append(StringBuilder target, String fragment) throws ProtocolException {
        if (fragment == null) return;
        accumulatedChars += fragment.length();
        if (accumulatedChars > maxAccumulatedChars) throw new ProtocolException("Completion exceeds size limit");
        target.append(fragment);
    }

    private static String optionalString(JSONObject object, String key) throws ProtocolException {
        Object value = object.opt(key);
        if (value == null || value == JSONObject.NULL) return null;
        if (!(value instanceof String)) throw new ProtocolException(key + " must be a string");
        return (String) value;
    }

    private static int index(JSONObject object, String key) throws ProtocolException {
        Object value = object.opt(key);
        if (!(value instanceof Number)) throw new ProtocolException(key + " must be an integer");
        double number = ((Number) value).doubleValue();
        if (!Double.isFinite(number) || number < 0 || number > 65535 || number != Math.floor(number)) {
            throw new ProtocolException(key + " is out of range");
        }
        return (int) number;
    }
}
