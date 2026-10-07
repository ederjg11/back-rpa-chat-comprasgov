package br.gov.compras.rpa;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.BoundingBox;

public class TestarSessao {

    public static void main(String[] args) {

        String cpf = "06303344127";
        String senha = "Rfmh05046@";

        Playwright playwright = Playwright.create();

        Browser browser = playwright.firefox().launch(
                new BrowserType.LaunchOptions()
                        .setHeadless(false)
                        .setSlowMo(300)
        );

        BrowserContext context = browser.newContext();
        Page page = context.newPage();

        System.out.println("🔥 Acessando tela de seleção de empresa...");
        page.navigate("https://www.comprasnet.gov.br/seguro/loginFornecedorSelecEmpresa.asp");

        page.waitForTimeout(3000);

        // =========================
        // LOGIN GOV.BR
        // =========================
        page.locator("text=Entrar com Gov.br").click();
        page.waitForLoadState();

        page.fill("input[name='accountId'], #accountId", cpf);
        page.locator("#enter-account-id, button:has-text('Continuar')").click();

        page.waitForTimeout(3000);

        page.fill("input[name='password'], #password", senha);
        page.locator("#submit-button, button:has-text('Entrar')").click();

        System.out.println("⏳ Aguardando login...");
        page.waitForTimeout(8000);

        // =========================
        // SELECIONAR EMPRESA
        // =========================
        String empresa = "MEDIPHACOS INDUSTRIAS MEDICAS S/A";

        page.waitForSelector("text=" + empresa);
        page.locator("text=" + empresa).first().click();

        page.waitForTimeout(2000);

        page.locator("input[type='submit'], button:has-text('Confirmar')").click();

        System.out.println("⏳ Aguardando portal...");
        page.waitForTimeout(8000);
// =========================
        // 🔥 NOVA ESTRATÉGIA (DIRETO NA URL)
        // =========================
        System.out.println("🚀 Acessando página direta de dispensa...");

        page.navigate("https://www.comprasnet.gov.br/assinadas/dispensa_eletronica.asp");

        page.waitForTimeout(8000);

        System.out.println("🔥 URL ATUAL:");
        System.out.println(page.url());

        System.out.println("\n🔥 HTML DA PÁGINA:");
        System.out.println(page.content());

        // manter aberto pra análise
       // page.waitForTimeout(30000);

        //browser.close();


    }
}