package com.ff.mycloud.common.util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * 密码工具类，基于BCrypt算法提供密码加密和验证功能。
 * 使用默认12轮对数回合（log_rounds）进行哈希，平衡安全性与性能。
 *
 * @author 飞飞
 * @date 2026/6/27
 */
public class PasswordUtil {
    /** BCrypt默认对数回合数，2^12次迭代 */
    private static final int DEFAULT_LOG_ROUNDS = 12;

    /**
     * 对明文密码进行BCrypt加密
     *
     * @param password 明文密码
     * @return 加密后的密文
     */
    public static String encode(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(DEFAULT_LOG_ROUNDS));
    }

    /**
     * 验证明文密码是否与密文匹配
     *
     * @param password 明文密码
     * @param encoded  BCrypt加密后的密文
     * @return true表示匹配，false表示不匹配
     */
    public static boolean matches(String password, String encoded) {
        return BCrypt.checkpw(password, encoded);
    }
}
