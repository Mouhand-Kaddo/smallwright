# Codex Subscription Authentication Research

## Result

No supported Codex or ChatGPT subscription authentication integration for a
distributed Android client was identified in this bounded review. No host OAuth
token, browser session, client ID, or credential has been inspected, copied, or
used.

## Primary sources reviewed on 2026-09-13

- [OpenAI API overview](https://developers.openai.com/api/reference/overview#authentication)
  states that API requests use API keys or workload-identity-federation
  short-lived tokens, and explicitly says not to expose API keys in client-side
  apps.
- [OpenAI billing separation](https://help.openai.com/en/articles/8156019)
  states that ChatGPT and the API platform have separate billing systems.
- [OpenAI production best practices](https://developers.openai.com/api/docs/guides/production-best-practices)
  recommends server-side secret handling and says API billing is separate from
  ChatGPT subscriptions.

## Consequence for this alpha

The credential-free local compiler experiment does not need AI authentication.
Do not treat the current OpenCode/ChatGPT OAuth session as an application
credential. Do not embed a user API key in an APK. A future client-side model
feature requires a documented supported integration and a design that keeps
long-lived credentials out of the distributed controller APK. This is an open
product and security decision, not an implementation authorization.
