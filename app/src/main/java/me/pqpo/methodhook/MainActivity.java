package me.pqpo.methodhook;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnClick = (Button) findViewById(R.id.click);
        Button btnHook = (Button) findViewById(R.id.hook);
        Button btnRestore = (Button) findViewById(R.id.restore);

        btnClick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showToast("Hello!");
            }
        });

        btnHook.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Method srcMethod = MainActivity.class.getDeclaredMethod("showToast", String.class);
                    Method destMethod = MainActivity.class.getDeclaredMethod("showHookToast", String.class);
                    HookManager.get().hookMethod(srcMethod, destMethod);
                } catch (NoSuchMethodException e) {
                    e.printStackTrace();
                }
            }
        });

        btnRestore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //hook 失败
//                try {
//                    HookManager.get().hookMethod(Toast.class.getDeclaredMethod("show"), MainActivity.class.getDeclaredMethod("Toast_show"));
//                } catch (NoSuchMethodException e) {
//                    e.printStackTrace();
//                }
            }

        });


        }

    public void showToast(String msg) {
        Toast toast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
        toast.show();
    }

    public void showHookToast(String msg) {
        Log.d("MainActivity", "msg:" + msg);
        HookManager.get().callOrigin(this, msg + "(Hook)");
    }

    public void Toast_show() {
        Log.d("MainActivity", "Toast_show");
        HookManager.get().callOrigin(this);
    }

}
