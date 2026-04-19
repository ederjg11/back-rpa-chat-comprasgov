package br.gov.compras.rpa.service;

import br.gov.compras.rpa.model.ChatMessageLog;
import br.gov.compras.rpa.model.MonitoredClient;
import br.gov.compras.rpa.repository.ChatMessageLogRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class ChatMonitorService {

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
            emailNotificationService.notifyClientMention(client, content);
            whatsAppNotificationService.notifyClientMention(client, content);
        });

        chatMessageLogRepository.save(new ChatMessageLog(
                sourceMessageId,
                content,
                matchedClient.map(MonitoredClient::getName).orElse(null),
                OffsetDateTime.now()
        ));
    }
}
