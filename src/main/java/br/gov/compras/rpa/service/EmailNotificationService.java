package br.gov.compras.rpa.service;

import br.gov.compras.rpa.model.MonitoredClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailNotificationService implements NotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailNotificationService.class);

    private final JavaMailSender mailSender;
    private final String destination;

    public EmailNotificationService(ObjectProvider<JavaMailSender> mailSenderProvider,
                                    @Value("${app.notifications.email.to:}") String destination) {
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.destination = destination;
    }

    @Override
    public void notifyClientMention(MonitoredClient client, String message) {
        if (destination == null || destination.isBlank()) {
            LOGGER.info("Email destination not configured. Notification skipped for client {}", client.getName());
            return;
        }
        if (mailSender == null) {
            LOGGER.info("JavaMailSender is not configured. Notification skipped for client {}", client.getName());
            return;
        }

        SimpleMailMessage email = new SimpleMailMessage();
        email.setTo(destination);
        email.setSubject("[ComprasGov] Cliente mencionado: " + client.getName());
        email.setText("Mensagem capturada: " + message + "\nCNPJ cliente: " + client.getCnpj());
        mailSender.send(email);
    }
}
