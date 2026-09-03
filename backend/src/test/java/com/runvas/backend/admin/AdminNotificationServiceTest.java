package com.runvas.backend.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

import org.junit.jupiter.api.Test;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class AdminNotificationServiceTest {

    @Test
    void notifyNewReport_메일을_발송한다() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        AdminNotificationService service = new AdminNotificationService(mailSender, "admin@example.com");

        service.notifyNewReport("posts", "post-1", "reporter-1");

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void notifyNewReport_발송이_실패해도_예외를_전파하지_않는다() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        doThrow(new MailException("smtp down") {}).when(mailSender).send(any(SimpleMailMessage.class));
        AdminNotificationService service = new AdminNotificationService(mailSender, "admin@example.com");

        service.notifyNewReport("posts", "post-1", "reporter-1");
    }

    @Test
    void notifyBlock_메일을_발송한다() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        AdminNotificationService service = new AdminNotificationService(mailSender, "admin@example.com");

        service.notifyBlock("blocker-1", "blocked-1");

        verify(mailSender).send(any(SimpleMailMessage.class));
    }
}
