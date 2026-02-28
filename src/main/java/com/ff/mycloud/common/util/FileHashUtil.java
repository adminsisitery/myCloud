package com.ff.mycloud.common.util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 文件哈希计算工具类
 */
public class FileHashUtil {

    private static final Logger logger = LoggerFactory.getLogger(FileHashUtil.class);
    private static final int BUFFER_SIZE = 8192;

    /**
     * 计算MultipartFile的SHA-256哈希值
     *
     * @param file 上传的文件
     * @return SHA-256哈希值的十六进制字符串表示，如果计算失败则返回null
     */
    public static String calculateSHA256(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        try (InputStream inputStream = file.getInputStream()) {
            return calculateSHA256(inputStream);
        } catch (IOException e) {
            logger.error("计算文件SHA-256哈希值时发生IO错误", e);
            return null;
        }
    }
    
    /**
     * 计算File的SHA-256哈希值
     *
     * @param file 文件对象
     * @return SHA-256哈希值的十六进制字符串表示，如果计算失败则返回null
     */
    public static String calculateSHA256(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return null;
        }

        try (FileInputStream inputStream = new FileInputStream(file)) {
            return calculateSHA256(inputStream);
        } catch (IOException e) {
            logger.error("计算文件SHA-256哈希值时发生IO错误", e);
            return null;
        }
    }

    /**
     * 计算输入流的SHA-256哈希值
     *
     * @param inputStream 输入流
     * @return SHA-256哈希值的十六进制字符串表示，如果计算失败则返回null
     */
    public static String calculateSHA256(InputStream inputStream) {
        if (inputStream == null) {
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;

            // 重置输入流位置（如果支持）
            if (inputStream.markSupported()) {
                inputStream.mark(Integer.MAX_VALUE);
            }

            try {
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }

                // 重置输入流位置（如果支持）
                if (inputStream.markSupported()) {
                    inputStream.reset();
                }

                byte[] hashBytes = digest.digest();
                return bytesToHex(hashBytes);
            } catch (IOException e) {
                logger.error("计算SHA-256哈希值时发生IO错误", e);
                return null;
            }
        } catch (NoSuchAlgorithmException e) {
            logger.error("SHA-256算法不可用", e);
            return null;
        }
    }

    /**
     * 将字节数组转换为十六进制字符串
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}

