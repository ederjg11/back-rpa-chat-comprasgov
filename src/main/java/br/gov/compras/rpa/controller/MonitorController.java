package br.gov.compras.rpa.controller;

import br.gov.compras.rpa.dto.IncomingChatMessage;
import br.gov.compras.rpa.repository.ChatMessageLogRepository;
import br.gov.compras.rpa.repository.MonitoredClientRepository;
import br.gov.compras.rpa.service.ChatMonitorService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Controller
public class MonitorController {

    private final MonitoredClientRepository monitoredClientRepository;
    private final ChatMessageLogRepository chatMessageLogRepository;
    private final ChatMonitorService chatMonitorService;

    public MonitorController(MonitoredClientRepository monitoredClientRepository,
                             ChatMessageLogRepository chatMessageLogRepository,
                             ChatMonitorService chatMonitorService) {
        this.monitoredClientRepository = monitoredClientRepository;
        this.chatMessageLogRepository = chatMessageLogRepository;
        this.chatMonitorService = chatMonitorService;
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
}
