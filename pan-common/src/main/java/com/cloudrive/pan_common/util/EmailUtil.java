package com.cloudrive.pan_common.util;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * 邮件发送工具类，用于发送系统通知邮件（如验证码）。
 * 基于Spring JavaMailSender，支持HTML格式邮件发送。
 *
 * @author 飞飞
 * @date 2026/6/27
 */

@Component
public class EmailUtil {

    private static JavaMailSender mailSender;
    private static String fromEmail;

    /**
     * 构造方法，初始化MailSender和发件人邮箱地址
     *
     * @param mailSender Spring邮件发送器
     * @param fromEmail  发件人邮箱地址（从配置文件注入）
     */
    public EmailUtil(JavaMailSender mailSender,
                     @Value("${spring.mail.username}")
                     String fromEmail) {
        EmailUtil.mailSender = mailSender;
        EmailUtil.fromEmail = fromEmail;
    }

    /**
     * 发送验证码邮件
     *
     * @param toEmail          收件人邮箱地址
     * @param verificationCode 验证码
     * @return true表示发送成功，false表示发送失败
     */
    public static boolean sendVerificationCode(String toEmail, String verificationCode)  {
        String subject = "MY Cloud 验证码";
        String content = buildVerificationEmailContent(verificationCode);
        return sendHtmlEmail(toEmail,subject,content);
    }

    /**
     * 发送HTML格式邮件
     *
     * @param toEmail 收件人邮箱地址
     * @param subject 邮件主题
     * @param content HTML格式的邮件内容
     * @return true表示发送成功，false表示发送失败
     */
    private static boolean sendHtmlEmail(String toEmail, String subject, String content)  {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "utf-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(content, true);
            mailSender.send(mimeMessage);
            return true;
        } catch (MessagingException e) {
            return false;
        }


    }

    public static boolean sendUsage(String toEmail, String msg){
        String subject = "MY Cloud Usage";
        String content = buildUsageEmailContent(msg);
        return sendHtmlEmail(toEmail,subject,content);
    }

    private static String buildUsageEmailContent(String msg) {
        return "<div style=\"font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 5px;\">"
                + "<h2 style=\"color: #1976d2; text-align: center; margin-top: 0;\">系统用量不足提醒</h2>"
                + "<p style=\"font-size: 16px; color: #333; margin: 10px 0;\">您好,</p>"
                + "<p style=\"font-size: 16px; color: #333; margin: 10px 0;\">检测到您的系统资源用量即将不足，详细信息如下：</p>"
                + "<div style=\"text-align: center; margin: 20px 0;\">"
                + "<span style=\"font-size: 16px; font-weight: bold; letter-spacing: 2px; color: #f56c6c; background-color: #f5f5f5; padding: 10px 20px; border-radius: 4px; display: inline-block;\">"
                + msg
                + "</span>"
                + "</div>"
                + "<p style=\"font-size: 16px; color: #333; margin: 10px 0;\">为避免影响业务正常使用，请及时充值或扩容资源。</p>"
                + "<p style=\"font-size: 14px; color: #757575; margin-top: 30px; text-align: center; margin-bottom: 0;\">此邮件由系统自动发送，请勿回复</p>"
                + "</div>";
    }

    /**
     * 构建验证码邮件的HTML内容
     *
     * @param verificationCode 验证码
     * @return HTML格式的邮件内容字符串
     */
    private static String buildVerificationEmailContent(String verificationCode) {
        return "<div style=\"font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 5px;\">"
                + "<h2 style=\"color: #1976d2; text-align: center;\">Cloud Drive 验证码</h2>"
                + "<p style=\"font-size: 16px; color: #333;\">您好,</p>"
                + "<p style=\"font-size: 16px; color: #333;\">您的验证码是:</p>"
                + "<div style=\"text-align: center; margin: 20px 0;\">"
                + "<span style=\"font-size: 24px; font-weight: bold; letter-spacing: 5px; color: #1976d2; background-color: #f5f5f5; padding: 10px 20px; border-radius: 4px;\">"
                + verificationCode
                + "</span>"
                + "</div>"
                + "<p style=\"font-size: 16px; color: #333;\">验证码有效期为5分钟，请勿将验证码泄露给他人。</p>"
                + "<p style=\"font-size: 14px; color: #757575; margin-top: 20px; text-align: center;\">此邮件由系统自动发送，请勿回复</p>"
                + "</div>";
    }

}
