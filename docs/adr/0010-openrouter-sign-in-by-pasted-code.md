# 0010. OpenRouter sign-in by a pasted one-time code

Date: 2026-11-03. Status: accepted.

The user asked for OpenRouter sign-in so people don't have to make and paste a key. The builder uses OpenRouter's OAuth PKCE flow in its headless form ([docs](https://openrouter.ai/docs/guides/overview/auth/oauth)):

1. **Sign in with OpenRouter** opens `https://openrouter.ai/auth` in the browser with an S256 `code_challenge` and `key_label=Smallwright`, and no `callback_url`.
2. After the person approves, OpenRouter shows a one-time code (single use, 10 minutes).
3. The person pastes it into **Code from OpenRouter** and taps **Use this code**.
4. The builder posts the code, its verifier and `S256` to `https://openrouter.ai/api/v1/auth/keys`, with the same attribution headers as chat requests.
5. The returned `key` must pass the pasted-key check (`ModelKeyInput.openRouterKey`). It is then saved by `CredentialStore.openRouter` exactly like a pasted key.

Pasting a key and the model-server option stay.

## Why no callback

- AGENTS.md rules out network listeners, so a localhost callback is out.
- OpenRouter documents only http(s) callbacks, with localhost on any port. A custom `smallwright://` scheme is not documented.
- An https App Link would need a site the app verifies against, such as the website. OpenRouter would then attribute the app to that address, while chat requests name `https://github.com/Mouhand-Kaddo/smallwright`.
- A GitHub URL callback would land the code on GitHub, where the app can't read it.
- The cost is one copy and paste.

## Details

- The verifier is 32 random bytes from `SecureRandom`, held in memory for one sign-in. If Android ends the app while the browser is open, the code can't be used, and the person signs in again.
- OpenRouter's 400 and 403 replies mean the code was refused. They are told apart from network failures and from a reply without a valid key.
- Sign-in replaces the "Get a key at openrouter.ai/keys" link in setup.
