package me.pqpo.methodhook;

import android.support.v4.util.Pair;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by qiulinmin on 7/10/17.
 */

public final class HookManager {

    private HookManager(){}

    public static HookManager get() {
        return InstanceHolder.sInstance;
    }

    private static class InstanceHolder {
        private static HookManager sInstance = new HookManager();
    }

    private Map<Pair<String, String>, MethodHook> methodHookMap = new ConcurrentHashMap<>();

    public void hookMethod(Method originMethod, Method hookMethod) {
        if (originMethod == null || hookMethod == null) {
            throw new IllegalArgumentException("argument cannot be null");
        }
//        if (!Modifier.isStatic(hookMethod.getModifiers())) {
//            throw new IllegalArgumentException("hook method must be static");
//        }
        Pair<String, String> key = Pair.create(hookMethod.getDeclaringClass().getName(), hookMethod.getName());
        if (methodHookMap.containsKey(key)) {
            MethodHook methodHook = methodHookMap.get(key);
            methodHook.restore();
        }
        MethodHook methodHook = new MethodHook(originMethod, hookMethod);
        methodHookMap.put(key, methodHook);
        methodHook.hook();
    }

    public void callOrigin(Object receiver, Object... args) {
        StackTraceElement stackTrace = Thread.currentThread().getStackTrace()[3];
        String className = stackTrace.getClassName();
        String methodName = stackTrace.getMethodName();
        MethodHook methodHook = methodHookMap.get(Pair.create(className, methodName));
        if (methodHook != null) {
            try {
                methodHook.callOrigin(receiver, args);
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

}
