package br.gov.compras.rpa;

import com.microsoft.playwright.*;

import java.nio.file.Paths;

public class SalvarSessao {

    public static void main(String[] args) {

        try (Playwright playwright = Playwright.create()) {

            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions()
                            .setHeadless(false) // importante
            );

            BrowserContext context = browser.newContext();

            Page page = context.newPage();

            System.out.println("👉 Abrindo login do ComprasNet...");
            page.navigate("https://www.comprasnet.gov.br/seguro/loginPortalFornecedor.asp");

            System.out.println("⚠️ AGORA FAÇA O LOGIN MANUAL:");
            System.out.println("- Clique em 'Entrar com Gov.br'");
            System.out.println("- Informe CPF");
            System.out.println("- Informe senha");
            System.out.println("- Resolva captcha se aparecer");
            System.out.println("- Espere entrar no sistema");

            // 👉 TEMPO PARA VOCÊ LOGAR MANUALMENTE
            Thread.sleep(60000); // 60 segundos (se quiser aumentar depois)

            // 🔥 SALVA SESSÃO
            context.storageState(
                    new BrowserContext.StorageStateOptions()
                            .setPath(Paths.get("storage.json"))
            );

            System.out.println("✅ Sessão salva em storage.json!");

            browser.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
