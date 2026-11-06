package com.tripbudget.tripbudget_core.auth.mail;

import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

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
        this.from = from.isBlank() ? "\"TripBudget\" <" + user + ">" : from;
        this.template = new ClassPathResource("templates/otp-email.html").getContentAsString(StandardCharsets.UTF_8);
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

    public boolean sendOtp(String to, String otp, String userName) {
        if (user.isBlank() || password.isBlank()) {
            log.warn("[DEV MODE] Email is not configured. OTP for {} is: {}", to, otp);
            return true;
        }
        try {
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("[TripBudget] Mã xác thực tài khoản / Verification Code: " + otp);
            helper.setText(template.replace("{{userName}}", userName).replace("{{otp}}", otp), true);
            sender.send(message);
            return true;
        } catch (MessagingException | MailException exception) {
            // Existing behavior: SMTP failure does not undo registration or delete the OTP.
            log.warn("Unable to send OTP email");
            return false;
        }
    }
}
