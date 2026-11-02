# OpenRouter C1 protocol slice (2026-09-26)

The user selected OpenRouter and authorized only free models on an account with no credits.
The controller now has a masked, phone-local key field and an explicit `Send (free)` action
after a revision-bound context preview. The connected S23 Ultra received a real model reply
after the user saved a key directly in the app and approved a free request. No real credential
was copied into the laptop, tools, or repository, and no paid use is authorized.

## Official contract checked

- [North Mini Code free](https://openrouter.ai/cohere/north-mini-code%3Afree/api):
  the controller now requests `cohere/north-mini-code:free`, a fixed zero-priced
  model advertising `tools` and `tool_choice` support. Availability and rate limits
  still apply. The earlier `openrouter/free` router selected different free models
  across retries, which made response diagnosis nondeterministic.
- [Chat completions](https://openrouter.ai/docs/api/api-reference/chat/create-a-chat-completion):
  POST `https://openrouter.ai/api/v1/chat/completions`, bearer authorization, messages and
  bounded completion tokens; this client chooses `stream:false`.
- [Client tools](https://openrouter.ai/docs/guides/features/tool-calling): function arguments
  arrive as JSON strings. The client validates them; model text/tool requests confer no
  execution authority. No hosted tools, plugins, file service or remote execution is requested.
- [Usage accounting](https://openrouter.ai/docs/cookbook/administration/usage-accounting):
  response usage includes prompt, completion, total tokens and cost. The parser requires
  bounded consistent token counts, and rejects any supplied cost that is nonnumeric or nonzero.
  Missing cost is not proof of actual billing. The fixed `:free` request model is
  the free-only routing control; there is no paid fallback.
- [Limits](https://openrouter.ai/docs/api_reference/limits): free usage can be rate limited;
  authentication/account/rate-limit failures stop the attempt without retries or paid fallback.

## Implemented boundary

One `ModelClient` instance is one run and permits one attempted request. `MainActivity` obtains
a revision-bound context preview and explicit `Send (free)` approval; this class does not create
or validate that UI approval. The dialog displays the exact context included as the user
message; the protocol client wraps it in fixed OpenRouter request fields. Input is capped at
128 KiB, serialized request at 256 KiB, response
at 128 KiB, completion at 2,048 tokens, and response JSON nesting at 16. Connect/read timeouts
are 10/30 seconds, plus a 45-second disconnect deadline. Cancellation disconnects the active
connection. Actual Android socket/DNS cancellation timing still needs runtime verification.

All HTTP redirects are disabled. Only the fixed HTTPS OpenRouter endpoint is opened. Errors
expose fixed messages/status codes, never raw response bodies, prompts or underlying exception
messages. Caller-owned credential bytes are copied and that copy is wiped; the caller must wipe
its own bytes. `HttpURLConnection` requires an immutable authorization String that cannot be
reliably erased from memory. No key is stored by this client or included in the request body.

The text-response action advertises only `read_file` for `counter-alpha`, approved source Java/XML paths, bounded
offset and limit. Exactly one tool call may be returned. Unknown names, extra/missing argument
keys, wrong types, duplicate JSON keys, invalid paths and truncated replies fail closed. Parsed
tools are immutable requests only. An offline controller dispatcher now validates the current
revision, redacts source, and performs bounded reads, but no model run invokes it yet.
The separate Generate action advertises only `replace_source`, binds it to the approved
revision, and accepts a strictly parsed proposal. Its UI requires a nonempty request,
displays the current context before Send, and rechecks that context after the reply. One
valid proposal commits source through the revision store and invokes the real isolated
worker. A verified signed APK enables a separate Install action. Source remains committed
if the build fails; no model text can mark a build successful. Conversation continuation,
streaming and automatic repair are not implemented.

## Evidence and remaining work

`ModelProtocolTest` uses deterministic simulated HTTP responses and an injected connection
boundary; it never opens a socket. Test-first cycles reproduced missing APIs, then a second
request escaping the run budget, then sanitized HTTP errors losing their useful status. Five
host tests pass through direct JDK/JUnit invocation, including response/tool validation,
redirect rejection, body bounds, cancellation before connection, deadline disconnection of a
stalled body and truncated-body rejection. Interrupted streaming is addressed by explicitly
not requesting streams and rejecting incomplete response JSON; there is no streaming parser.

Run the integrated checks with:

```sh
source scripts/env.sh
./gradlew :controller:testDebugUnitTest --tests 'dev.smallwright.agent.ModelProtocolTest' --offline --no-daemon --max-workers=1
```

The emulator showed a masked key field. A dummy key was saved into the app's private
`no_backup/credentials` directory and removed. The dialog displayed `Local only`, `Cancel`,
and `Send (free)` choices. Selecting Send with no key reported that no model request was sent.
The real key was pasted directly into the updated app on the phone; it was not put in chat,
terminal commands, repository files, or desktop clipboard. Saving the key did not send it.

On the S23 Ultra, the updated controller APK installed successfully, bound the worker, and
reported `OpenRouter free model replied` after the user's test. The masked field was empty
after save. The user then supplied screenshots: the 09:08 app reply displayed 1,207 tokens,
matching the dashboard's 806 input plus 401 output tokens and `$0.00` cost. Two earlier free
model rows also displayed `$0.00`. This confirms a real Android model-response path and
provider-displayed zero cost for those calls; it is not an independent billing audit. The
request was empty, so the response summarized the counter source and asked for a task. It
did not edit files or build an app. The agent made no second request.
The later generation checkpoint passed 61 host tests, lint, controller/test APK assembly,
and an emulator test that sent a simulated proposal through the real worker. Subsequent
free-only S23 Ultra requests exposed a strict tool-call key-set check. After pinning
North Mini Code and accepting extra metadata outside the required tool-call fields,
a live `replace_source` proposal committed the source and produced a verified signed
APK through the isolated phone worker. The app reported 1,420 provider tokens and
artifact SHA-256 `387d4380be5da81b0be2504ded78180bf149423f5793bf59b8f8cc11e741989a`.
The source editor visibly contained `Add one`; the user subsequently installed and
ran the generated app and confirmed the `Add one` button. The generation checkpoint
passed 70 host tests, lint and both APK assemblies. Phone UI automation returned
only filtered fixed labels/bounds; no key,
raw source, provider body or complete UI hierarchy was exported. The C1 live read-tool
effect gate remains open. Later C2 checkpoints wired a bounded SQLite run journal
into Generate, tested a simulated compiler repair through the real phone worker,
and verified interrupted-run journal recovery on an API 36 emulator. No live
provider-supplied compiler repair or real process-kill recovery has been observed.
User text feedback now has a separate preview and free-only update action, but
no live feedback update or screenshot/crash import has been tested. See
`docs/STATUS.md` for the exact evidence and APK hashes. Gson 2.11.0 is pinned
from the existing cache; its tagged Apache
license and JAR hash are under `toolchain/licenses/gson/`.
