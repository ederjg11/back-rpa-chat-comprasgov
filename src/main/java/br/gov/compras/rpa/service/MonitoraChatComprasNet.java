package br.gov.compras.rpa.service;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.nio.file.Paths;
import java.util.List;

public class MonitoraChatComprasNet{

    private static final String CPF = "06303344127";
    private static final String SENHA = "Rfmh05046@";
    private static final String UASG = "160199";
    private static final String NUMERO_PREGAO = "90033/2024";

    public static void main(String[] args) {

        Playwright playwright = Playwright.create();

        Browser browser = playwright.firefox().launch(
                new BrowserType.LaunchOptions()
                        .setHeadless(false)
                        .setSlowMo(200)
        );

        // 🔥 SESSÃO PERSISTENTE
        BrowserContext context = browser.newContext(
                new Browser.NewContextOptions()
                        .setStorageStatePath(Paths.get("session.json"))
        );

        // 🔥 LOG CHAT
        context.onRequest(request -> {
            if (request.url().contains("/v2/chat")) {
                System.out.println("\n🚀 REQUEST CHAT:");
                System.out.println(request.url());
            }
        });

        context.onResponse(response -> {
            if (response.url().contains("/v2/chat")) {
                System.out.println("\n🔥 RESPONSE CHAT:");
                System.out.println("STATUS: " + response.status());
            }
        });

        Page page = context.newPage();

        // =====================================================
        // LOGIN
        // =====================================================
        System.out.println("Fazendo login...");
        page.navigate("https://www.comprasnet.gov.br/seguro/loginFornecedorSelecEmpresa.asp");

        page.locator("text=Entrar com Gov.br").click();
        page.waitForLoadState();

        page.fill("input[name='accountId'], #accountId", CPF);
        page.locator("#enter-account-id").click();
        page.waitForTimeout(3000);

        page.fill("input[name='password'], #password", SENHA);
        page.locator("#submit-button").click();
        page.waitForTimeout(8000);

        // 🔥 SALVA SESSÃO
        context.storageState(new BrowserContext.StorageStateOptions()
                .setPath(Paths.get("session.json")));

        // =====================================================
        // EMPRESA
        // =====================================================
        page.locator("text=MEDIPHACOS INDUSTRIAS MEDICAS S/A").first().click();
        page.locator("input[type='submit'], button:has-text('Confirmar')").click();
        page.waitForTimeout(8000);

        // =====================================================
        // ABRIR PREGAO
        // =====================================================
        System.out.println("Abrindo pregão...");

        Page finalPage = page;
        Page novaPagina = context.waitForPage(() -> {
            finalPage.navigate("https://www.comprasnet.gov.br/assinadas/dispensa_eletronica.asp");
        });

        page = novaPagina;
        page.waitForLoadState();
        page.waitForTimeout(8000);

        // =====================================================
        // ENCONTRAR PREGAO CORRETO
        // =====================================================
        System.out.println("Procurando pregão...");

        List<Locator> cards = page.locator(".cp-itens-card").all();
        boolean clicou = false;

        for (Locator card : cards) {
            String texto = card.innerText();

            if (texto != null && texto.contains(NUMERO_PREGAO) && texto.contains(UASG)) {

                System.out.println("Pregão encontrado!");

                Locator botao = card.locator("button:has(i.fa-plus-square)").first();

                botao.scrollIntoViewIfNeeded();
                page.waitForTimeout(1000);
                botao.click();

                clicou = true;
                break;
            }
        }

        if (!clicou) {
            System.out.println("Pregão não encontrado.");
            return;
        }

        page.waitForTimeout(8000);

        // =====================================================
        // 🔥 RELOAD CONTROLADO (ESSENCIAL)
        // =====================================================
        System.out.println("Recarregando página...");
        page.reload();
        page.waitForLoadState();
        page.waitForTimeout(8000);

        // =====================================================
        // ABRIR CHAT
        // =====================================================
        System.out.println("Abrindo chat...");

        Locator chatBtn = page.locator("app-botao-mensagens-da-compra button.br-button").last();

        chatBtn.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(20000));

        // simula leve interação humana
        page.mouse().move(300, 200);
        page.waitForTimeout(800);

        try {
            chatBtn.click();
        } catch (Exception e) {
            System.out.println("Fallback JS click...");
            page.evaluate("document.querySelectorAll('app-botao-mensagens-da-compra button.br-button')[0].click()");
        }

        page.waitForTimeout(5000);

        System.out.println("Fluxo finalizado.");
    }
}