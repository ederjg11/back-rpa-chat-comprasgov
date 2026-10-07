package br.gov.compras.rpa.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class MonitoraChatComprasNetV1 {

    // ================================================================
    // CONFIGURE AQUI
    // ================================================================
    private static final String CPF             = "06303344127";
    private static final String SENHA           = "Rfmh05046@";
    private static final String ID_COMPRA       = "16019905900332024";
    private static final String UASG            = "160199";
    private static final String NUMERO_PREGAO   = "90033/2024";
    private static final int    OPORTUNIDADE_ID = 1;

    private static Connection obterConexao() throws SQLException {
        throw new UnsupportedOperationException(
                "Substitua este metodo pelo seu proprio de conexao com o banco MySQL."
        );
    }
    // ================================================================

    private static final DateTimeFormatter[] FORMATOS_DATA = {
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'as' HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    };

    private static final List<MensagemChat> mensagensCapturadas = new ArrayList<MensagemChat>();
    private static final Set<String>        idsVistos           = new HashSet<String>();
    private static final ObjectMapper       mapper              = new ObjectMapper();

    private static volatile String  tokenCaptcha  = null;
    private static volatile String  tokenBearer   = null;
    private static volatile boolean apiRespondeu  = false;

    // ================================================================
    // MAIN
    // ================================================================
    public static void main(String[] args) throws Exception {

        Playwright playwright = Playwright.create();

        Browser browser = playwright.firefox().launch(
                new BrowserType.LaunchOptions()
                        .setHeadless(false)
                        .setSlowMo(200)
        );

        BrowserContext context = browser.newContext();
        final Page page0 = context.newPage();

        // ================================================================
        // LISTENER DE REQUEST — captura token captcha e bearer
        // ================================================================
        context.onRequest(new Consumer<Request>() {
            @Override
            public void accept(Request request) {
                try {
                    String url = request.url();
                    if (!url.contains("/v2/chat/" + ID_COMPRA)) return;

                    System.out.println(">>> REQUEST capturado: " + url.substring(0, Math.min(120, url.length())));

                    if (url.contains("captcha=")) {
                        String query = url.contains("?") ? url.substring(url.indexOf('?') + 1) : "";
                        for (String param : query.split("&")) {
                            if (param.startsWith("captcha=")) {
                                tokenCaptcha = param.substring("captcha=".length());
                                System.out.println("Token captcha capturado ("
                                        + tokenCaptcha.length() + " chars): "
                                        + tokenCaptcha.substring(0, Math.min(40, tokenCaptcha.length())) + "...");
                            }
                        }
                    }

                    String auth = request.headerValue("authorization");
                    if (auth == null) auth = request.headerValue("Authorization");
                    if (auth != null && auth.startsWith("Bearer ")) {
                        tokenBearer = auth;
                        System.out.println("Token Bearer capturado ("
                                + auth.length() + " chars).");
                    }

                } catch (Exception e) {
                    System.out.println("Erro no listener request: " + e.getMessage());
                }
            }
        });

        // ================================================================
        // LISTENER DE RESPONSE — loga TUDO do chat, inclusive erros
        // ================================================================
        context.onResponse(new Consumer<Response>() {
            @Override
            public void accept(Response response) {
                try {
                    String url = response.url();

                    // Captura qualquer resposta relacionada ao chat
                    if (!url.contains("comprasnet-mensagem") && !url.contains("/chat/")) return;

                    int status = response.status();
                    System.out.println(">>> RESPONSE: status=" + status + " url=" + url.substring(0, Math.min(120, url.length())));

                    // Loga o body independente do status para ver o erro real
                    String body = "";
                    try {
                        body = response.text();
                    } catch (Exception e) {
                        System.out.println("    (nao foi possivel ler body: " + e.getMessage() + ")");
                    }

                    if (body != null && !body.trim().isEmpty()) {
                        // Mostra os primeiros 500 chars do body para diagnostico
                        System.out.println("    BODY: " + body.substring(0, Math.min(500, body.length())));
                    } else {
                        System.out.println("    BODY: (vazio)");
                    }

                    // Processa apenas se for sucesso
                    if (status == 200 || status == 206) {
                        if (url.contains("/v2/chat/" + ID_COMPRA)) {
                            System.out.println("    --> API de mensagens respondeu com sucesso!");
                            apiRespondeu = true;

                            JsonNode root = mapper.readTree(body);
                            JsonNode lista = root.isArray() ? root
                                    : root.has("content") ? root.get("content") : null;

                            if (lista != null && lista.isArray()) {
                                processarMensagens(lista);
                            }
                        }
                    }

                } catch (Exception e) {
                    // ignora
                }
            }
        });

        // ================================================================
        // LOGIN GOV.BR
        // ================================================================
        System.out.println("Fazendo login...");
        page0.navigate("https://www.comprasnet.gov.br/seguro/loginFornecedorSelecEmpresa.asp");
        page0.locator("text=Entrar com Gov.br").click();
        page0.waitForLoadState();

        page0.fill("input[name='accountId'], #accountId", CPF);
        page0.locator("#enter-account-id").click();
        page0.waitForTimeout(3000);

        page0.fill("input[name='password'], #password", SENHA);
        page0.locator("#submit-button").click();
        page0.waitForTimeout(8000);

        // ================================================================
        // SELECAO DE EMPRESA
        // ================================================================
        String empresa = "MEDIPHACOS INDUSTRIAS MEDICAS S/A";
        page0.locator("text=" + empresa).first().click();
        page0.locator("input[type='submit'], button:has-text('Confirmar')").click();
        page0.waitForTimeout(8000);

        // ================================================================
        // FECHAR OVERLAYS
        // ================================================================
        page0.evaluate(
                "function() {" +
                        "  document.querySelectorAll('.cdk-overlay-backdrop,.modal,.overlay,.dialog,.popup')" +
                        "    .forEach(function(e){ e.remove(); });" +
                        "  document.querySelectorAll('button').forEach(function(btn){" +
                        "    if (btn.innerText && btn.innerText.toLowerCase().indexOf('fechar') >= 0)" +
                        "      btn.click();" +
                        "  });" +
                        "}"
        );

        // ================================================================
        // ABRIR NOVA ABA
        // ================================================================
        System.out.println("Abrindo lista de dispensas...");
        final Page[] paginaRef = { page0 };

        Page novaPagina = context.waitForPage(new Runnable() {
            public void run() {
                paginaRef[0].navigate(
                        "https://www.comprasnet.gov.br/assinadas/dispensa_eletronica.asp");
            }
        });

        final Page page = novaPagina;
        page.waitForLoadState();
        System.out.println("Nova aba: " + page.url());
        page.waitForTimeout(9000);

        // ================================================================
        // ENCONTRAR E CLICAR NO PREGAO
        // ================================================================
        System.out.println("Procurando pregao " + NUMERO_PREGAO + "...");
        List<Locator> cards = page.locator(".cp-itens-card").all();
        boolean clicou = false;

        for (Locator card : cards) {
            try {
                String texto = card.innerText();
                if (texto != null && texto.contains(NUMERO_PREGAO) && texto.contains(UASG)) {
                    System.out.println("Pregao encontrado!");
                    Locator botao = card.locator("button:has(i.fa-plus-square)").first();
                    botao.scrollIntoViewIfNeeded();
                    page.waitForTimeout(1000);
                    botao.click();
                    clicou = true;
                    break;
                }
            } catch (Exception ignored) {}
        }

        if (!clicou) {
            System.out.println("Pregao nao encontrado. Encerrando.");
            browser.close();
            playwright.close();
            return;
        }

        page.waitForTimeout(8000);

        // ================================================================
        // RELOAD E AQUECIMENTO
        // ================================================================
        System.out.println("Recarregando...");
        page.waitForTimeout(5000);
        page.reload();
        page.waitForLoadState();
        page.waitForTimeout(8000);

        System.out.println("Aquecendo contexto...");
        page.mouse().move(100 + Math.random() * 300, 200 + Math.random() * 200);
        page.mouse().wheel(0, 300);
        page.waitForTimeout(5000);
        page.mouse().move(400 + Math.random() * 200, 350 + Math.random() * 150);
        page.waitForTimeout(3000);

        // ================================================================
        // CLICAR NO BOTAO DO CHAT
        // ================================================================
        System.out.println("Procurando botao do chat...");
        Locator botoes = page.locator("app-botao-mensagens-da-compra button.br-button");
        botoes.first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.ATTACHED)
                .setTimeout(15000));

        page.mouse().move(300 + Math.random() * 200, 200 + Math.random() * 100);
        page.waitForTimeout(500 + (long)(Math.random() * 400));
        page.mouse().move(500 + Math.random() * 100, 350 + Math.random() * 50);
        page.waitForTimeout(400);
        page.evaluate("window.scrollBy(0, 200)");
        page.waitForTimeout(800);

        page.evaluate(
                "function() {" +
                        "  var botoes = document.querySelectorAll(" +
                        "    'app-botao-mensagens-da-compra button.br-button');" +
                        "  if (botoes.length > 0) botoes[botoes.length - 1].click();" +
                        "}"
        );

        System.out.println("Clique executado. Aguardando resposta...");

        for (int i = 0; i < 30; i++) {
            page.waitForTimeout(500);
            if (apiRespondeu) break;
        }

        // ================================================================
        // RESULTADO
        // ================================================================
        if (!apiRespondeu) {
            System.out.println("API nao respondeu com sucesso.");
            System.out.println("tokenCaptcha presente: " + (tokenCaptcha != null));
            System.out.println("tokenBearer presente:  " + (tokenBearer  != null));
        }

        System.out.println("Total capturado: " + mensagensCapturadas.size() + " mensagens.");
        System.out.println("--- FIM DO DIAGNOSTICO ---");

        // Mantem browser aberto para inspecao manual
        // browser.close();
        // playwright.close();
    }

    // ================================================================
    // PROCESSA JSON DE MENSAGENS
    // ================================================================
    private static void processarMensagens(JsonNode lista) {
        for (int i = 0; i < lista.size(); i++) {
            JsonNode msg = lista.get(i);

            String id = campoTexto(msg, "chaveMensagemNaOrigem", "id", "idMensagem", "uuid");
            if (id == null || id.isEmpty()) continue;
            if (idsVistos.contains(id)) continue;
            idsVistos.add(id);

            String remetente = campoTexto(msg, "tipoRemetente", "remetente", "tipo", "nomRemetente");
            String texto     = campoTexto(msg, "texto", "mensagem", "descricao", "conteudo");
            String dataStr   = campoTexto(msg, "dataHora", "data", "dataCadastro", "dataEnvio");

            MensagemChat m = new MensagemChat();
            m.id        = id;
            m.remetente = remetente != null ? remetente : "N/A";
            m.texto     = texto     != null ? texto     : "";
            m.dataStr   = dataStr   != null ? dataStr   : "";

            mensagensCapturadas.add(m);
            System.out.println("Mensagem | " + m.remetente + " | " + m.dataStr
                    + " | " + (m.texto.length() > 60 ? m.texto.substring(0, 60) + "..." : m.texto));
        }
    }

    private static String campoTexto(JsonNode node, String... campos) {
        for (String campo : campos) {
            JsonNode n = node.path(campo);
            if (!n.isMissingNode() && !n.isNull()) {
                String val = n.asText().trim();
                if (!val.isEmpty()) return val;
            }
        }
        return null;
    }

    private static LocalDateTime parsearData(String dataStr) {
        if (dataStr == null || dataStr.trim().isEmpty()) return null;
        String s = dataStr.trim().replace(" às ", " ").replace(" as ", " ");
        for (DateTimeFormatter fmt : FORMATOS_DATA) {
            try { return LocalDateTime.parse(s, fmt); }
            catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private static class MensagemChat {
        String  id;
        String  remetente;
        String  texto;
        String  dataStr;
        Integer itemLote;
    }
}