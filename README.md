原文链接： [热修复之 Method Hook 原理分析](https://pqpo.me/2017/07/07/hotfix-method-hook/)

## 引言

目前国内大厂均开源了自己的 Android 热修复框架，阿里的《深入探索 Android 热修复技术原理》全面介绍了热修复技术的现状，原理与展望。一方面是阿里系为代表的底层方法替换，另一方面是以腾讯系为代表的类加载方案。前者支持立即生效，但是限制比较多；后者必须冷启动生效，相对较稳定，修复范围广。之前分析过微信的热修复框架 Tinker 即属于后者， [《Tinker 接入及源码分析》](https://pqpo.me/2017/01/07/tinker-analysis1/)。本篇文章主要分析以 AndFix 为代表的底层方法替换方案，并且实现了《深入探索 Android 热修复技术原理》中提到的方法替换新方案。

方法替换是 AndFix 的热修复方案的关键，虚拟机在加载一个类的时候会将类中方法解析成 ArtMethod 结构体，结构体中保存着一些运行时的必要信息以及需要执行的指令指针地址。那么我们只要在 native 层将原方法的 ArtMethod 结构体替换成新方法的结构体，那么执行原方法的时候便会执行到新方法的指令，完成了方法替换。

Andfix 中的关键代码如下：

```java 

public static void addReplaceMethod(Method src, Method dest) {
	try {
	      replaceMethod(src, dest);
	      initFields(dest.getDeclaringClass());
	} catch (Throwable e) {
	      Log.e(TAG, "addReplaceMethod", e);
	}
}

```

replaceMethod 是 native 方法，查看其实现：

```c++ 

static void replaceMethod(JNIEnv* env, jclass clazz, jobject src,
		jobject dest) {
	if (isArt) {
		art_replaceMethod(env, src, dest);
	} else {
		dalvik_replaceMethod(env, src, dest);
	}
}

```

ART 与 Dalvik 虚拟机分别有不同的实现，看 ART 虚拟机下的实现代码：

```c++ 

extern void __attribute__ ((visibility ("hidden"))) art_replaceMethod(
		JNIEnv* env, jobject src, jobject dest) {
    if (apilevel > 23) {
        replace_7_0(env, src, dest);
    } else if (apilevel > 22) {
	replace_6_0(env, src, dest);
    } else if (apilevel > 21) {
	replace_5_1(env, src, dest);
    } else if (apilevel > 19) {
	replace_5_0(env, src, dest);
    }else{
        replace_4_4(env, src, dest);
    }
}

```

由于不同的版本 ArtMethod 结构体参数会一样，所以不同的版本又有不同的实现，每种实现本地保留一份不同的结构体代码，我们看 6.0 的版本：

```c++ 

void replace_6_0(JNIEnv* env, jobject src, jobject dest) {
    art::mirror::ArtMethod* smeth = (art::mirror::ArtMethod*) env->FromReflectedMethod(src);
    art::mirror::ArtMethod* dmeth = (art::mirror::ArtMethod*) env->FromReflectedMethod(dest);

    reinterpret_cast<art::mirror::Class*>(dmeth->declaring_class_)->class_loader_ =
    reinterpret_cast<art::mirror::Class*>(smeth->declaring_class_)->class_loader_; //for plugin classloader
    reinterpret_cast<art::mirror::Class*>(dmeth->declaring_class_)->clinit_thread_id_ =
    reinterpret_cast<art::mirror::Class*>(smeth->declaring_class_)->clinit_thread_id_;
    reinterpret_cast<art::mirror::Class*>(dmeth->declaring_class_)->status_ = reinterpret_cast<art::mirror::Class*>(smeth->declaring_class_)->status_-1;
    //for reflection invoke
    reinterpret_cast<art::mirror::Class*>(dmeth->declaring_class_)->super_class_ = 0;
    smeth->declaring_class_ = dmeth->declaring_class_;
    smeth->dex_cache_resolved_methods_ = dmeth->dex_cache_resolved_methods_;
    smeth->dex_cache_resolved_types_ = dmeth->dex_cache_resolved_types_;
    smeth->access_flags_ = dmeth->access_flags_ | 0x0001;
    smeth->dex_code_item_offset_ = dmeth->dex_code_item_offset_;
    smeth->dex_method_index_ = dmeth->dex_method_index_;
    smeth->method_index_ = dmeth->method_index_;  
    smeth->ptr_sized_fields_.entry_point_from_interpreter_ =
    dmeth->ptr_sized_fields_.entry_point_from_interpreter_;
    smeth->ptr_sized_fields_.entry_point_from_jni_ =
    dmeth->ptr_sized_fields_.entry_point_from_jni_;
    smeth->ptr_sized_fields_.entry_point_from_quick_compiled_code_ =
    dmeth->ptr_sized_fields_.entry_point_from_quick_compiled_code_;
}

```

除前两行代码，后面都是结构体内容的替换，env->FromReflectedMethod(src) 返回的是 jmethodID ,事实上就是 ArtMethod 结构体的指针地址，所以可以强制类型转换成 ArtMethod 结构体指针。最终就完成了方法替换。

上面的实现有很多局限性，由于不同系统版本 ArtMethod 结构体会不一致，所以本地保留了不同版本的 ArtMethod 结构体代码，每次 Android 有新版本发布均需要做一次兼容，而且如果第三方 ROM 修改了 ArtMethod 结构体，那么这种方案就会失效。

《深入探索 Android 热修复技术原理》给出了一种更优雅的实现方案，不关心 ArtMethod 的内部结构，直接通过内存地址替换整个 ArtMethod，我使用文中提供的方案使用极少的代码实现了一个 Demo 并上传到了 GitHub 上：[https://github.com/pqpo/MethodHook](https://github.com/pqpo/MethodHook)

主要就提供了一个 MethodHook 类完成类方法替换，使用方法如下：

```java

public class MainActivity extends AppCompatActivity {

    MethodHook methodHook;
    Method srcMethod;
    Method destMethod;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        methodHook = new MethodHook();

        Button btnClick = (Button) findViewById(R.id.click);
        Button btnHook = (Button) findViewById(R.id.hook);
        Button btnRestore = (Button) findViewById(R.id.restore);

        btnClick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //方法替换前显示Hello!，方法替换后实际调用showHookToast()显示Hello Hook!
                showToast();
            }
        });

        try {
            srcMethod = getClass().getDeclaredMethod("showToast");
            destMethod = getClass().getDeclaredMethod("showHookToast");
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }

        btnHook.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //方法替换
                methodHook.hook(srcMethod, destMethod);
            }
        });

        btnRestore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //方法恢复
                methodHook.restore(srcMethod);
            }
        });

    }

    public void showToast() {
        Toast.makeText(this, "Hello!", Toast.LENGTH_SHORT).show();
    }

    public void showHookToast() {
        Toast.makeText(this, "Hello Hook!", Toast.LENGTH_SHORT).show();
    }

}

```

界面如下：

![](https://o0iqn064q.qnssl.com/wp-content/uploads/2017/07/methodhook1-169x300.png) ![](https://o0iqn064q.qnssl.com/wp-content/uploads/2017/07/methodhook2-169x300.png)

点击 CLICK 按钮出现图一；点击 HOOK 按钮，再点击 CLICK 按钮出现图二；点击 RESTORE 按钮，再点击 CLICK 按钮出现图一。整个过程完成了方法的替换与恢复。

Java 层核心方法如下：

```java

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

```

其中 hook 方法将完成方法替换，restore 方法恢复原来的方法， Map<Method, Long> methodBackup 用于保存备份的方法地址，Key 为备份的方法，Value 为方法备份的内存地址。

主要逻辑位于 native 层,我们知道 System.loadLibrary 加载一个动态库是，首先会调用其 JNI_OnLoad 函数，在 JNI_OnLoad 函数中完成一般会完成 native 方法的动态注册，在本例中还加入了如下代码，用于计算不同平台 ArtMethod 结构体的大小 ：

```c++ 

methodHookClassInfo.m1 = env -> GetStaticMethodID(classEvaluateUtil, "m1", "()V");
methodHookClassInfo.m2 = env -> GetStaticMethodID(classEvaluateUtil, "m2", "()V");
methodHookClassInfo.methodSize = reinterpret_cast(methodHookClassInfo.m2) - reinterpret_cast(methodHookClassInfo.m1);

```

在 Java MethodHook 类中有下面两个静态的空方法：

```java

public static void m1(){}
public static void m2(){}

```

在 native 层获取这两个方法的 jmethodID, 事实上就是 ArtMethod 结构体的指针地址，由于 m1,m2 方法是相邻的，其在 native 层的 ArtMethod 结构体也是相邻的，所以它们内存地址的差值就是 ArtMethod 结构体的大小。
接下来看方法替换的逻辑：

```c++ 

static long methodHook(JNIEnv* env, jclass type, jobject srcMethodObj, jobject destMethodObj) {
    void* srcMethod = reinterpret_cast<void*>(env -> FromReflectedMethod(srcMethodObj));
    void* destMethod = reinterpret_cast<void*>(env -> FromReflectedMethod(destMethodObj));
    int* backupMethod = new int[methodHookClassInfo.methodSize];
    memcpy(backupMethod, srcMethod, methodHookClassInfo.methodSize);
    memcpy(srcMethod, destMethod, methodHookClassInfo.methodSize);
    return reinterpret_cast(backupMethod);
}

```

这里的 srcMethodObj，destMethodObj 对应 Java 层的 Method 类， 通过 env -> FromReflectedMethod 获取方法的 jmethodID, 实际上就获取了方法位于 native 层 ArtMethod 结构体的指针地址。
一开始已经计算出 ArtMethod 结构体的大小并保存在了 methodHookClassInfo.methodSize 中。再新构造一个 int 数组来备份原方法。使用 memcpy 将原函数 ArtMethod 内存拷贝至备份的数组中，然后使用 memcpy 将目标函数 ArtMethod 结构体拷贝至原函数结构体指针起始位置完成结构体替换。最后将用于备份的 int 数组的指针强制类型转换为 long 类型返回给 Java 层以供后续恢复之用。

下面是方法恢复的函数：  


```c++  

static jobject methodRestore(JNIEnv* env, jclass type, jobject srcMethod, jlong methodPtr) {
    int* backupMethod = reinterpret_cast<int*>(methodPtr);
    void* artMethodSrc = reinterpret_cast<void*>(env -> FromReflectedMethod(srcMethod));
    memcpy(artMethodSrc, backupMethod, methodHookClassInfo.methodSize);
    delete []backupMethod;
    return srcMethod;
}

```  

将上一步保存的 long 类型地址强制转换成 int 指针，然后获取原函数 ArtMethod 结构体地址，接着将 int 数组的内容恢复至原函数内存地址处，完成恢复函数，最后释放备份用的 int 数组。

可以看出这种方法相比较之前的方案优雅不少，不用考虑 ArtMethod 的内部结构，巧妙的计算出不同平台的 ArtMethod 结构体大小，从而不需要做任何适配工作。然而若要真正要将这个技术应用到热修复框架中还需要考虑很多其他因素，本文抛砖引玉，只讲述 Method Hook 的原理，对热修复知识有兴趣的可以阅读阿里的《深入探索 Android 热修复技术原理》。文章讲到了阿里的热修复框架 Sophix 结合了方法替换与类加载替换方案值得期待。

参考：

- [Android热修复升级探索——追寻极致的代码热替换](https://yq.aliyun.com/articles/74598?t=t1#)  
- [AndFix](https://github.com/alibaba/AndFix)
- 《深入探索 Android 热修复技术原理》
