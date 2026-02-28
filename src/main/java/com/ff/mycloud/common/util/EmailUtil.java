package com.ff.mycloud.common.util;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.eclipse.angus.mail.util.logging.MailHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/1/20 10:09
 * @version 1.0
 */

@Component
public class EmailUtil {

    private static JavaMailSender mailSender;
    private static String fromEmail;

    public EmailUtil(JavaMailSender mailSender,
                     @Value("${spring.mail.username}")
                     String fromEmail) {
        EmailUtil.mailSender = mailSender;
        EmailUtil.fromEmail = fromEmail;
    }

    public static boolean sendVerificationCode(String toEmail, String verificationCode)  {
        String subject = "MY Cloud 验证码";
        String content = buildVerificationEmailContent(verificationCode);
        return sendHtmlEmail(toEmail,subject,content);
    }

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
