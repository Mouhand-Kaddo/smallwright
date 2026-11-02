# Toolchain rights inquiry drafts — 2026-09-27

These are drafts for review. No inquiry has been sent and no permission has been obtained. The project owner must choose a sender and authorize sending. Do not attach signing keys, credentials, user projects, or generated user APKs.

## 1. API library licensing and SDK use

Suggested routing: `opensource-licensing@google.com`, the address named in the [AOSP prebuilt SDK licensing note](https://android.googlesource.com/platform/prebuilts/sdk/+/3b7daadc4e54d51439e5ae9b4dbd96cda8b6104e/Android.bp). This is a request for the correct owner/contact, not a documented commercial licensing application or guaranteed support channel. That note belongs to a different prebuilt repository from the fullsdk artifact below. If redirected, ask for the responsible Android SDK/AOSP owner. A clarification about one component cannot waive another right holder's terms.

**Subject:** Licensing clarification for public Android API definitions in an on-device Android app builder

We are developing an Android application that lets users create native Android apps on their own phones. A controller owns projects and per-project signing keys. A separate-UID compiler worker has no Internet permission. Generated source is compiled on the phone using ECJ, D8 and AAPT2, then signed locally and installed through Android's user confirmation. A cloud model may assist with source editing; it does not build or execute the app. The product is intended for public distribution, potentially commercially. We are not requesting permission to copy Google's branding or to claim Google endorsement.

We want complete public framework and Java API definitions for a selected Android SDK level. They would be compiler inputs only, not loaded into our controller or copied as implementations into generated apps. Resource compilation also requires the matching public framework resource definitions.

The reference platform is API 36 revision 2, build 13352765. The local `android.jar` has SHA-256 `d9eb9da824d9e247a352f570f01e1169e725b2954bca9e283a71786c59b59f9a`; its Git blob is `e560471790469782b41614eac5f290f5388d0d79`. The accompanying `android-stubs-src.jar` has SHA-256 `f06372cb9bdbe4dececd35e5cb7b61fec01bb521dcd8a2424489f659b1e9322a`. Both match files in [the public fullsdk revision 2 update](https://android.googlesource.com/platform/prebuilts/fullsdk/platforms/+/1b45636761b0dbab9f80779add8fc85ab94b80fb%5E%21/). We recognize that public availability alone is not a redistribution grant.

Could you clarify or direct us to authoritative material on these questions?

1. Which source manifests, component licenses, notices and corresponding-source packages apply to this exact binary and source archive, including the public framework resources? Can the public API library be redistributed under those component licenses through SDK agreement section 3.5, once their conditions are met?
2. If we use the Google-distributed SDK package inside this product, does section 3.2's restriction on developing another SDK apply to this app-building use? Please distinguish publisher bundling from a user's direct installation of the official package for local builds.
3. If existing component grants are insufficient for the Google-distributed artifact, is there a written permission/licensing route for distributing it inside our signed compiler worker, including updates? What scope, conditions, source/notice duties and third-party exclusions would apply? We have found no published price or standard partnership requirement for this use.
4. For a library generated independently from AOSP source under its applicable open source licenses, is there an authoritative component/source manifest and public API generation recipe you recommend? We understand that independently applicable open source grants do not inherently require a special Google agreement; we want to avoid misidentifying an SDK prebuilt as independently licensed source.
5. If official package acquisition by each user is a supported alternative, what download endpoints, license presentation/acceptance, artifact verification and update behavior are appropriate for an Android client? We would not treat download or acceptance as permission to redistribute.

We can provide a small artifact/source inventory and architecture description. We are seeking confirmation of applicable grants and duties, or routing for written permission where needed; we are not asking you to approve third-party rights you do not hold.

## 2. Separate Play distribution question

Route through [Play Console Help](https://support.google.com/googleplay/android-developer/answer/12085295?hl=en) using the product owner's account, if a Play edition is pursued. No account action or submission has been performed.

**Subject:** Policy eligibility of a native on-device Android app builder

Please assess whether the following product can qualify for `REQUEST_INSTALL_PACKAGES` and whether any additional restrictions apply: users describe/edit native app source; a fixed compiler bundled in our signed APK compiles that source entirely on the user's phone; a separate package signed with a per-project key is installed only after user action and Android confirmation. The controller never loads generated code into itself. Executable compiler updates would arrive only through Play. Library inputs may be downloaded for compilation into a separately installed generated app, subject to their licenses; they are not run as compiler plugins or loaded into the controller.

Our core use is app creation and testing, not a file manager. We do not want to misclassify it as another category to obtain the install permission. Please clarify eligibility under the current installation, executable-download and policy-coverage rules, including a companion worker installation if we retain two APKs. We can supply a demonstration once the reviewed toolchain is ready. If direct installation is ineligible, is user export/open through the system file flow acceptable for this product? We recognize that this changes the UX and does not by itself settle the other policies.

## 3. Generated-app registration and long-term installation UX

Route through the [Android Developer Console Help Center linked by the official FAQ](https://developer.android.com/developer-verification/guides/faq). This is separate from SDK licensing and Play review.

**Subject:** Supported registration flow for apps generated and signed on the user's phone

The intended production builder will create a distinct package name and signing identity per user project; the current test harness still uses a fixed generated package. Private keys remain on the phone. For the planned broader developer-verification enforcement, what supported flow can a phone-only authoring tool use for personal generated apps without requiring a desktop, a shared publisher signing key, or an upload of user project source?

The Console API documents public-certificate registration for previously unseen package names, and signed-APK ownership challenges for some existing names. Please clarify whether a native Android OAuth client can use the registration APIs directly (the current guide describes server-to-server use), account requirements for personal use, package/account quotas, limited-distribution support, and whether the builder publisher may register independent users' projects or must act under each user's account. Please also confirm any special treatment for apps compiled and installed on the same device; we have found no documented exemption to assume.

We would require explicit consent for any registration metadata transmission. Our current architecture permits cloud inference only, so an additional registration service or APK upload would need an explicit product-scope decision. We will not embed service-account secrets, upload signing keys, automate security-setting changes, or claim registration of the builder covers its generated apps.
