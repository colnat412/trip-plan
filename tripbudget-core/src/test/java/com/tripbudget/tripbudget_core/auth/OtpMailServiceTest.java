package com.tripbudget.tripbudget_core.auth;

import com.tripbudget.tripbudget_core.auth.mail.OtpMailService;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OtpMailServiceTest {
    @Test
    void sendsHtmlAndPlainTextWithEscapedNameAndLeadingZeroCode() throws Exception {
        var mailer = new OtpMailService("smtp.invalid", 587, false, "sender@example.com", "test", "");
        var sender = mock(JavaMailSenderImpl.class);
        var message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        ReflectionTestUtils.setField(mailer, "sender", sender);
        assertTrue(mailer.sendOtp("member@example.com", "001234", "A & B <team>"));
        verify(sender).send(message);
        message.saveChanges();
        var parts = new HashMap<String, String>();
        collectText(message, parts);
        assertTrue(parts.get("html").contains("A &amp; B &lt;team&gt;"));
        assertFalse(parts.get("html").contains("{{"));
        assertTrue(parts.get("html").contains("001234"));
        assertTrue(parts.get("html").contains("Vi Vu"));
        assertTrue(parts.get("plain").contains("001234"));
        assertTrue(parts.get("plain").contains("Valid for 5 minutes"));
        assertEquals("member@example.com", message.getAllRecipients()[0].toString());
    }

    @Test
    void smtpFailureReturnsFalse() throws Exception {
        var mailer = new OtpMailService("smtp.invalid", 587, false, "sender@example.com", "test", "");
        var sender = mock(JavaMailSenderImpl.class);
        var message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        doThrow(new MailSendException("test failure")).when(sender).send(message);
        ReflectionTestUtils.setField(mailer, "sender", sender);
        assertFalse(mailer.sendOtp("member@example.com", "123456", "Member"));
    }

    @Test
    void missingCredentialsNeverPretendsToSend() throws Exception {
        var mailer = new OtpMailService("smtp.invalid", 587, false, "", "", "");
        var sender = mock(JavaMailSenderImpl.class);
        ReflectionTestUtils.setField(mailer, "sender", sender);
        assertFalse(mailer.sendOtp("member@example.com", "123456", "Member"));
        verifyNoInteractions(sender);
    }

    private void collectText(Part part, Map<String, String> parts) throws Exception {
        if (part.isMimeType("text/html")) parts.put("html", (String) part.getContent());
        else if (part.isMimeType("text/plain")) parts.put("plain", (String) part.getContent());
        else if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) collectText(multipart.getBodyPart(i), parts);
        }
    }
}
