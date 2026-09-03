package com.runvas.backend.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class AdminNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AdminNotificationService.class);

    private final JavaMailSender mailSender;
    private final String adminEmail;

    public AdminNotificationService(
            JavaMailSender mailSender,
            @Value("${runvas.admin.notification-email}") String adminEmail
    ) {
        this.mailSender = mailSender;
        this.adminEmail = adminEmail;
    }

    public void notifyNewReport(String targetType, String targetId, String reporterId) {
        send(
                "[Runvas] 새 신고 접수",
                "targetType=%s, targetId=%s, reporterId=%s 신고가 접수되었습니다. 관리자 대시보드(/admin/reports)에서 24시간 이내 처리해주세요."
                        .formatted(targetType, targetId, reporterId)
        );
    }

    public void notifyBlock(String blockerId, String blockedId) {
        send(
                "[Runvas] 사용자 차단 발생",
                "blockerId=%s가 blockedId=%s를 차단했습니다. 차단이 반복되는 사용자는 관리자 대시보드(/admin/users)에서 확인해주세요."
                        .formatted(blockerId, blockedId)
        );
    }

    private void send(String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(adminEmail);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
        } catch (Exception exception) {
            log.warn("관리자 알림 메일 발송 실패: {}", exception.getMessage());
        }
    }
}
