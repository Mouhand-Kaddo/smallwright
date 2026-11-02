# 0004. Generated apps are ordinary Android apps

Date: 2026-10-18. Status: accepted.

The original plan gave generated apps no network, no permissions, one screen
and no libraries. The user wants people to build anything Android allows,
with no extra warnings from us, because what a person builds is their own
responsibility, as with Claude Code. So the model may write the app's
manifest: permissions including Internet, more activities, services and
receivers. The harness keeps ownership of the package name, the signing
identity, `minSdk` and `targetSdk`, and refuses `sharedUserId`, the debuggable
flag, a `taskAffinity` other than the app's own package (which would let it join
another app's task) and anything that would weaken installation. Android's own permission
prompts still ask the phone's owner. Apps may use libraries the way an
Android developer adds dependencies. The builder can search Google's Maven
repository and Maven Central, and add, update or remove a library in the
project's library list at an exact version (the newest stable one when it is
added). Adding one is a step in the chat with no question, unless the person
turns on "ask before adding libraries" in Settings. The controller downloads
the library and what it depends on straight from those two repositories,
checks each file against its published checksum, and caches it on the phone
for offline builds, so the harness ships and redistributes no library.
Prebuilt native code inside a library is copied into the app for the phone's
CPU, uncompressed and aligned as Android requires; nothing is compiled from
C. Libraries that need compiler plugins or annotation processing (Jetpack
Compose, Room, Hilt, Dagger) are out: the worker runs no plugins or annotation
processors, and the builder sees the build error and picks another. JitPack and
other repositories are out: they don't publish checks of the same kind.
Projects may hold many Java and resource files.
Existing one-file projects become multi-file projects that hold one file.

The harness's own boundaries don't change. The OpenRouter key never reaches a
generated app, and the worker stays a separate UID with no Internet.
