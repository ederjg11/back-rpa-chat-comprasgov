package br.gov.compras.rpa.service;

import br.gov.compras.rpa.model.ChatMessageLog;
import br.gov.compras.rpa.model.MonitoredClient;
import br.gov.compras.rpa.repository.ChatMessageLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class ChatMonitorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatMonitorService.class);

    private final ChatMessageLogRepository chatMessageLogRepository;
    private final ClientMatcherService clientMatcherService;
    private final NotificationService emailNotificationService;
    private final NotificationService whatsAppNotificationService;

    public ChatMonitorService(ChatMessageLogRepository chatMessageLogRepository,
                              ClientMatcherService clientMatcherService,
                              @Qualifier("emailNotificationService") NotificationService emailNotificationService,
                              @Qualifier("whatsAppNotificationService") NotificationService whatsAppNotificationService) {
        this.chatMessageLogRepository = chatMessageLogRepository;
        this.clientMatcherService = clientMatcherService;
        this.emailNotificationService = emailNotificationService;
        this.whatsAppNotificationService = whatsAppNotificationService;
    }

    public void processMessage(String sourceMessageId, String content) {
        if (chatMessageLogRepository.existsBySourceMessageId(sourceMessageId)) {
            return;
        }

        Optional<MonitoredClient> matchedClient = clientMatcherService.match(content);
        matchedClient.ifPresent(client -> {
            notifySafely(emailNotificationService, client, content, "email");
            notifySafely(whatsAppNotificationService, client, content, "whatsapp");
        });

        chatMessageLogRepository.save(new ChatMessageLog(
                sourceMessageId,
                content,
                matchedClient.map(MonitoredClient::getName).orElse(null),
                OffsetDateTime.now()
        ));
    }

    private void notifySafely(NotificationService notificationService, MonitoredClient client, String content, String channel) {
        try {
            notificationService.notifyClientMention(client, content);
        } catch (RuntimeException ex) {
            LOGGER.error("Failed to send {} notification for client {}", channel, client.getName(), ex);
        }
    }
}
