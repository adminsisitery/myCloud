package com.cloudrive.fileAsyncService.common.util;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * 邮件发送工具类（异步服务版）
 * <p>用于用量通知消费者发送资源用量提醒邮件。
 * 邮件发送器或发件人邮箱未配置时只记录日志并返回 false，不影响 MQ 消费主流程。</p>
 */
@Slf4j
@Component("asyncEmailUtil")
public class EmailUtil {

    private static JavaMailSender mailSender;
    private static String fromEmail;

    public EmailUtil(ObjectProvider<JavaMailSender> mailSenderProvider,
                     @Value("${spring.mail.username:}") String fromEmail) {
        EmailUtil.mailSender = mailSenderProvider.getIfAvailable();
        EmailUtil.fromEmail = fromEmail;
    }

    /**
     * 发送用量提醒邮件
     *
     * @param toEmail 收件人邮箱
     * @param msg     提醒内容
     * @return true 表示发送成功
     */
    public static boolean sendUsage(String toEmail, String msg) {
        if (mailSender == null) {
            log.warn("邮件发送器未配置，跳过用量通知邮件: to={}", toEmail);
            return false;
        }
        if (fromEmail == null || fromEmail.isBlank()) {
            log.warn("发件人邮箱未配置，跳过用量通知邮件: to={}", toEmail);
            return false;
        }
        return sendHtmlEmail(toEmail, "MY Cloud Usage", buildUsageEmailContent(msg));
    }

    private static boolean sendHtmlEmail(String toEmail, String subject, String content) {
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
            log.warn("用量通知邮件发送失败: to={}, error={}", toEmail, e.getMessage());
            return false;
        }
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
}
