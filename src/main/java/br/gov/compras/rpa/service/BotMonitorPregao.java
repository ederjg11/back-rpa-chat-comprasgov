package br.gov.compras.rpa.service;


import com.microsoft.playwright.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class BotMonitorPregao {

    private static final String SESSION_FILE = "session.json";

    public static void main(String[] args) {

        String idPregao = "16019905000352023"; // depois vem do banco

        Playwright playwright = Playwright.create();

        Browser browser = playwright.firefox().launch(
                new BrowserType.LaunchOptions().setHeadless(false)
        );

        // 🔥 AQUI: usa a sessão salva
        BrowserContext context = browser.newContext(
                new Browser.NewContextOptions()
                        .setStorageStatePath(Paths.get(SESSION_FILE))
        );

        Page page = context.newPage();

        Set<String> vistos = new HashSet<>();
        ObjectMapper mapper = new ObjectMapper();

        // 🔥 listener do chat
        context.onResponse(response -> {
            try {
                if (response.url().contains("/v2/chat/" + idPregao)) {

                    if (response.status() == 200 || response.status() == 206) {

                        String body = response.text();
                        JsonNode json = mapper.readTree(body);

                        for (JsonNode msg : json) {

                            String id = msg.get("chaveMensagemNaOrigem").asText();

                            if (!vistos.contains(id)) {
                                vistos.add(id);

                                String texto = msg.get("texto").asText();
                                String data = msg.get("dataHora").asText();

                                System.out.println("\n🔥 NOVA MENSAGEM:");
                                System.out.println("🕒 " + data);
                                System.out.println("💬 " + texto);
                            }
                        }
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // 🔥 abre direto no sistema (já logado)
        page.navigate("https://cnetmobile.estaleiro.serpro.gov.br/comprasnet-web/seguro/fornecedor/acompanhamento-compra");

        page.waitForTimeout(5000);

        System.out.println("🚀 Iniciando monitoramento do pregão: " + idPregao);

        // 🔥 dispara fetch interno (sem captcha externo)
        while (true) {

            page.evaluate("""
                (id) => {
                    window.fetch(`/comprasnet-mensagem/v2/chat/${id}?size=10&page=0&legadoAsp=false`);
                }
            """, idPregao);

            page.waitForTimeout(10000); // 10s
        }
    }
}
