package com.dhht.user;

import android.content.Context;

import com.alibaba.android.arouter.facade.annotation.Route;

/**
 * @author HanPei
 * @date 2019/4/4  上午11:57
 */
@Route(path = "/user/UserService")
public class UserServiceImpl implements UserService {


    public UserServiceImpl() {

    }

    @Override
    public UserInfo getUser() {
        return new UserInfo("Tyhj");
    }

    @Override
    public void init(Context context) {

    }
}
