package me.pqpo.methodhook;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by qiulinmin on 7/7/17.
 */

public class MethodHook {

    public static void m1(){}
    public static void m2(){}

    private Map<Method, Long> methodBackup = new ConcurrentHashMap<>();

    public void hook(Method src, Method dest) {
        if (src == null || dest == null) {
            return;
        }
        if (!methodBackup.containsKey(src)) {
            methodBackup.put(src, hook_native(src, dest));
        }
    }

    public void restore(Method src) {
        if (src == null) {
            return;
        }
        Long srcMethodPtr = methodBackup.get(src);
        if (srcMethodPtr != null) {
            methodBackup.remove(restore_native(src, srcMethodPtr));
        }
    }

    private static native long hook_native(Method src, Method dest);
    private static native Method restore_native(Method src, long methodPtr);

    static {
        System.loadLibrary("method-hook-lib");
    }

}
