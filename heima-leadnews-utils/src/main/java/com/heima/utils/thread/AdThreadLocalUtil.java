package com.heima.utils.thread;

import com.heima.model.admin.pojos.AdUser;

public class AdThreadLocalUtil {
    private final static ThreadLocal<AdUser> Ap_USER_THREAD_LOCAL = new ThreadLocal<>();

    /**
     * 添加用户
     * @param adUser
     */
    public static void  setUser(AdUser adUser){
        Ap_USER_THREAD_LOCAL.set(adUser);
    }

    /**
     * 获取用户
     */
    public static AdUser getUser(){
        return Ap_USER_THREAD_LOCAL.get();
    }

    /**
     * 清理用户
     */
    public static void clear(){
        Ap_USER_THREAD_LOCAL.remove();
    }
}
