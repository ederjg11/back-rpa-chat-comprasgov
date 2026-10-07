package br.gov.compras.rpa;


import com.microsoft.playwright.*;

import java.nio.file.Paths;

public class GeraSessao {

    public static void main(String[] args) {

        Playwright playwright = Playwright.create();

        Browser browser = playwright.firefox().launch(
                new BrowserType.LaunchOptions()
                        .setHeadless(false)
        );

        BrowserContext context = browser.newContext();

        Page page = context.newPage();

        System.out.println("🔥 Abrindo ComprasNet (área correta)...");

        // 🔥 IMPORTANTE: já entra direto no sistema real
        page.navigate("https://cnetmobile.estaleiro.serpro.gov.br/comprasnet-web/");

        System.out.println("\n👉 FAÇA LOGIN COMPLETO (gov.br)");
        System.out.println("👉 ENTRE ATÉ QUALQUER TELA INTERNA (ex: acompanhamento de compra)");
        System.out.println("👉 SOMENTE DEPOIS volte aqui e aperte ENTER\n");

        try {
            System.in.read();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 🔥 SALVA SESSÃO CORRETA
        context.storageState(new BrowserContext.StorageStateOptions()
                .setPath(Paths.get("session.json")));

        System.out.println("✅ SESSION.JSON CORRETO SALVO!");

        browser.close();
    }
}