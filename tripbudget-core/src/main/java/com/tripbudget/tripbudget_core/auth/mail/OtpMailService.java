package com.tripbudget.tripbudget_core.auth.mail;

import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class OtpMailService {
    private final JavaMailSenderImpl sender;
    private final String user;
    private final String password;
    private final String from;
    private final String template;
    private final String resetTemplate;

    public OtpMailService(
            @Value("${MAIL_HOST:localhost}") String host,
            @Value("${MAIL_PORT:587}") int port,
            @Value("${MAIL_SECURE:false}") boolean secure,
            @Value("${MAIL_USER:}") String user,
            @Value("${MAIL_PASSWORD:}") String password,
            @Value("${MAIL_FROM:}") String from
    ) throws IOException {
        this.user = user;
        this.password = password;
        this.from = from.isBlank() ? "\"Vi Vu\" <" + user + ">" : from;
        this.template = new ClassPathResource("templates/otp-email.html").getContentAsString(StandardCharsets.UTF_8);
        this.resetTemplate = new ClassPathResource("templates/password-reset-email.html").getContentAsString(StandardCharsets.UTF_8);
        sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(user);
        sender.setPassword(password);
        sender.getJavaMailProperties().setProperty("mail.smtp.auth", "true");
        sender.getJavaMailProperties().setProperty("mail.smtp.ssl.enable", Boolean.toString(secure));
        sender.getJavaMailProperties().setProperty("mail.smtp.starttls.enable", Boolean.toString(!secure));
        sender.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout", "10000");
        sender.getJavaMailProperties().setProperty("mail.smtp.timeout", "30000");
    }

    public boolean sendOtp(String to, String otp, String userName) { return sendCode(to, otp, userName, false); }

    public boolean sendPasswordReset(String to, String otp, String userName) { return sendCode(to, otp, userName, true); }

    private boolean sendCode(String to, String otp, String userName, boolean reset) {
        if (user.isBlank() || password.isBlank()) {
            log.warn("Unable to send OTP email: SMTP credentials are not configured");
            return false;
        }
        try {
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(reset ? "[Vi Vu] Đặt lại mật khẩu / Reset password" : "[Vi Vu] Mã xác thực tài khoản / Verification Code: " + otp);
            String html = (reset ? resetTemplate : template).replace("{{userName}}", HtmlUtils.htmlEscape(userName))
                    .replace("{{otp}}", HtmlUtils.htmlEscape(otp));
            String plainText = "Xin chào " + userName + ",\n\nMã xác thực tài khoản Vi Vu của bạn: " + otp
                    + "\nMã có hiệu lực trong 5 phút. Không chia sẻ mã này với bất kỳ ai.\n\n"
                    + "Your Vi Vu verification code: " + otp + "\nValid for 5 minutes. If you did not request it, ignore this email.";
            if (reset) plainText = "Mã đặt lại mật khẩu Vi Vu: " + otp + "\nCó hiệu lực 5 phút. Không chia sẻ mã này. Nếu không yêu cầu, hãy bỏ qua.\nPassword reset code: " + otp + "\nValid for 5 minutes. If you did not request this, ignore this email.";
            helper.setText(plainText, html);
            helper.addInline("vivu-logo", new ClassPathResource("templates/mail/vi-vu-logo.png"), "image/png");
            sender.send(message);
            return true;
        } catch (MessagingException | MailException exception) {
            // The caller keeps the account pending and removes the undelivered code.
            log.warn("Unable to send OTP email");
            return false;
        }
    }
}
