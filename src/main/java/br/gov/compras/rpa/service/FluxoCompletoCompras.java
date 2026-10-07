package br.gov.compras.rpa.service;

import com.microsoft.playwright.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Paths;
import java.util.*;

public class FluxoCompletoCompras {

    // 🔥 CONFIG
    static String CPF = "06303344127";
    static String SENHA = "Rfmh05046@";

    static String UASG = "160199";
    static String NUMERO_PREGAO = "90033/2024";
    static String ID_COMPRA = "16019905900332024";

    public static void main(String[] args) {

        Playwright playwright = Playwright.create();

        Browser browser = playwright.firefox().launch(
                new BrowserType.LaunchOptions()
                        .setHeadless(false)
                        .setSlowMo(300)
        );

        BrowserContext context = browser.newContext();
        Page page = context.newPage();


        System.out.println("🚀 Iniciando fluxo completo...");

        // =========================
        // 🔐 LOGIN GOV.BR
        // =========================
        page.navigate("https://www.comprasnet.gov.br/seguro/loginFornecedorSelecEmpresa.asp");

        page.click("text=Entrar com gov.br");

        page.waitForSelector("input[name='accountId']");
        page.fill("input[name='accountId']", CPF);
        page.click("text=Continuar");

        page.waitForSelector("input[name='password']");
        page.fill("input[name='password']", SENHA);
        page.click("text=Entrar");

        page.waitForTimeout(5000);

        // =========================
        // 🏢 SELECIONAR EMPRESA
        // =========================
        page.locator("text=MEDIPHACOS INDUSTRIAS MEDICAS").first().click();
        page.click("text=Confirmar");

        page.waitForTimeout(5000);

        // =========================
        // 🔥 ABRIR LISTA (NOVA ABA)
        // =========================
        Page finalPage = page;

        Page novaPagina = context.waitForPage(() -> {
            finalPage.navigate("https://www.comprasnet.gov.br/assinadas/dispensa_eletronica.asp");
        });

        page = novaPagina;

        page.waitForLoadState();

        System.out.println("⏳ Aguardando Angular carregar...");
        page.waitForTimeout(8000);

        // =========================
        // 🔍 ENCONTRAR PREGÃO
        // =========================
        System.out.println("🔍 Procurando pregão...");

        List<ElementHandle> elementos = page.querySelectorAll("body *");

        boolean clicou = false;

        for (ElementHandle el : elementos) {

            try {
                String texto = el.innerText();

                if (texto == null) continue;

                if (texto.contains(NUMERO_PREGAO) && texto.contains(UASG)) {

                    System.out.println("✅ Pregão encontrado!");

                    ElementHandle container = el;

                    for (int i = 0; i < 6; i++) {
                        container = container.querySelector("xpath=..");
                        if (container == null) break;
                    }

                    if (container == null) continue;

                    ElementHandle botao = container.querySelector("button:has(i.fa-plus-square)");

                    if (botao != null) {

                        botao.scrollIntoViewIfNeeded();
                        page.waitForTimeout(1000);

                        botao.click();

                        clicou = true;
                        break;
                    }
                }

            } catch (Exception ignored) {}
        }

        if (!clicou) {
            System.out.println("❌ Não encontrou o pregão.");
            return;
        }

        // =========================
        // 🚀 AGUARDAR ENTRAR NO PREGÃO
        // =========================
        page.waitForTimeout(8000);

        System.out.println("🚀 Dentro do pregão!");

        // =========================
        // 📡 INTERCEPTAR API DO CHAT
        // =========================
        ObjectMapper mapper = new ObjectMapper();
        Set<String> idsVistos = new HashSet<>();

        context.onResponse(response -> {
            try {

                String url = response.url();

                if (!url.contains("/chat/" + ID_COMPRA)) return;
                if (response.status() != 200 && response.status() != 206) return;

                String body = response.text();
                if (body == null || body.isEmpty()) return;

                System.out.println("🎯 CHAT API DETECTADA!");

                JsonNode root = mapper.readTree(body);
                JsonNode lista = root.isArray() ? root : root.path("content");

                for (JsonNode msg : lista) {

                    String id = get(msg, "id", "uuid", "idMensagem");

                    if (id == null || idsVistos.contains(id)) continue;
                    idsVistos.add(id);

                    String texto = get(msg, "texto", "mensagem", "descricao");
                    String data = get(msg, "dataHora", "data");

                    System.out.println("📨 " + data + " -> " + texto);
                }

            } catch (Exception ignored) {}
        });

        // =========================
        // 💬 ABRIR CHAT (AGORA FUNCIONA)
        // =========================
        page.mouse().wheel(0, 1000);
        page.waitForTimeout(2000);
        System.out.println("💬 Abrindo chat...");

        System.out.println("⏳ Aguardando chat ficar disponível...");

// espera o Angular terminar MESMO
        page.waitForFunction("""
() => {
    const btn = document.querySelector('app-botao-mensagens-da-compra button');
    if (!btn) return false;

    const style = window.getComputedStyle(btn);

    return (
        style.display !== 'none' &&
        style.visibility !== 'hidden' &&
        btn.offsetParent !== null
    );
}
""");

        System.out.println("💬 Chat disponível!");

// agora sim pega o botão
        Locator botaoChat = page.locator("app-botao-mensagens-da-compra button").first();

        botaoChat.scrollIntoViewIfNeeded();

        page.waitForTimeout(1000);

        botaoChat.click(new Locator.ClickOptions().setForce(true));

        System.out.println("🔥 Chat aberto!");
    }

    private static String get(JsonNode node, String... campos) {
        for (String c : campos) {
            JsonNode v = node.get(c);
            if (v != null && !v.isNull()) return v.asText();
        }
        return null;
    }
}
