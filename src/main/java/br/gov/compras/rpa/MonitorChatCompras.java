package br.gov.compras.rpa;

import br.gov.compras.rpa.service.MonitoraChatComprasNet;
import com.microsoft.playwright.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MonitorChatCompras {

    public static void main(String[] args) {

        String cpf = "06303344127";
        String senha = "Rfmh05046@";
        String idCompra = "16019905900332024"; // 🔥 depois vem do banco

        Playwright playwright = Playwright.create();

        Browser browser = playwright.firefox().launch(
                new BrowserType.LaunchOptions()
                        .setHeadless(false)
                        .setSlowMo(300)
        );

        BrowserContext context = browser.newContext();
        Page page = context.newPage();

        ObjectMapper mapper = new ObjectMapper();
        Set<String> mensagensVistas = new HashSet<>();

        // =========================
        // 🔥 LISTENER GLOBAL (CAPTURA AUTOMÁTICA)
        // =========================
        context.onResponse(response -> {
            try {
                if (response.url().contains("/v2/chat/" + idCompra)) {

                    if (response.status() == 200 || response.status() == 206) {

                        String body = response.text();
                        JsonNode json = mapper.readTree(body);

                        for (JsonNode msg : json) {

                            String id = msg.get("chaveMensagemNaOrigem").asText();

                            if (!mensagensVistas.contains(id)) {

                                mensagensVistas.add(id);

                                System.out.println("\n🔥 NOVA MENSAGEM:");
                                System.out.println("🕒 " + msg.get("dataHora").asText());
                                System.out.println("💬 " + msg.get("texto").asText());
                            }
                        }
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // =========================
        // LOGIN + EMPRESA (igual antes)
        // =========================
        page.navigate("https://www.comprasnet.gov.br/seguro/loginFornecedorSelecEmpresa.asp");

        page.locator("text=Entrar com Gov.br").click();
        page.waitForLoadState();

        page.fill("input[name='accountId'], #accountId", cpf);
        page.locator("#enter-account-id").click();

        page.waitForTimeout(3000);

        page.fill("input[name='password'], #password", senha);
        page.locator("#submit-button").click();

        page.waitForTimeout(8000);

        String empresa = "MEDIPHACOS INDUSTRIAS MEDICAS S/A";

        page.locator("text=" + empresa).first().click();
        page.locator("input[type='submit'], button:has-text('Confirmar')").click();

        page.waitForTimeout(8000);
        // =========================
        // 🔥 CAPTURAR NOVA ABA
        // =========================
        System.out.println("🚀 Abrindo página de dispensa...");
        // =========================
        // 🔥 FECHAR OVERLAYS / MODAIS
        // =========================
        System.out.println("👉 Fechando possíveis modais...");

        page.evaluate("""
        () => {
            // remove overlays comuns
            document.querySelectorAll('.cdk-overlay-backdrop, .modal, .overlay, .dialog, .popup')
                .forEach(e => e.remove());
        
            // tenta clicar em botões de fechar visíveis
            document.querySelectorAll('button')
                .forEach(btn => {
                    if (btn.innerText && btn.innerText.toLowerCase().includes('fechar')) {
                        btn.click();
                    }
                });
        }
        """);


        Page finalPage = page;
        Page novaPagina = context.waitForPage(() -> {
            finalPage.navigate("https://www.comprasnet.gov.br/assinadas/dispensa_eletronica.asp");
        });

        // agora você passa a usar a nova aba
        page = novaPagina;

        page.waitForLoadState();

        System.out.println("✅ Nova aba capturada:");
        System.out.println(page.url());

        // =========================
        // 🔥 ABRIR DIRETO O PREGÃO
        // =========================
        String url = "https://cnetmobile.estaleiro.serpro.gov.br/comprasnet-web/seguro/fornecedor/acompanhamento-compra?compra=" + idCompra;

        System.out.println("🚀 Abrindo pregão...");
        page.navigate(url);

        page.waitForTimeout(12000);

        // =========================
        // 🔥 ABRIR CHAT
        // =========================

        System.out.println("📄 botao clicado");

        Set<String>        vistos = new HashSet<String>();
        int                totalPag = 1;

        try {
            List<Locator> pBotoes = page.locator(
                    "app-mensagens-da-compra p-paginator button.p-paginator-page").all();
            if (!pBotoes.isEmpty()) totalPag = pBotoes.size();
        } catch (Exception ignored) {}

        System.out.println("Paginas do chat (DOM): " + totalPag);

        for (int pagina = 0; pagina < totalPag; pagina++) {

            if (pagina > 0) {
                try {
                    page.locator(
                            "app-mensagens-da-compra p-paginator button.p-paginator-page"
                    ).nth(pagina).click();
                    page.waitForTimeout(2500);
                } catch (Exception e) {
                    System.out.println("Erro ao trocar pagina DOM: " + e.getMessage());
                }
            }

            List<String> remetentes = page.locator(".mensagens-remetente").allTextContents();
            List<String> textos = page.locator(".mensagens-texto").allTextContents();
            List<String> datas = page.locator(".mensagens-data small").allTextContents();

            for (int i = 0; i < textos.size(); i++) {

                String texto = textos.get(i).trim();
                String remetente = i < remetentes.size() ? remetentes.get(i).trim() : "N/A";
                String dataStr = i < datas.size() ? datas.get(i).trim() : "";

                String chave = (remetente + "|" + dataStr + "|" + texto).hashCode() + "";
                if (vistos.contains(chave)) continue;
                vistos.add(chave);
                if (texto.isEmpty()) continue;
            }
        }

        System.out.println("📄 HTML da página:");

        String html = page.content();

        System.out.println(html.substring(0, Math.min(html.length(), 500000)));

        // =========================
// 🔥 PEGAR FRAME
// =========================
        Frame frameCorreto = null;

        for (Frame frame : page.frames()) {
            if (frame.url().contains("acompanhamento-compra")) {
                frameCorreto = frame;
                break;
            }
        }

        if (frameCorreto == null) {
            throw new RuntimeException("Frame não encontrado");
        }

// =========================
// 🔥 ESPERAR ANGULAR ATIVAR BOTÃO (CHAVE DO PROBLEMA)
// =========================
        System.out.println("👉 Aguardando botão ficar clicável...");

        frameCorreto.waitForFunction("""
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

// =========================
// 🔥 AGORA SIM LOCALIZA
// =========================
        Locator botao = frameCorreto.locator(
                "app-botao-mensagens-da-compra button"
        ).first();

// =========================
// 🔥 CLICK REAL
// =========================
        System.out.println("👉 Clicando no botão...");

        botao.click();

        System.out.println("✅ Clique executado");

        page.waitForTimeout(8000);

    }
}