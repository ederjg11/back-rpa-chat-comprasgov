package br.gov.compras.rpa.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "chat_message_logs")
public class ChatMessageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sourceMessageId;

    @Column(nullable = false, length = 3000)
    private String content;

    @Column
    private String matchedClient;

    @Column(nullable = false)
    private OffsetDateTime processedAt;

    protected ChatMessageLog() {
    }

    public ChatMessageLog(String sourceMessageId, String content, String matchedClient, OffsetDateTime processedAt) {
        this.sourceMessageId = sourceMessageId;
        this.content = content;
        this.matchedClient = matchedClient;
        this.processedAt = processedAt;
    }

    public Long getId() {
        return id;
    }

    public String getSourceMessageId() {
        return sourceMessageId;
    }

    public String getContent() {
        return content;
    }

    public String getMatchedClient() {
        return matchedClient;
    }

    public OffsetDateTime getProcessedAt() {
        return processedAt;
    }
}
