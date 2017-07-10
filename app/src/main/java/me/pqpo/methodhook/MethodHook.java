package me.pqpo.methodhook;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by qiulinmin on 7/7/17.
 */

public class MethodHook {

    public static void m1(){}
    public static void m2(){}

    private Method srcMethod;
    private Method hookMethod;

    private long backupMethodPtr;

    public MethodHook(Method src, Method dest) {
        srcMethod = src;
        hookMethod = dest;
        srcMethod.setAccessible(true);
        hookMethod.setAccessible(true);
    }

    public void hook() {
        if (backupMethodPtr == 0) {
            backupMethodPtr = hook_native(srcMethod, hookMethod);
        }
    }

    public void restore() {
        if (backupMethodPtr != 0) {
            restore_native(srcMethod, backupMethodPtr);
            backupMethodPtr = 0;
        }
    }

    public void callOrigin(Object receiver, Object... args) throws InvocationTargetException, IllegalAccessException {
        if (backupMethodPtr != 0) {
            restore();
            srcMethod.invoke(receiver, args);
            hook();
        } else {
            srcMethod.invoke(receiver, args);
        }
    }

    private static native long hook_native(Method src, Method dest);
    private static native Method restore_native(Method src, long methodPtr);

    static {
        System.loadLibrary("method-hook-lib");
    }

}
