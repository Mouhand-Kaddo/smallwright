# 0009. Besides OpenRouter, one OpenAI-compatible model server

Date: 2026-11-02. Status: accepted (untested against a real server until the
user runs it on the A33).

The user asked for other providers and chose any server that speaks OpenAI's
chat completions API: the person types its https base URL (for example
`https://api.openai.com/v1`, `https://api.deepseek.com/v1` or
`https://api.groq.com/openai/v1`) and its API key, then picks a model from the
server's `GET {base}/models` list or types its name. OpenRouter stays as it was.

AGENTS.md rules out a provider framework, so there are exactly two cases.
`ModelProvider` is either `OPENROUTER` or one model server made from a checked
address; a `ModelChoice` carries its provider, so the model taken at the tap on
Send also fixes where every request of the run goes. An OpenRouter model always
has a listed price; a server model never does, and it can only be made with a
server provider. Nothing else is configurable.

## The address and the key

- The address must be `https://` in printable ASCII, with a host and no name,
  password, query or fragment. It is normalized (lower-case host, no `:443`, no
  trailing slash, `..` resolved, never above the root) before it is saved.
- A server connection opens TLS only to a public internet address
  (`PublicAddress.SOCKETS`, as web pages use), so a typed address can't reach
  the phone itself or its local network. AGENTS.md allows cloud inference only.
- The key is 8 to 1024 characters of an OAuth bearer token (letters, digits and
  `-._~+/=`), read from the field into bytes without a `String`, like the
  OpenRouter key. It is kept by `CredentialStore.server`, in its own encrypted
  file under its own Android Keystore key, next to the unchanged OpenRouter
  slot. Saving a new address removes the old server key first, so a key is never
  sent to an address it was not saved for.
- Redirects are refused for the model list and for requests, as before.

## What a request to a server holds

The standard body: `model`, `messages`, `tools` with `tool_choice: "auto"`,
`stream: true` and `stream_options: {"include_usage": true}`. Nothing
OpenRouter-only: no `provider` routing, no `reasoning` object, no web search
tool, and no `max_tokens`, since a standard model list gives no reply ceiling.

- `reasoning_effort` is not sent. Servers that don't know it, or models that
  don't reason, may answer 400, and there is no safe rule for which accept it.
  The thinking level is hidden for server models; each server's default
  applies.
- `stream_options.include_usage` is sent so token counts sharpen when a run
  summarizes its earlier steps. A server that refuses it answers with a plain
  "Model server request rejected (HTTP 400)".
- A reply's `reasoning_content` (DeepSeek's field) is shown as the model's
  thinking and repeated in the next request's assistant message: DeepSeek's own
  API refuses a tool conversation in thinking mode with 400 when it is missing.
  The builder keeps at most the first 64K characters of it. OpenRouter replies
  never get reasoning back, as before.
- A streamed reply from a server has no silence limit. OpenRouter sends
  heartbeats, so 90 seconds of silence there still means a dead connection; a
  server promises none, and the user's rule is that thinking is never cut off.
  Stop ends a stuck request.

## What a reply from a server is checked for

The same envelope, tool-call and size checks as OpenRouter's. The reply's
`model` must be the picked name or a dated variant of it (the name, `-`, then
anything); OpenAI answers `gpt-4o` with `gpt-4o-2024-08-06`. Usage is counted
when the server reports prompt and completion tokens and is otherwise taken as
unreported, never as a failure. A server reports no cost, and the builder does
not guess one from tokens.

Failures name the "Model server", never OpenRouter: 401 authentication, 402
payment, 429 rate limit, other statuses, transport failures and mid-stream
errors. 408, 429, 500, 502, 503 and 504 are tried again as for OpenRouter, and
the retry step says "Model server is busy."

## OpenRouter only

- Web search (OpenRouter's server tool): not offered to a server model, as for
  free models. Reading a public page with `fetch_web` works with either.
- Icons and pictures: OpenRouter's image model is paid with the OpenRouter key.
  A server run still offers the tools; a call ends as a failed step and a tool
  result saying icons and pictures are made only with an OpenRouter model, and
  the server key is never sent to OpenRouter.
- Thinking level: shown only for OpenRouter models listed with reasoning.
- Spending: only OpenRouter reports a cost. A spending limit refuses Send with
  a server model, because the limit could not be kept; the step limit and stop
  when stuck work as before. No dollar amount is shown for a server run.
- Attached pictures: only for OpenRouter models listed as reading images. A
  standard model list does not say which models do, so Send with pictures is
  refused for a server model, and `look_at_app` declines to open the app, as
  for any model not listed as reading pictures.

## Considered options

- A list of named providers (OpenAI, DeepSeek, Groq, ...) with their quirks.
  Rejected: that is the provider framework AGENTS.md rules out.
- Sending `reasoning_effort` and retrying without it on 400. Rejected for now:
  a second request after a refusal is more code and a second charge.
- Passing reasoning back to every server. Rejected: only DeepSeek's field is
  known to need it, and OpenRouter keeps its own `reasoning_details` contract.

## Consequences

- The run's context check, revision checks, Stop, retries and background runs
  are unchanged; only where a request goes and how its reply is read differ.
- A server model's context is what its list says (`context_length` or
  `context_window`), else 131,072 tokens, so a run summarizes sooner than a
  larger model needs and a smaller one may answer 400.
- With no `max_tokens`, a server's own default reply length applies; a short
  default cuts long file writes off, and the run asks for smaller steps.
- The first physical check is OpenRouter's own OpenAI-compatible endpoint used
  as a server, with the approved DeepSeek V4.1 Flash.
