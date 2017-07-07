package me.pqpo.methodhook;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.lang.reflect.Method;

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
                methodHook.hook(srcMethod, destMethod);
            }
        });

        btnRestore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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
