#include <jni.h>
#include <string.h>
//
// Created by qiulinmin on 7/7/17.
//
static const char* kClassMethodHookChar = "me/pqpo/methodhook/MethodHook";

static struct {
    jmethodID m1;
    jmethodID m2;
    size_t methodSize;
} methodHookClassInfo;


static long methodHook(JNIEnv* env, jclass type, jobject srcMethodObj, jobject destMethodObj) {
    void* srcMethod = reinterpret_cast<void*>(env -> FromReflectedMethod(srcMethodObj));
    void* destMethod = reinterpret_cast<void*>(env -> FromReflectedMethod(destMethodObj));
    int* backupMethod = new int[methodHookClassInfo.methodSize];
    memcpy(backupMethod, srcMethod, methodHookClassInfo.methodSize);
    memcpy(srcMethod, destMethod, methodHookClassInfo.methodSize);
    return reinterpret_cast<long>(backupMethod);
}

static jobject methodRestore(JNIEnv* env, jclass type, jobject srcMethod, jlong methodPtr) {
    int* backupMethod = reinterpret_cast<int*>(methodPtr);
    void* artMethodSrc = reinterpret_cast<void*>(env -> FromReflectedMethod(srcMethod));
    memcpy(artMethodSrc, backupMethod, methodHookClassInfo.methodSize);
    delete []backupMethod;
    return srcMethod;
}

static JNINativeMethod gMethods[] = {
        {
                "hook_native",
                "(Ljava/lang/reflect/Method;Ljava/lang/reflect/Method;)J",
                (void*)methodHook
        },
        {
                "restore_native",
                "(Ljava/lang/reflect/Method;J)Ljava/lang/reflect/Method;",
                (void*)methodRestore
        }
};

extern "C"
JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv *env = nullptr;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_4) != JNI_OK) {
        return JNI_FALSE;
    }
    jclass classEvaluateUtil = env->FindClass(kClassMethodHookChar);
    if(env -> RegisterNatives(classEvaluateUtil, gMethods, sizeof(gMethods)/ sizeof(gMethods[0])) < 0) {
        return JNI_FALSE;
    }
    methodHookClassInfo.m1 = env -> GetStaticMethodID(classEvaluateUtil, "m1", "()V");
    methodHookClassInfo.m2 = env -> GetStaticMethodID(classEvaluateUtil, "m2", "()V");
    methodHookClassInfo.methodSize = reinterpret_cast<size_t>(methodHookClassInfo.m2) - reinterpret_cast<size_t>(methodHookClassInfo.m1);
    return JNI_VERSION_1_4;
}
