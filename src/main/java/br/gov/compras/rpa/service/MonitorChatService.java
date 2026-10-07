package br.gov.compras.rpa.service;

import br.gov.compras.rpa.db.DatabaseService;
import br.gov.compras.rpa.model.MensagemChat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.*;

import java.nio.file.Paths;

public class MonitorChatService {

    private static final String ID_COMPRA = "16019905900332024";

    public static void main(String[] args) {

        try {

            DatabaseService.criarTabela();

            Playwright playwright = Playwright.create();

            Browser browser = playwright.firefox().launch(
                    new BrowserType.LaunchOptions()
                            .setHeadless(false)
                            .setSlowMo(200)
            );

            BrowserContext context = browser.newContext(
                    new Browser.NewContextOptions()
                            .setStorageStatePath(Paths.get("session.json"))
            );

            ObjectMapper mapper = new ObjectMapper();

            // =========================
            // 🔥 CAPTURA API CHAT
            // =========================
            context.onResponse(response -> {

                if (!response.url().contains("/v2/chat")) return;

                try {

                    String body = response.text();

                    if (body == null || body.isEmpty()) return;

                    JsonNode root = mapper.readTree(body);

                    JsonNode lista = root.isArray() ? root : root.path("content");

                    for (JsonNode msg : lista) {

                        MensagemChat m = new MensagemChat();

                        m.id = getText(msg, "id", "uuid", "chaveMensagemNaOrigem");
                        if (m.id == null || m.id.isEmpty()) continue;

                        m.texto = getText(msg, "texto", "mensagem", "conteudo");
                        m.remetente = getText(msg, "remetente", "tipoRemetente");
                        m.data = getText(msg, "dataHora", "dataEnvio");

                        JsonNode itemNode = msg.path("numeroItem");
                        if (!itemNode.isMissingNode()) {
                            m.itemLote = itemNode.asInt();
                        }

                        DatabaseService.salvar(m);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            Page page = context.newPage();

            System.out.println("🚀 Abrindo chat direto...");

            page.navigate("https://cnetmobile.estaleiro.serpro.gov.br/comprasnet-web/seguro/fornecedor/acompanhamento-compra?compra=" + ID_COMPRA);

            page.waitForTimeout(8000);

            // =========================
            // 🔥 ABRIR CHAT
            // =========================
            page.locator("app-botao-mensagens-da-compra button").first().click();

            page.waitForTimeout(5000);

            // =========================
            // 🔥 BUSCAR TODAS PAGINAS
            // =========================
            for (int pageIndex = 0; pageIndex < 20; pageIndex++) {

                int p = pageIndex;

                page.evaluate(
                        "([id, page]) => {" +
                                "   fetch('/comprasnet-mensagem/v2/chat/' + id + '?size=50&page=' + page + '&legadoAsp=false')" +
                                "}",
                        new Object[]{ID_COMPRA, p}
                );

                page.waitForTimeout(1500);
            }

            System.out.println("✅ Finalizado");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =========================
    // 🔧 UTIL
    // =========================
    private static String getText(JsonNode node, String... campos) {
        for (String c : campos) {
            JsonNode n = node.get(c);
            if (n != null && !n.isNull()) {
                return n.asText();
            }
        }
        return "";
    }
}