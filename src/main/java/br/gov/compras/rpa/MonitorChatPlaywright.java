package br.gov.compras.rpa;


import com.microsoft.playwright.*;

public class MonitorChatPlaywright {

    public static void main(String[] args) {

        Playwright playwright = Playwright.create();

        Browser browser = playwright.firefox().launch(
                new BrowserType.LaunchOptions()
                        .setHeadless(false)
        );

        BrowserContext context = browser.newContext();

        // 🔥 AQUI É O PASSO 2 (ANTES DO PAGE)
        context.onResponse(response -> {
            try {
                String url = response.url();

                if (url.contains("/v2/chat/")) {

                    System.out.println("\n🔥 CHAT CAPTURADO:");
                    System.out.println("URL: " + url);
                    System.out.println("STATUS: " + response.status());

                    if (response.status() == 200 || response.status() == 206) {

                        try {
                            String body = response.text();

                            System.out.println("📦 MENSAGENS:");
                            System.out.println(body);

                        } catch (Exception e) {
                            System.out.println("⚠️ Não conseguiu ler body");
                        }
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // AGORA cria a página
        Page page = context.newPage();

        System.out.println("🔥 Abrindo ComprasNet...");
        page.navigate("https://www.comprasnet.gov.br");

        System.out.println("👉 Faça login e abra o chat");

        // mantém rodando
        while (true) {
            page.waitForTimeout(5000);
        }
    }
}