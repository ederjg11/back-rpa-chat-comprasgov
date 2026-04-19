package br.gov.compras.rpa.service;

import br.gov.compras.rpa.model.MonitoredClient;
import br.gov.compras.rpa.repository.ChatMessageLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMonitorServiceTest {

    @Mock
    private ChatMessageLogRepository chatMessageLogRepository;

    @Mock
    private ClientMatcherService clientMatcherService;

    @Mock
    private NotificationService emailNotificationService;

    @Mock
    private NotificationService whatsAppNotificationService;

    private ChatMonitorService chatMonitorService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        chatMonitorService = new ChatMonitorService(
                chatMessageLogRepository,
                clientMatcherService,
                emailNotificationService,
                whatsAppNotificationService
        );
    }

    @Test
    void shouldNotifyWhenClientIsMatched() {
        MonitoredClient client = new MonitoredClient("Cliente Sul", "11.111.111/0001-11");
        when(chatMessageLogRepository.existsBySourceMessageId("msg-1")).thenReturn(false);
        when(clientMatcherService.match("conteudo")).thenReturn(Optional.of(client));

        chatMonitorService.processMessage("msg-1", "conteudo");

        verify(emailNotificationService).notifyClientMention(client, "conteudo");
        verify(whatsAppNotificationService).notifyClientMention(client, "conteudo");
        verify(chatMessageLogRepository).save(any());
    }

    @Test
    void shouldSkipDuplicateMessage() {
        when(chatMessageLogRepository.existsBySourceMessageId("msg-2")).thenReturn(true);

        chatMonitorService.processMessage("msg-2", "conteudo");

        verify(clientMatcherService, never()).match(any());
        verify(chatMessageLogRepository, never()).save(any());
    }
}
