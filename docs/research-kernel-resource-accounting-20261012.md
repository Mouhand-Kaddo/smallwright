# Retained kernel resource accounting

The existing NDK r28c resource header is byte-identical to the independently
retrieved [Android 16 r2 Bionic header](https://android.googlesource.com/platform/bionic/+/android-16.0.0_r2/libc/include/sys/resource.h).
Both are 2,661 bytes, SHA256 `e2285f858bbab115d78deb39c8e9097604fa789e7a53521ac7694ce476d9c38e`.
The helper compiles for ARM64/minimum API31; physical execution here is API36,
without an API31 runtime claim. Local NDK version/notices remain recorded in
[the toolchain audit](toolchain-redistribution-audit.md).

In pinned [Android common kernel sys.c](https://android.googlesource.com/kernel/common/+/7be50f2e8f20fc2299069b28dea59a28e3abe20a/kernel/sys.c),
SELF reads the process RSS high-water, CHILDREN reads `cmaxrss`, and the kernel
converts pages to KiB. Pinned [exit.c](https://android.googlesource.com/kernel/common/+/7be50f2e8f20fc2299069b28dea59a28e3abe20a/kernel/exit.c)
updates the parent's child maximum while reaping, taking the larger child/descendant
maximum rather than summing their RSS. Child CPU counters accumulate separately.
The parent checked those sources; retained raw source hashes are in ignored
`toolchains/alpha-kernel-resources-20261012/primary-source-verification.json`.
Linux manual retrieval failed during the parent check; no fresh manual-read claim.

These sources explain why a waited-for child's peak survives disappearance of
its `/proc` directory. They do not identify Samsung's exact vendor kernel.
[Actual A33 tests](validation/kernel-resources-a33-20261012.md) independently
verify positive retained child usage and SELF against `/proc`.

The largest child value is not a simultaneous process-tree peak. Summing controller,
worker and largest-child individual RSS maxima is a conditional upper envelope
only under serial-child/no-unaccounted-process assumptions. Maxima can occur at
different times and shared pages can be counted repeatedly. Descendant concurrency,
unique physical memory, GPU memory and whole-device memory are not established
by this reader. Missing own process-group memory counters remain unavailable;
no global cgroup configuration was changed.
