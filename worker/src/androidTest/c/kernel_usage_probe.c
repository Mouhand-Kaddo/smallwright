#include <jni.h>
#include <sys/resource.h>

/* Trusted test-harness reader. No generated code, process launch or compiler hooks. */
JNIEXPORT jlongArray JNICALL
Java_dev_smallwright_worker_KernelResourceUsageProbe_readNativeUsage(JNIEnv *env, jclass type) {
    (void)type;
    struct rusage self_usage;
    struct rusage child_usage;
    if (getrusage(RUSAGE_SELF, &self_usage) != 0 ||
        getrusage(RUSAGE_CHILDREN, &child_usage) != 0) {
        jclass error = (*env)->FindClass(env, "java/io/IOException");
        if (error != NULL) (*env)->ThrowNew(env, error, "Kernel resource query failed");
        return NULL;
    }
    jlong values[4] = {
        (jlong)self_usage.ru_maxrss,
        (jlong)child_usage.ru_maxrss,
        (jlong)child_usage.ru_utime.tv_sec * 1000000 + child_usage.ru_utime.tv_usec,
        (jlong)child_usage.ru_stime.tv_sec * 1000000 + child_usage.ru_stime.tv_usec
    };
    jlongArray result = (*env)->NewLongArray(env, 4);
    if (result != NULL) (*env)->SetLongArrayRegion(env, result, 0, 4, values);
    return result;
}
