package com.ff.mycloud.common.util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/1/13 21:12
 * @version 1.0
 */
 
public class PasswordUtil {
    private static final  int DEFAULT_LOG_ROUNDS = 12;

    //加密密码
    public static String encode(String password){
        return BCrypt.hashpw(password, BCrypt.gensalt(DEFAULT_LOG_ROUNDS));
    }
    //验证密码
    public static boolean matches(String password,String encoded){
        return BCrypt.checkpw(password, encoded);
    }
}
