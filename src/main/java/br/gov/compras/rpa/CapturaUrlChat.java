package br.gov.compras.rpa;


import com.microsoft.playwright.*;

public class CapturaUrlChat {

    public static void main(String[] args) {

        try (Playwright playwright = Playwright.create()) {

            BrowserContext context = playwright.chromium().launchPersistentContext(
                    java.nio.file.Paths.get(System.getProperty("user.home") + "/chrome-rpa-profile"),
                    new BrowserType.LaunchPersistentContextOptions()
                            .setChannel("chrome")
                            .setHeadless(false)
            );

            Page page = context.newPage();

            page.onRequest(request -> {

                if (request.url().contains("/chat/")) {
                    System.out.println("🔥 URL CAPTURADA:");
                    System.out.println(request.url());
                }
            });

            page.navigate("https://www.comprasnet.gov.br");

            System.out.println("👉 Abra o chat manualmente agora...");

            page.waitForTimeout(30000); // tempo pra você abrir chat

        }
    }
}
