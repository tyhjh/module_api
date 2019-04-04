package com.dhht.module_api;

import com.alibaba.android.arouter.launcher.ARouter;

/**
 * @author HanPei
 * @date 2019/4/4  下午1:47
 */
public class Application extends android.app.Application {

    boolean isDebug = true;

    @Override
    public void onCreate() {
        super.onCreate();
        initARouter();

    }


    /**
     * 初始化阿里路由
     */
    private void initARouter() {
        if (isDebug) {
            ARouter.openLog();
            ARouter.openDebug();
        }
        ARouter.init(this);
    }
}
