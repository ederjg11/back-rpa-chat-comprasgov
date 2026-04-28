package br.gov.compras.rpa.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatHtmlReaderServiceTest {

    private final ChatHtmlReaderService chatHtmlReaderService = new ChatHtmlReaderService();

    @Test
    void shouldExtractChatMessagesFromHtml() {
        String html = """
                <html>
                  <body>
                    <div>Menu</div>
                    <div id="chat-panel">
                        <div>Pregoeiro: favor proposta atualizada para Cliente Exemplo LTDA.</div>
                        <div>Fornecedor: ciente.</div>
                    </div>
                  </body>
                </html>
                """;

        var messages = chatHtmlReaderService.extractMessages(html);

        assertThat(messages).contains("Pregoeiro: favor proposta atualizada para Cliente Exemplo LTDA.");
        assertThat(messages).contains("Fornecedor: ciente.");
        assertThat(messages).doesNotContain("Menu");
    }

    @Test
    void shouldIgnoreScriptsAndStyles() {
        String html = """
                <html>
                  <head>
                    <style>.chat { color: red; }</style>
                    <script>console.log('secret');</script>
                  </head>
                  <body>
                    <div>Mensagem válida 12.345.678/0001-90</div>
                  </body>
                </html>
                """;

        var messages = chatHtmlReaderService.extractMessages(html);

        assertThat(messages).containsExactly("Mensagem válida 12.345.678/0001-90");
    }
}
