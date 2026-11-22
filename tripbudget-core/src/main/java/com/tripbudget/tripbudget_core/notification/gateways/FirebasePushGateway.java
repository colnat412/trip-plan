package com.tripbudget.tripbudget_core.notification.gateways;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.*;
import com.google.firebase.messaging.*;
import com.tripbudget.tripbudget_core.common.services.HashidsService;
import com.tripbudget.tripbudget_core.notification.entities.*;
import com.tripbudget.tripbudget_core.notification.enums.NotificationType;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.time.*;

@Component
@lombok.RequiredArgsConstructor
public class FirebasePushGateway {
    private final HashidsService hashids;
    @Value("${notifications.firebase.project-id:}") private String projectId;
    @Value("${notifications.web-origin:http://localhost:3000}") private String webOrigin;
    @Value("${GOOGLE_APPLICATION_CREDENTIALS:}") private String credentialsFile;
    private FirebaseMessaging messaging;
    @Value("${notifications.push-enabled:false}") private boolean pushEnabled;
    @jakarta.annotation.PostConstruct
    public void validateConfig() {
        if (!pushEnabled) return;
        java.net.URI origin = java.net.URI.create(webOrigin);
        if (projectId.isBlank() || !"https".equals(origin.getScheme()) || origin.getHost() == null ||
            origin.getUserInfo() != null || origin.getQuery() != null || origin.getFragment() != null ||
            (origin.getPath() != null && !origin.getPath().isEmpty() && !"/".equals(origin.getPath()))) {
            throw new IllegalStateException("Push requires FIREBASE_PROJECT_ID and an HTTPS NOTIFICATION_WEB_ORIGIN");
        }
    }

    private GoogleCredentials credentials() throws IOException {
        if (credentialsFile.isBlank()) return GoogleCredentials.getApplicationDefault();
        try (var input = java.nio.file.Files.newInputStream(java.nio.file.Path.of(credentialsFile))) {
            return ServiceAccountCredentials.fromStream(input);
        }
    }
    private synchronized FirebaseMessaging messaging() throws IOException {
        if (messaging == null) {
            FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials())
                .setProjectId(projectId).setConnectTimeout(10000).setReadTimeout(15000).build();
            FirebaseApp app = FirebaseApp.initializeApp(options, "vivu-notifications");
            messaging = FirebaseMessaging.getInstance(app);
        }
        return messaging;
    }
    private String description(NotificationType type, boolean english) {
        return switch (type) {
            case MEMBER_INVITED -> english ? "You have a trip invitation. Accept or decline in Vi Vu." : "Bạn có lời mời tham gia chuyến đi. Mở Vi Vu để chấp nhận hoặc từ chối.";
            case MEMBER_INVITATION_ACCEPTED -> english ? "You accepted the trip invitation." : "Bạn đã chấp nhận lời mời tham gia chuyến đi.";
            case MEMBER_INVITATION_DECLINED -> english ? "You declined the trip invitation." : "Bạn đã từ chối lời mời tham gia chuyến đi.";
            case MEMBER_INVITATION_CANCELLED -> english ? "The trip invitation is no longer available." : "Lời mời tham gia chuyến đi không còn hiệu lực.";
            case MEMBER_ADDED -> english ? "You were added to a trip." : "Bạn đã được thêm vào chuyến đi.";
            case MEMBER_APPROVAL_REQUIRED -> english ? "A member is waiting for approval." : "Có thành viên đang chờ duyệt.";
            case MEMBER_PENDING -> english ? "Your trip membership is awaiting approval." : "Bạn đang chờ duyệt vào chuyến đi.";
            case MEMBER_APPROVED -> english ? "Your trip membership was approved." : "Bạn đã được duyệt vào chuyến đi.";
            case MEMBER_ROLE_CHANGED -> english ? "Your role has changed." : "Vai trò của bạn đã thay đổi.";
            case MEMBER_REMOVED -> english ? "You are no longer a member of this trip." : "Bạn không còn là thành viên của chuyến đi.";
            case MEMBER_LEFT -> english ? "A member has left the trip." : "Có thành viên đã rời chuyến đi.";
            case ACTIVITY_REMINDER -> english ? "Your activity is starting soon." : "Hoạt động của bạn sắp bắt đầu.";
            case TRIP_REMINDER -> english ? "Your trip departs in 3 days." : "Chuyến đi khởi hành sau 3 ngày.";
            case TRIP_CANCELLED -> english ? "The trip was cancelled." : "Chuyến đi đã bị hủy.";
            case EXPENSE_CREATED -> english ? "A new expense involves you." : "Có khoản chi mới liên quan đến bạn.";
            case EXPENSE_UPDATED -> english ? "An expense involving you has changed." : "Khoản chi liên quan đến bạn đã thay đổi.";
            case EXPENSE_DELETED -> english ? "An expense involving you was deleted." : "Khoản chi liên quan đến bạn đã bị xóa.";
            case SETTLEMENT_RECORDED -> english ? "A payment record involving you was added." : "Có bản ghi khoản trả mới liên quan đến bạn.";
            case SETTLEMENT_DELETED -> english ? "A payment record involving you was deleted." : "Bản ghi khoản trả liên quan đến bạn đã bị xóa.";
            case SETTLEMENT_CONFIRMATION_REQUIRED -> english ? "A payment request needs your confirmation." : "Có yêu cầu về khoản trả tiền cần bạn xác nhận.";
            case SETTLEMENT_UPDATED -> english ? "A payment request involving you was updated." : "Yêu cầu về khoản trả tiền liên quan đến bạn đã được cập nhật.";
        };
    }

    public String send(NotificationEntity notification, PushDeviceEntity device, String locale)
            throws IOException, FirebaseMessagingException {
        boolean english = "en".equals(locale);
        String body = description(notification.getType(), english);
        long ttl = notification.getExpiresAt() == null ? 86400 : Math.max(0,
            Duration.between(Instant.now(), notification.getExpiresAt()).getSeconds());
        Message.Builder builder = Message.builder()
            .setNotification(Notification.builder().setTitle("Vi Vu").setBody(body).build())
            .putData("notificationId", hashids.encode(notification.getId()))
            .putData("recipientId", hashids.encode(notification.getRecipientId()))
            .putData("type", notification.getType().name())
            .putData("payloadVersion", "1")
            .setWebpushConfig(WebpushConfig.builder()
                .putHeader("TTL", Long.toString(Math.min(ttl, 86400)))
                .setNotification(WebpushNotification.builder().setTag(hashids.encode(notification.getId())).build())
                .setFcmOptions(WebpushFcmOptions.withLink(webOrigin.replaceAll("/$", "") +
                    "/overview?notificationId=" + hashids.encode(notification.getId()))).build());
        if ("FCM_FID".equals(device.getAddressType())) builder.setFid(device.getAddressValue());
        else builder.setToken(device.getAddressValue());
        return messaging().send(builder.build());
    }
}
