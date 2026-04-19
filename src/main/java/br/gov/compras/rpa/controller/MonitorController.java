package br.gov.compras.rpa.controller;

import br.gov.compras.rpa.dto.IncomingChatMessage;
import br.gov.compras.rpa.repository.ChatMessageLogRepository;
import br.gov.compras.rpa.repository.MonitoredClientRepository;
import br.gov.compras.rpa.service.ChatHtmlReaderService;
import br.gov.compras.rpa.service.ChatMonitorService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.stream.IntStream;

@Controller
public class MonitorController {

    private final MonitoredClientRepository monitoredClientRepository;
    private final ChatMessageLogRepository chatMessageLogRepository;
    private final ChatMonitorService chatMonitorService;
    private final ChatHtmlReaderService chatHtmlReaderService;

    public MonitorController(MonitoredClientRepository monitoredClientRepository,
                             ChatMessageLogRepository chatMessageLogRepository,
                             ChatMonitorService chatMonitorService,
                             ChatHtmlReaderService chatHtmlReaderService) {
        this.monitoredClientRepository = monitoredClientRepository;
        this.chatMessageLogRepository = chatMessageLogRepository;
        this.chatMonitorService = chatMonitorService;
        this.chatHtmlReaderService = chatHtmlReaderService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("clients", monitoredClientRepository.findByActiveTrue());
        model.addAttribute("processedMessages", chatMessageLogRepository.count());
        return "dashboard";
    }

    @PostMapping("/monitor/messages")
    public ResponseEntity<Void> receiveMessage(@Valid @RequestBody IncomingChatMessage message) {
        chatMonitorService.processMessage(message.sourceMessageId(), message.content());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/monitor/chat-html")
    public ResponseEntity<Void> receiveChatHtml(@Valid @RequestBody IncomingChatMessage chatHtml) {
        var extractedMessages = chatHtmlReaderService.extractMessages(chatHtml.content());
        IntStream.range(0, extractedMessages.size())
                .forEach(i -> chatMonitorService.processMessage(
                        chatHtml.sourceMessageId() + "-" + i,
                        extractedMessages.get(i)
                ));
        return ResponseEntity.accepted().build();
    }
}
