# Model authentication review (2026-09-25)

The compiler alpha requires no AI credential. Milestone C cannot make a live model call until
the user selects a supported provider/access route and separately approves any credential or
spend. Do not read host credentials, package them in an APK, reuse Codex login tokens, invent an
OAuth client ID, or add remote build/execution.

## Official OpenAI paths checked

- The [OpenAI API overview](https://developers.openai.com/api/reference/overview) documents
  API keys and workload-identity access tokens for API authentication. It explicitly warns
  against exposing API keys in client-side apps, so a key compiled into this Android APK is
  unacceptable. The local `CredentialStore` is a tested storage primitive, not approval to put
  a real OpenAI API key into a shipped client.
- [Workload identity federation](https://developers.openai.com/api/docs/guides/workload-identity-federation)
  maps a trusted workload's external identity to an API Platform service account. Its Codex
  variant is for managed ChatGPT workspaces and requires administrative configuration. The
  documented flow does not establish consumer Android app access through a Codex subscription.
- The [Agents API](https://developers.openai.com/api/docs/guides/agents-api/overview) can run
  an OpenAI-managed harness, including sandbox tools. That execution model cannot substitute
  for this project's on-phone source, tools, build and generated-app execution boundary.
- The [Codex app-server](https://learn.chatgpt.com/docs/app-server) is a documented way to
  embed Codex in a product and offers Codex-managed ChatGPT browser/device-code login. Its
  documentation does not establish an Android build/runtime path for the app-server or a
  standalone OAuth client contract for this controller. It is a candidate to investigate,
  not authorization to copy host Codex tokens, emulate its first-party client, or ship a
  guessed Android OAuth flow.

No official standalone public-client OAuth route for this Android controller was verified in
the sources above. This is a bounded finding, not proof that no such integration can ever exist.
Before C1's live call, choose one provider and verify its permitted mobile authentication
flow, endpoint contract, pricing and data handling against current primary documentation.
Then obtain separate user authorization for any credential and paid usage. Until then,
protocol/dispatcher work may use deterministic test inputs clearly labeled as simulated;
no simulated result is live-provider evidence.
