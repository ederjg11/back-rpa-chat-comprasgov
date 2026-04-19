package br.gov.compras.rpa.service;

import br.gov.compras.rpa.model.MonitoredClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class WhatsAppNotificationService implements NotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WhatsAppNotificationService.class);

    private final RestClient restClient;
    private final String webhookUrl;

    public WhatsAppNotificationService(RestClient.Builder restClientBuilder,
                                       @Value("${app.notifications.whatsapp.webhook-url:}") String webhookUrl) {
        this.restClient = restClientBuilder.build();
        this.webhookUrl = webhookUrl;
    }

    @Override
    public void notifyClientMention(MonitoredClient client, String message) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            LOGGER.info("WhatsApp webhook not configured. Notification skipped for client {}", client.getName());
            return;
        }

        try {
            restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "clientName", client.getName(),
                            "cnpj", client.getCnpj(),
                            "message", message
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException ex) {
            LOGGER.error("Failed to send WhatsApp notification for client {}", client.getName(), ex);
        }
    }
}
