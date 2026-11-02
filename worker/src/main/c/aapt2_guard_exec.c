#include "child_process_policy.h"
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

/* Fixed compiler launcher; no shell interpretation, generated code or fallback. */
int main(int argc, char **argv) {
    if (argc < 2) return 124;
    char self[PATH_MAX], target[PATH_MAX], expected[PATH_MAX];
    ssize_t size = readlink("/proc/self/exe", self, sizeof(self) - 1);
    if (size <= 0 || (size_t)size >= sizeof(self) - 1) return 124;
    self[size] = '\0';
    char *name = strrchr(self, '/');
    if (name == NULL || strcmp(name + 1, "libaapt2_guard_exec.so") != 0) return 124;
    *name = '\0';
    int written = snprintf(expected, sizeof(expected), "%s/libaapt2_exec.so", self);
    if (written < 0 || (size_t)written >= sizeof(expected) ||
            realpath(argv[1], target) == NULL || strcmp(expected, target) != 0) return 124;
    if (install_child_process_policy() != 0) {
        fputs("AAPT2 process policy unavailable\n", stderr);
        return 125;
    }
    execv(target, argv + 1);
    fputs("AAPT2 guarded execution failed\n", stderr);
    return 126;
}
