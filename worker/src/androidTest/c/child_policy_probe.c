#include "../../main/c/child_process_policy.h"
#include <pthread.h>
#include <signal.h>
#include <sched.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ptrace.h>
#include <sys/wait.h>
#include <unistd.h>

static int child_exit(void *ignored) { (void)ignored; return 0; }
static int same_process_thread;
static pid_t owner;
static void *thread_body(void *ignored) {
    (void)ignored;
    same_process_thread = getpid() == owner;
    errno = 0;
    pid_t child = fork();
    if (child == 0) _exit(0);
    if (child > 0) waitpid(child, NULL, 0);
    return (void *)(long)(child < 0 && errno == EPERM);
}

int main(int argc, char **argv) {
    owner = getpid();
    if (argc == 4 && strcmp(argv[1], "refuse-policy") == 0) {
        /* Real kernel denial of a later filter install in this test child only.
         * The production guard must refuse to exec its actual AAPT2 sibling. */
        struct sock_filter refuse[] = {
            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, arch)),
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, POLICY_ARCH, 1, 0),
            BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_KILL_PROCESS),
            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, nr)),
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_prctl, 0, 3),
            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, args[0])),
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, PR_SET_SECCOMP, 0, 1),
            BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ERRNO | EPERM),
            BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW),
        };
        const struct sock_fprog program = {
            .len = (unsigned short)(sizeof(refuse) / sizeof(refuse[0])),
            .filter = refuse,
        };
        if (prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0) != 0 ||
                prctl(PR_SET_SECCOMP, SECCOMP_MODE_FILTER, &program) != 0) return 123;
        execl(argv[2], argv[2], argv[3], "version", (char *)NULL);
        return 126;
    }
    if (argc == 1) {
        if (install_child_process_policy() != 0) return 125;
        char pid[24]; snprintf(pid, sizeof(pid), "%d", owner);
        execl("/proc/self/exe", argv[0], "after-exec", pid, NULL);
        return 126;
    }
    int baseline = argc == 2 && strcmp(argv[1], "baseline") == 0;
    if (!baseline && (argc != 3 || strcmp(argv[1], "after-exec") != 0 || atoi(argv[2]) != owner)) return 127;
    errno = 0;
    pid_t child = fork();
    int fork_denied = child < 0 && errno == EPERM;
    int fork_allowed = child > 0;
    if (child == 0) _exit(0);
    if (child > 0 && waitpid(child, NULL, 0) != child) return 121;
    pthread_t thread; void *thread_result;
    if (pthread_create(&thread, NULL, thread_body, NULL) != 0 ||
            pthread_join(thread, &thread_result) != 0) return 120;
    if (baseline) {
        if (!fork_allowed || !same_process_thread) return 122;
        errno = 0;
        struct clone_args args = {.exit_signal = SIGCHLD};
        long result = syscall(__NR_clone3, &args, sizeof(args));
        int observed_errno = result < 0 ? errno : 0;
        if (result == 0) _exit(0);
        if (result > 0 && waitpid((pid_t)result, NULL, 0) != result) return 121;
        printf("CHILD_POLICY_BASELINE forkAllowed=1 threadAllowed=1 clone3Available=%d clone3Errno=%d pid=%d\n",
                result > 0, observed_errno, owner);
        return 0;
    }
    /* A process clone using CLONE_VM (vfork-style) still must not be allowed. */
    errno = 0;
    _Alignas(16) char child_stack[16384];
    long clone_result = clone(child_exit, child_stack + sizeof(child_stack), CLONE_VM | SIGCHLD, NULL);
    int clone_denied = clone_result < 0 && errno == EPERM;
    if (clone_result > 0) waitpid((pid_t)clone_result, NULL, 0);
    errno = 0;
    struct clone_args clone3_args = {.exit_signal = SIGCHLD};
    long clone3_result = syscall(__NR_clone3, &clone3_args, sizeof(clone3_args));
    if (clone3_result == 0) _exit(0);
    if (clone3_result > 0) waitpid((pid_t)clone3_result, NULL, 0);
    int clone3_denied = clone3_result < 0 && errno == ENOSYS;
    errno = 0;
    long ptrace_result = ptrace(PTRACE_TRACEME, 0, NULL, NULL);
    int ptrace_denied = ptrace_result < 0 && errno == EPERM;
    printf("CHILD_POLICY_RESULT forkDenied=%d threadAllowed=%d threadForkDenied=%d cloneDenied=%d clone3ObservedEnosys=%d ptraceDenied=%d execSamePid=1 pid=%d\n",
            fork_denied, same_process_thread, (int)(long)thread_result,
            clone_denied, clone3_denied, ptrace_denied, owner);
    return 0;
}
