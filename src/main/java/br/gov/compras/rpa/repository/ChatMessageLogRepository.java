package br.gov.compras.rpa.repository;

import br.gov.compras.rpa.model.ChatMessageLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageLogRepository extends JpaRepository<ChatMessageLog, Long> {

    boolean existsBySourceMessageId(String sourceMessageId);
}
