# OpenRouter sign-in on the A33 (2026-11-03)

Design: [ADR 0010](../adr/0010-openrouter-sign-in-by-pasted-code.md).

## Build

- Revision: `99838b5` plus the changes in this commit.
- Release-signed builder `cdf7ead6a18578fef1a0ef33418dbf6b6c7aec9e05c235cbfd2d15ab8487dc9a`, version code 4. It was installed with `adb install -r` over 0.1.1, which kept the app's data. The worker stayed at 0.1.1.
- Host: 779 controller tests pass, including `OpenRouterSignInTest`:
  - The challenge matches RFC 7636's S256 example.
  - The page URL has no `callback_url`.
  - The exchange body carries the trimmed code, the verifier and `S256`, with no Authorization header.
  - 400 and 403 replies are refusals.
  - Malformed codes are refused without a request.
  - Replies without a valid `sk-or-` key, and server errors, are failures, not refusals.

  Lint and Spotless pass.

## Phone (Samsung Galaxy A33 5G, Android 16)

| Step | Who | Result |
|---|---|---|
| Settings with no key saved | agent | **Sign in with OpenRouter** above the key field |
| Tap it | agent | Samsung Internet opened on its first-run screen |
| Accept the browser's terms, sign in to OpenRouter, approve, copy the code, paste it into **Code from OpenRouter**, tap **Use this code** | user | Settings: "A key is saved on this phone." and "Key saved on this phone."; the code field closed |
| Ask "In one sentence, what does this app do? Do not change anything." with DeepSeek V4.1 Flash (approved paid model) | agent | Read 3 files, answered in one sentence, "I changed nothing." |

The key bytes were never read or printed. The user can see or delete the new "Smallwright" key at openrouter.ai/keys.

## Not covered

On the phone, these were not tried:
- a refused or expired code;
- no network during the exchange;
- the onboarding version of the same controls.

The host tests cover the first two.
