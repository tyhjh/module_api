package com.dhht.user;

import com.alibaba.android.arouter.facade.template.IProvider;

/**
 * @author HanPei
 * @date 2019/4/4  上午11:56
 */
public interface UserService extends IProvider {
    UserInfo getUser();
}
