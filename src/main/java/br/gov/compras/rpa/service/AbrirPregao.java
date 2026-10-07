package br.gov.compras.rpa.service;

import com.microsoft.playwright.*;

public class AbrirPregao {

    public static void main(String[] args) {

        String idPregao = "16019905000352023";

        Playwright playwright = Playwright.create();

        Browser browser = playwright.firefox().launch(
                new BrowserType.LaunchOptions().setHeadless(false)
        );

        BrowserContext context = browser.newContext();

        Page page = context.newPage();

        System.out.println("🔥 Abrindo sistema...");
        page.navigate("https://www.comprasnet.gov.br");

        System.out.println("👉 Faça login manual");

        // espera você logar
        try {
            System.in.read();
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("🔥 Navegando até a tela de acompanhamento...");

        page.navigate("https://cnetmobile.estaleiro.serpro.gov.br/comprasnet-web/seguro/fornecedor/acompanhamento-compra");

        page.waitForTimeout(5000);

        System.out.println("🔥 Tentando localizar o pregão...");

        // 🔥 procura pelo ID na tela
        page.locator("text=" + idPregao).first().click();

        page.waitForTimeout(5000);

        System.out.println("🔥 Tentando abrir o chat...");

        // 🔥 tenta clicar no botão de chat
        page.locator("text=Chat").click();

        page.waitForTimeout(10000);

        System.out.println("✅ Se abriu o chat, funcionou!");

        page.waitForTimeout(30000);

        browser.close();
    }
}