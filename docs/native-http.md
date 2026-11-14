# Native HTTP isolation

The hook creates a separate Ktor client with the target APK's own transport classes. It does not reuse the app's authenticated HTTP factory, shared engine, request plugin configuration, or cookie storage. TLS verification remains at the transport defaults. Both Ktor and OkHttp redirects are disabled, so the configured API credential is not forwarded through a redirected request.

This mapping is specific to the supplied Aicy 13.1.1 APK:

- package: `com.meizu.voiceassistant`
- version code: `13001001`
- SHA-256: `eea6d390e6b1cbc3fb151354d354fe14516d44c6edd06fef9ca7571bc89d69af`

## Verified native symbols

The names below are original DEX names, not JADX's display aliases.

| Symbol | Descriptor / purpose |
| --- | --- |
| `io.ktor.client.f.a` | `(Lio/ktor/client/engine/okhttp/a;Lkotlin/jvm/functions/Function1;)Lio/ktor/client/b;` — creates a fresh client config and owned OkHttp engine |
| `io.ktor.client.engine.okhttp.a.a` | `Lio/ktor/client/engine/okhttp/a;` — singleton factory |
| `io.ktor.client.c.e` | `Z` — enables installation of Ktor's `HttpRedirect` plugin; set to false |
| `io.ktor.client.c.d` | `Lkotlin/jvm/functions/Function1;` — engine configuration callback |
| `io.ktor.client.engine.okhttp.c.a` | `Lkotlin/jvm/functions/Function1;` — OkHttp builder configuration callback |
| `bi.U.i` | `Z` — OkHttp `followRedirects`; set to false |
| `bi.U.j` | `Z` — OkHttp `followSslRedirects`; set to false |

The Ktor constructor installs `io.ktor.client.plugins.w.d` only when user config `e` is true. The plugin's DEX initializer identifies it as `HttpRedirect`. The OkHttp client constructor copies builder fields `i` and `j` into client fields `h` and `i`; `RetryAndFollowUpInterceptor.buildRedirectRequest` checks those two client fields before constructing redirected requests. NativeHttpClients checks each expected field and method type before creating a client, and its transport callback fails if the redirect flags cannot be disabled.

## App authentication and streaming

The app's `com.flyme.agentos.auth.e` factory installs its `BizAuthPlugin`. The plugin has an origin check before it requests a Flyme business token and replaces Authorization. This module uses a fresh Ktor configuration instead of relying on that origin check or copying the auth plugin.

The native OpenAI client requires a non-null streaming holder (`com.flyme.agentos.llm.impl.openai.f`). Its stream coroutine rejects a null holder. The adapter supplies the isolated client to the holder's existing client field before use and retains the native acquire/release behavior. Its retained original client factory callback is not invoked while that field holds the isolated client.

The ordinary OpenAI client does not own its supplied HTTP client. The module must retain and reuse the isolated client deliberately and close it when retiring it; constructing a transport for each request would create unnecessary engines and connection pools.

## Evidence and verification limits

v0.4.1 removes the global RealCall hook introduced in v0.4.0. Logging attaches only to the module's isolated client, using a small module-owned concrete interceptor that preserves native checked exceptions. No shared OkHttp execution method is hooked during application startup. Metadata failures still finish the row as unknown, and the database initialization fix remains. See [call logs](call-logs.md).

The symbols and behavior were inspected from `classes3.dex` and `classes6.dex` using targeted JADX decompilation and DEX disassembly. Vendor source code is not included in this repository. These checks establish the static mapping; actual target-device loading and network behavior still require device validation.
