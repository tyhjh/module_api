package com.dhht.other;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Toast;

import com.alibaba.android.arouter.facade.annotation.Autowired;
import com.alibaba.android.arouter.facade.annotation.Route;
import com.alibaba.android.arouter.launcher.ARouter;
import com.dhht.user.UserService;

@Route(path = "/other/MainActivity")
public class MainActivity extends AppCompatActivity {

    @Autowired//(name = "/user/UserService")
    UserService mUserService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ARouter.getInstance().inject(this);
        setContentView(R.layout.other_activity_main);


        //mUserService=ARouter.getInstance().navigation(UserService.class);

        findViewById(R.id.btHello).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "Hello：" + mUserService.getUser().getName(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
