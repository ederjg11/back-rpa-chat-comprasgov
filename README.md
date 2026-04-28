# back-rpa-chat-comprasgov

Projeto Java (Spring Boot MVC) para monitoramento de mensagens do chat do ComprasGov, com detecção de menções por CNPJ/nome de cliente e envio de notificações por email e WhatsApp.

## Stack
- Java 17
- Spring Boot (MVC)
- Spring Data JPA
- H2
- Thymeleaf

## Executar
```bash
mvn spring-boot:run
```

## Endpoints
- `GET /` Dashboard MVC
- `POST /monitor/messages` Ingestão de mensagens do RPA
- `POST /monitor/chat-html` Ingestão de HTML da tela de chat (extração automática de mensagens)

Exemplo de payload:
```json
{
  "sourceMessageId": "chat-123",
  "content": "Mensagem citando 12.345.678/0001-90"
}
```

Exemplo de HTML bruto:
```json
{
  "sourceMessageId": "chat-page-001",
  "content": "<div id=\"chat\"><div>Pregoeiro: Mensagem para 12.345.678/0001-90</div></div>"
}
```

## Configuração de notificações
No `application.properties`:
- `app.notifications.email.to` define email destino
- `app.notifications.whatsapp.webhook-url` define webhook HTTP para integração WhatsApp
