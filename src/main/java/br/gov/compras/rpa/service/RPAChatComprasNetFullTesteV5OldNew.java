package br.gov.compras.rpa.service;

import br.gov.compras.rpa.model.Oportunidade;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Component
@EnableScheduling
public class RPAChatComprasNetFullTesteV5OldNew {

    // =========================
    // LOGIN
    // =========================
    private static final String CPF    = "06303344127";
    private static final String SENHA  = "Rfmh05046@";

    // =========================
    // BANCO
    // =========================
    private static String DB_URL;
    private static String DB_USER;
    private static String DB_PASSWORD;

    // =========================
    // CONTROLE DE FLUXO
    // =========================
    private static int     countNovaMensagem = 0;
    private static boolean mensagemExiste    = false;

    // =========================
    // WHATSAPP
    // =========================
    private static final String WHATS_NUMERO = "5534996461183";
    private static final String WHATS_GRUPO  = "ChatComprasGov";

    // =========================
    // SESSION
    // =========================
    private static final String SESSION_FILE = "session.json";

    // =========================
    // OPORTUNIDADES
    // =========================
    private static List<Oportunidade> OPORTUNIDADES;

    // =========================
    // JSON CHAT
    // =========================
    private static volatile String ULTIMO_JSON_CHAT = "";

    // =========================
    // TWOCAPTCHA
    // =========================
    private static final String TWOCAPTCHA_API_KEY = "52067df225b4ea06685c5ea1d898f88b";

    // =========================
    // PROXY RESIDENCIAL (opcional)
    // =========================
    private static final String PROXY_SERVER   = null;
    private static final String PROXY_USERNAME = null;
    private static final String PROXY_PASSWORD = null;

    // =========================
    // ENUM ESTADO PÓS-CPF
    // =========================
    private enum EstadoPosCpf {
        CAMPO_SENHA_VISIVEL,
        CAPTCHA_VISIVEL,
        ERRO_DESCONHECIDO
    }

    // =========================
    // RUN
    // =========================
    public void run() {
        Page imagemErroCritico = null;
        try {

            carregarConfigBanco();
            List<Oportunidade> oportunidades = buscarOportunidadesDoBanco();

            if (oportunidades.isEmpty()) {
                System.out.println("Nenhuma oportunidade encontrada no banco. Encerrando.");
                return;
            }

            System.out.println("Total de oportunidades carregadas: " + oportunidades.size());
            OPORTUNIDADES = oportunidades;

            // =========================
            // PLAYWRIGHT
            // =========================
            Playwright playwright = Playwright.create();

            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setSlowMo(600)
                    .setArgs(List.of("--ignore-certificate-errors"));

            if (PROXY_SERVER != null && !PROXY_SERVER.isEmpty()) {
                com.microsoft.playwright.options.Proxy proxy =
                        new com.microsoft.playwright.options.Proxy(PROXY_SERVER);
                if (PROXY_USERNAME != null) proxy.setUsername(PROXY_USERNAME);
                if (PROXY_PASSWORD != null) proxy.setPassword(PROXY_PASSWORD);
                launchOptions.setProxy(proxy);
                System.out.println("🌐 Proxy residencial configurado: " + PROXY_SERVER);
            }

            Browser browser = playwright.firefox().launch(launchOptions);

            BrowserContext context = criarContextoHumano(browser);

            ObjectMapper mapper = new ObjectMapper();

            // =========================
            // LISTENER CHAT
            // =========================
            context.onResponse(response -> {
                try {
                    if (!response.url().contains("/v2/chat")) {
                        return;
                    }
                    System.out.println("\n📥 CHAT RESPONSE");
                    System.out.println(response.url());
                    System.out.println("STATUS: " + response.status());
                    String body = response.text();
                    if (body == null || body.isEmpty()) {
                        System.out.println("⚠️ BODY VAZIO");
                        return;
                    }
                    ULTIMO_JSON_CHAT = body;
                    System.out.println("📦 JSON RECEBIDO");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            // =========================
            // PAGE
            // =========================
            Page page = context.newPage();
            imagemErroCritico = page;

            realizarLoginRobusto(context, page);

            // =========================
            // ABRE PORTAL
            // =========================
            Page finalPage = page;
            Page portalPage = context.waitForPage(() -> {
                finalPage.navigate(
                        "https://www.comprasnet.gov.br/assinadas/dispensa_eletronica.asp"
                );
            });

            page = portalPage;
            page.waitForLoadState();
            page.waitForTimeout(10000);

            // =========================
            // LOOP OPORTUNIDADES
            // =========================
            for (int indice = 0; indice < OPORTUNIDADES.size(); indice++) {

                mensagemExiste    = false;
                countNovaMensagem = 0;

                Oportunidade oportunidade = OPORTUNIDADES.get(indice);

                try {

                    System.out.println("\n==================================");
                    System.out.println("🚀 PROCESSANDO OPORTUNIDADE");
                    System.out.println("CLIENTE: " + oportunidade.getCliente());
                    System.out.println("UASG: "    + oportunidade.getUasg());
                    System.out.println("PREGÃO: "  + oportunidade.getNumeroPregao());
                    System.out.println("==================================");

                    if (indice == 0) {

                        page.locator(".cp-itens-card")
                                .first()
                                .waitFor(new Locator.WaitForOptions()
                                        .setTimeout(30000)
                                        .setState(WaitForSelectorState.VISIBLE));

                        List<Locator> cards = page.locator(".cp-itens-card").all();
                        System.out.println("📦 TOTAL CARDS: " + cards.size());

                        boolean encontrou = false;

                        for (Locator card : cards) {
                            try {
                                String texto = card.innerText();
                                if (texto == null) continue;

                                System.out.println("\n-------------------");
                                System.out.println(texto);

                                if (texto.contains(oportunidade.getNumeroPregao())
                                        && texto.contains(oportunidade.getUasg())) {
                                    encontrou = true;
                                    System.out.println("✅ OPORTUNIDADE ENCONTRADA");
                                    card.locator("button:has(i.fa-plus-square)").first().click();
                                    page.waitForTimeout(8000);
                                    break;
                                }
                            } catch (Exception e) {
                                System.out.println("Erro ao processar card, mas continuando: " + e.getMessage());
                                tirarPrintDebug(page, "erro_oportunidade_" + indice + ".png");
                                e.printStackTrace();
                            }
                        }

                        if (!encontrou) {
                            System.out.println("⚠️ OPORTUNIDADE NÃO ENCONTRADA");
                            continue;
                        }

                    } else {

                        System.out.println("\n🚀 ABRINDO VIA URL DIRETA DA OPORTUNIDADE: " + oportunidade.getId());

                        String uasg = String.format("%06d",
                                Integer.parseInt(oportunidade.getUasg()));

                        String pregao = oportunidade.getNumeroPregao()
                                .replace("/", "")
                                .replace("-", "")
                                .trim();

                        String numeroCompra = uasg + "05" + pregao;

                        String urlCompra =
                                "https://cnetmobile.estaleiro.serpro.gov.br/comprasnet-web/seguro/fornecedor/acompanhamento-compra?compra="
                                        + numeroCompra;

                        System.out.println(urlCompra);

                        Locator fecharChat = page.locator(
                                "button[aria-label='Close'], button.p-dialog-header-icon");

                        if (fecharChat.count() > 0) {
                            try {
                                fecharChat.first().click();
                                page.waitForTimeout(8000);
                            } catch (Exception ignored) {
                                System.out.println("ERRO AO FECHAR CHAT, mas continuando: " + ignored.getMessage());
                                tirarPrintDebug(page, "erro_oportunidade_" + indice + ".png");
                            }
                        }

                        navegarComResiliencia(page, urlCompra);
                        System.out.println("✅ DETALHE CARREGADO");
                    }

                    page.waitForLoadState(LoadState.NETWORKIDLE);
                    page.waitForTimeout(10000);

                    Locator chatBtn = page.locator("app-botao-mensagens-da-compra button").last();
                    chatBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
                    chatBtn.click();
                    page.waitForLoadState(LoadState.NETWORKIDLE);
                    page.waitForTimeout(5000);

                    // Verifica se apareceu captcha ao abrir o chat
                    if (captchaVisivel(page)) {
                        System.out.println("⚠️ CAPTCHA AO ABRIR CHAT — tentando resolver...");
                        resolverCaptchaPortal(page);
                        page.waitForTimeout(5000);
                    }

                    page.waitForTimeout(5000);
                    System.out.println("✅ CHAT ABERTO");

                    capturarMensagensPaginadasComInteracao(oportunidade, page, mapper);

                    if (mensagemExiste) {
                        System.out.println("Mensagem ja existente no banco, Saindo do loop. Oportunidade: " + oportunidade.getId());
                        mensagemExiste = false;
                        continue;
                    }

                } catch (Exception e) {
                    System.out.println("❌ ERRO OPORTUNIDADE: " + oportunidade.getId());
                    tirarPrintDebug(page, "erro_oportunidade_" + indice + ".png");
                    e.printStackTrace();
                }
            }

            System.out.println("\n✅ FINALIZADO");
            deletarSessao();

        } catch (Exception e) {
            System.out.println("ERRO CRITICO: " + e.getMessage());
            tirarPrintDebug(imagemErroCritico, "erro_critico_11.png");
            deletarSessao();
            e.printStackTrace();
        }
    }

    // =========================
    // CONTEXTO ANTI-FINGERPRINT
    // =========================
    private static BrowserContext criarContextoHumano(Browser browser) throws Exception {

        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) "
                + "Gecko/20100101 Firefox/125.0";

        Browser.NewContextOptions opts = new Browser.NewContextOptions()
                .setUserAgent(userAgent)
                .setViewportSize(1366, 768)
                .setLocale("pt-BR")
                .setTimezoneId("America/Sao_Paulo")
                .setIgnoreHTTPSErrors(true)
                .setBypassCSP(true)
                .setExtraHTTPHeaders(Map.of(
                        "Accept-Language",    "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7",
                        "Accept-Encoding",    "gzip, deflate, br",
                        "sec-ch-ua-platform", "\"Windows\""
                ));

        if (Files.exists(Paths.get(SESSION_FILE))) {
            opts.setStorageStatePath(Paths.get(SESSION_FILE));
            System.out.println("🔄 Sessão carregada");
        } else {
            System.out.println("⚠️ Nova sessão");
        }

        BrowserContext context = browser.newContext(opts);

        context.addInitScript("""
            Object.defineProperty(navigator, 'webdriver', { get: () => undefined, configurable: true });
            delete window.__playwright;
            delete window.__pw_manual;
            delete window.__pwInitScripts;
            window.chrome = undefined;
            Object.defineProperty(navigator, 'plugins', {
                get: () => {
                    const arr = [
                        { name: 'PDF Viewer',         filename: 'internal-pdf-viewer',            description: 'Portable Document Format' },
                        { name: 'Chrome PDF Viewer',  filename: 'mhjfbmdgcfjbbpaeojofohoefgiehjai', description: 'Portable Document Format' },
                        { name: 'Chromium PDF Viewer',filename: 'internal-pdf-viewer',            description: 'Portable Document Format' }
                    ];
                    arr.item      = (i) => arr[i];
                    arr.namedItem = (n) => arr.find(p => p.name === n) || null;
                    arr.refresh   = () => {};
                    return arr;
                }, configurable: true
            });
            Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => 4, configurable: true });
            Object.defineProperty(navigator, 'deviceMemory',        { get: () => 8, configurable: true });
            Object.defineProperty(screen, 'colorDepth',  { get: () => 24, configurable: true });
            Object.defineProperty(screen, 'pixelDepth',  { get: () => 24, configurable: true });
            Object.defineProperty(navigator, 'mimeTypes', {
                get: () => {
                    const arr = [
                        { type: 'application/pdf', suffixes: 'pdf', description: '' },
                        { type: 'application/x-google-chrome-pdf', suffixes: 'pdf', description: '' }
                    ];
                    arr.item      = (i) => arr[i];
                    arr.namedItem = (n) => arr.find(m => m.type === n) || null;
                    return arr;
                }, configurable: true
            });
            const originalQuery = window.navigator.permissions
                ? window.navigator.permissions.query.bind(window.navigator.permissions) : null;
            if (originalQuery) {
                window.navigator.permissions.query = (parameters) =>
                    parameters.name === 'notifications'
                        ? Promise.resolve({ state: Notification.permission })
                        : originalQuery(parameters);
            }
        """);

        return context;
    }

    // =========================
    // DETECÇÃO DE ESTADO PÓS-CPF
    // =========================

    /**
     * Após submeter o CPF, detecta qual tela apareceu.
     * Detecta campo de senha, captcha de grid de imagens (Gov.br) ou hCaptcha.
     */
    private static EstadoPosCpf detectarEstadoPosCpf(Page page, int maxEsperaMs) {

        System.out.println("🔍 Aguardando estado pós-CPF (max " + maxEsperaMs + "ms)...");
        long inicio = System.currentTimeMillis();

        while (System.currentTimeMillis() - inicio < maxEsperaMs) {
            try {
                // Prioridade 1: campo de senha visível = fluxo sem captcha
                Locator campoSenha = page.locator("input[name='password'], #password");
                if (campoSenha.count() > 0 && campoSenha.first().isVisible()) {
                    System.out.println("✅ Campo de senha detectado — fluxo sem captcha");
                    return EstadoPosCpf.CAMPO_SENHA_VISIVEL;
                }

                // Prioridade 2: captcha visível (grid de imagens ou hCaptcha)
                if (captchaVisivel(page)) {
                    System.out.println("🔐 Captcha detectado na página");
                    return EstadoPosCpf.CAPTCHA_VISIVEL;
                }

                // Prioridade 3: texto de erro de validação de captcha
                if (page.locator("text=Não foi possível realizar a validação do Captcha").count() > 0) {
                    System.out.println("⚠️ Texto de erro de captcha encontrado na página");
                    return EstadoPosCpf.CAPTCHA_VISIVEL;
                }

                page.waitForTimeout(800);

            } catch (Exception e) {
                System.out.println("Erro ao detectar estado pós-CPF: " + e.getMessage());
            }
        }

        System.out.println("⚠️ Timeout ao detectar estado pós-CPF");
        tirarPrintDebug(page, "timeout_estado_pos_cpf_" + System.currentTimeMillis() + ".png");
        return EstadoPosCpf.ERRO_DESCONHECIDO;
    }

    // =========================
    // LOGIN ROBUSTO
    // =========================
    private static void realizarLoginRobusto(BrowserContext context, Page page) throws Exception {

        System.out.println("🔐 Iniciando login robusto...");

        page.navigate(
                "https://www.comprasnet.gov.br/seguro/loginFornecedorSelecEmpresa.asp"
        );
        page.waitForTimeout(4000);

        // Verifica se a sessão salva ainda é válida
        if (page.locator("text=MEDIPHACOS INDUSTRIAS MEDICAS S/A").count() > 0
                || page.url().contains("dispensa_eletronica")
                || page.url().contains("acompanhamento")) {
            System.out.println("✅ Sessão ainda válida — pulando login");
            selecionarEmpresaSeNecessario(page);
            return;
        }

        // Clica em "Entrar com Gov.br" se presente
        if (page.locator("text=Entrar com Gov.br").count() > 0) {
            page.locator("text=Entrar com Gov.br").click();
            page.waitForLoadState();
            page.waitForTimeout(2000 + (int)(Math.random() * 1000));
        }

        // Detecção de tela branca (bloqueio de proxy/datacenter pelo SERPRO)
        if (paginaBrancaOuBloqueada(page)) {
            System.out.println("⚠️ Tela branca detectada no SSO Gov.br — tentando recuperação...");
            tirarPrintDebug(page, "tela_branca_sso_" + System.currentTimeMillis() + ".png");

            boolean recuperou = false;
            for (int tentativa = 1; tentativa <= 3; tentativa++) {
                System.out.println("🔄 Reload tentativa " + tentativa + "/3...");
                page.reload(new Page.ReloadOptions().setTimeout(60000));
                page.waitForTimeout(4000 + (tentativa * 2000L));
                if (!paginaBrancaOuBloqueada(page)) {
                    System.out.println("✅ Página carregou após reload " + tentativa);
                    recuperou = true;
                    break;
                }
                tirarPrintDebug(page, "tela_branca_reload_" + tentativa + "_" + System.currentTimeMillis() + ".png");
            }

            if (!recuperou) {
                System.out.println("🔁 Tentando navegação direta ao SSO...");
                String urlSso = page.url();
                page.navigate("about:blank");
                page.waitForTimeout(2000);
                page.navigate(urlSso.isEmpty()
                                ? "https://sso.acesso.gov.br/login?client_id=comprasnet.gov.br"
                                : urlSso,
                        new Page.NavigateOptions().setTimeout(60000));
                page.waitForTimeout(5000);

                if (paginaBrancaOuBloqueada(page)) {
                    tirarPrintDebug(page, "tela_branca_irrecuperavel_" + System.currentTimeMillis() + ".png");
                    throw new RuntimeException(
                            "SSO Gov.br retornou tela branca mesmo após 3 reloads e navegação direta. "
                                    + "O IP pode estar bloqueado pelo SERPRO.");
                }
            }
        }

        // Aguarda e preenche campo CPF
        Locator campoCpf = page.locator("input[name='accountId'], #accountId");
        campoCpf.waitFor(new Locator.WaitForOptions()
                .setTimeout(20000)
                .setState(WaitForSelectorState.VISIBLE));

        // Movimento de mouse humanizado antes de interagir com o campo
        // O Gov.br analisa comportamento do mouse para detectar bots
        moverMouseHumanizado(page);
        page.waitForTimeout(800 + (int)(Math.random() * 1200));

        digitarComoHumano(page, campoCpf, CPF);

        // Pausa maior antes de clicar em Continuar — bots clicam imediatamente
        page.waitForTimeout(1500 + (int)(Math.random() * 2000));

        page.locator("#enter-account-id").click();

        // Pausa após clicar — simula tempo humano de reação
        page.waitForTimeout(1000 + (int)(Math.random() * 1000));

        // Detecta o que apareceu após submeter o CPF
        EstadoPosCpf estado = detectarEstadoPosCpf(page, 20000);

        switch (estado) {

            case CAPTCHA_VISIVEL -> {
                System.out.println("🔐 Captcha detectado após CPF — resolvendo via TwoCaptcha...");

                boolean resolveu = resolverCaptchaGrid(page);

                if (!resolveu) {
                    tirarPrintDebug(page, "captcha_nao_resolvido_" + System.currentTimeMillis() + ".png");
                    throw new RuntimeException("Falha ao resolver captcha. Abortando login.");
                }

                System.out.println("✅ Captcha resolvido. Aguardando campo de senha...");

                // Após resolver, aguarda campo de senha aparecer
                EstadoPosCpf estadoPosCaptcha = detectarEstadoPosCpf(page, 20000);
                if (estadoPosCaptcha != EstadoPosCpf.CAMPO_SENHA_VISIVEL) {
                    tirarPrintDebug(page, "pos_captcha_sem_senha_" + System.currentTimeMillis() + ".png");
                    throw new RuntimeException(
                            "Captcha resolvido mas campo de senha não apareceu. Estado: " + estadoPosCaptcha);
                }
                System.out.println("✅ Campo de senha disponível após resolver captcha");
            }

            case CAMPO_SENHA_VISIVEL -> {
                System.out.println("✅ Fluxo sem captcha");
            }

            case ERRO_DESCONHECIDO -> {
                tirarPrintDebug(page, "estado_desconhecido_pos_cpf_" + System.currentTimeMillis() + ".png");
                throw new RuntimeException(
                        "Estado desconhecido após submeter CPF. Screenshot salvo para diagnóstico.");
            }
        }

        // Preenche senha
        Locator campoSenha = page.locator("input[name='password'], #password");
        campoSenha.waitFor(new Locator.WaitForOptions()
                .setTimeout(15000)
                .setState(WaitForSelectorState.VISIBLE));

        digitarComoHumano(page, campoSenha, SENHA);
        page.waitForTimeout(600 + (int)(Math.random() * 600));

        page.locator("#submit-button").click();
        page.waitForTimeout(10000);

        context.storageState(new BrowserContext.StorageStateOptions()
                .setPath(Paths.get(SESSION_FILE)));
        System.out.println("✅ Sessão salva em " + SESSION_FILE);

        selecionarEmpresaSeNecessario(page);
    }

    // =========================
    // DETECÇÃO DE CAPTCHA
    // =========================

    /**
     * Detecta se qualquer tipo de captcha está visível na página:
     * - Grid de imagens do Gov.br (selecione carros, animais, etc.)
     * - hCaptcha (iframe)
     */
    private static boolean captchaVisivel(Page page) {
        try {
            // Grid de imagens — estrutura visual do Gov.br
            if (page.locator(".challenge-container, .rc-imageselect, [class*='captcha'], [id*='captcha']").count() > 0) {
                return true;
            }
            // hCaptcha iframe (fallback)
            if (page.locator("iframe[src*='hcaptcha.com']").count() > 0) {
                return true;
            }
            return false;
        } catch (Exception e) {
            System.out.println("Erro ao verificar captcha: " + e.getMessage());
            return false;
        }
    }

    // =========================
    // CAPTURA SITEKEY
    // =========================

    /**
     * Captura a sitekey do hCaptcha usando 4 estratégias em cascata.
     * Loga tudo para facilitar diagnóstico quando a sitekey muda.
     */
    private static String capturarSitekey(Page page) {
        String sitekey = "";

        // Estratégia 1: atributo src do iframe hcaptcha.com
        try {
            List<Locator> iframes = page.locator("iframe[src*=\'hcaptcha.com\']").all();
            System.out.println("\uD83D\uDD0D iframes hcaptcha encontrados: " + iframes.size());
            for (Locator iframe : iframes) {
                String src = iframe.getAttribute("src");
                System.out.println("   src: " + src);
                if (src != null && src.contains("sitekey=")) {
                    sitekey = src.split("sitekey=")[1].split("&")[0];
                    System.out.println("\uD83D\uDD11 Sitekey via src iframe: " + sitekey);
                    return sitekey;
                }
            }
        } catch (Exception e) {
            System.out.println("\u26A0\uFE0F Erro estrategia 1 (iframe src): " + e.getMessage());
        }

        // Estratégia 2: atributo data-sitekey em qualquer elemento
        try {
            Locator elem = page.locator("[data-sitekey]").first();
            if (elem.count() > 0) {
                sitekey = elem.getAttribute("data-sitekey");
                System.out.println("\uD83D\uDD11 Sitekey via data-sitekey: " + sitekey);
                return sitekey != null ? sitekey : "";
            }
        } catch (Exception e) {
            System.out.println("\u26A0\uFE0F Erro estrategia 2 (data-sitekey): " + e.getMessage());
        }

        // Estratégia 3: busca no HTML da página via regex JS
        try {
            String result = (String) page.evaluate(
                    "() => { const m = document.documentElement.innerHTML" +
                            ".match(/sitekey[=:\"\' ]+([0-9a-f\\-]{36})/i); return m ? m[1] : ''; }"
            );
            if (result != null && !result.isEmpty()) {
                sitekey = result;
                System.out.println("\uD83D\uDD11 Sitekey via HTML search: " + sitekey);
                return sitekey;
            }
        } catch (Exception e) {
            System.out.println("\u26A0\uFE0F Erro estrategia 3 (HTML search): " + e.getMessage());
        }

        // Estratégia 4: loga o HTML da área do captcha para diagnóstico manual
        try {
            String htmlCaptcha = (String) page.evaluate(
                    "() => { const el = document.querySelector('.h-captcha, [data-sitekey], #loginData');" +
                            "return el ? el.outerHTML.substring(0, 800) : 'elemento nao encontrado'; }"
            );
            System.out.println("\uD83D\uDCC4 HTML area captcha: " + htmlCaptcha);

            // Tenta extrair do outerHTML
            if (htmlCaptcha.contains("sitekey")) {
                String[] partes = htmlCaptcha.split("sitekey");
                if (partes.length > 1) {
                    String apos = partes[1].replaceAll("[^0-9a-f\\-]", " ").trim();
                    String[] tokens = apos.split(" ");
                    for (String t : tokens) {
                        if (t.length() == 36 && t.contains("-")) {
                            sitekey = t;
                            System.out.println("\uD83D\uDD11 Sitekey via outerHTML parse: " + sitekey);
                            return sitekey;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("\u26A0\uFE0F Erro estrategia 4 (outerHTML): " + e.getMessage());
        }

        System.out.println("\u274C Sitekey nao encontrada. Verifique os logs acima.");
        System.out.println("   Dica: abra DevTools > Network > filtre 'hcaptcha' > veja src do iframe.");
        return "";
    }

    // =========================
    // RESOLVER CAPTCHA GRID — TwoCaptcha API v2
    // =========================

    /**
     * Resolve o hCaptcha do Gov.br via 2captcha API v2 (HCaptchaTask).
     *
     * O captcha exibido é hCaptcha (imgs.hcaptcha.com).
     * O tipo correto na API v2 é HCaptchaTask: envia websiteURL + websiteKey,
     * recebe um token e injeta no campo oculto h-captcha-response.
     *
     * Fluxo:
     *  1. Captura a sitekey do iframe hCaptcha na página
     *  2. Envia HCaptchaTask para api.2captcha.com/createTask
     *  3. Polling getTaskResult até receber o token (max 120s)
     *  4. Injeta o token no campo h-captcha-response via JS
     *  5. Clica em Continuar para prosseguir
     */
    private static boolean resolverCaptchaGrid(Page page) {
        try {
            System.out.println("\uD83D\uDD10 Iniciando resolucao hCaptcha via TwoCaptcha API v2 (HCaptchaTask)...");

            String pageUrl = page.url();

            // 1. Captura a sitekey do hCaptcha — tenta múltiplas estratégias e loga tudo
            String sitekey = capturarSitekey(page);

            // Aborta se não encontrou a sitekey — não adianta tentar sem ela
            if (sitekey == null || sitekey.isEmpty()) {
                System.out.println("❌ Sitekey vazia — não é possível resolver o captcha.");
                tirarPrintDebug(page, "sitekey_vazia_" + System.currentTimeMillis() + ".png");
                return false;
            }

            // 2. Envia HCaptchaTask para o 2captcha (API v2)
            ObjectMapper om = new ObjectMapper();

            String corpoJson = om.writeValueAsString(om.createObjectNode()
                    .put("clientKey", TWOCAPTCHA_API_KEY)
                    .set("task", om.createObjectNode()
                            .put("type",       "HCaptchaTaskProxyless")
                            .put("websiteURL", pageUrl)
                            .put("websiteKey", sitekey)
                    )
            );

            System.out.println("\uD83D\uDCE4 Enviando HCaptchaTask...");
            String submitResponse = fazerPostHttp("https://api.2captcha.com/createTask", corpoJson);
            System.out.println("TwoCaptcha createTask: " + submitResponse);

            JsonNode submitJson = om.readTree(submitResponse);
            if (submitJson.path("errorId").asInt() != 0) {
                System.out.println("\u274C Erro ao criar task: " + submitResponse);
                return false;
            }

            String taskId = submitJson.path("taskId").asText();
            System.out.println("\uD83D\uDCE8 Task criada. ID: " + taskId);

            // 3. Polling getTaskResult (max 120s = 24 x 5s)
            String getResultJson = om.writeValueAsString(om.createObjectNode()
                    .put("clientKey", TWOCAPTCHA_API_KEY)
                    .put("taskId",    taskId)
            );

            String token = null;
            for (int tentativa = 0; tentativa < 24; tentativa++) {
                Thread.sleep(5000);

                String resResponse = fazerPostHttp("https://api.2captcha.com/getTaskResult", getResultJson);
                JsonNode resJson   = om.readTree(resResponse);

                System.out.println("TwoCaptcha polling [" + tentativa + "]: status=" + resJson.path("status").asText());

                if ("ready".equals(resJson.path("status").asText())) {
                    token = resJson.path("solution").path("gRecaptchaResponse").asText();
                    System.out.println("\u2705 Token hCaptcha obtido!");
                    break;
                }

                if (resJson.path("errorId").asInt() != 0) {
                    System.out.println("\u274C Erro no 2captcha: " + resResponse);
                    return false;
                }
            }

            if (token == null || token.isEmpty()) {
                System.out.println("\u274C Timeout ou token vazio aguardando 2captcha (120s)");
                return false;
            }

            // 4. Injeta o token via callback interno do hCaptcha
            // Setar el.value não é suficiente — o hCaptcha usa callback próprio
            // que habilita o botão e notifica o servidor
            final String tokenFinal = token;
            // ============================================================
            // INJEÇÃO DO TOKEN — hCaptcha invisible mode
            // O Gov.br usa hCaptcha invisible: o site registra um callback
            // via data-callback no elemento .h-captcha, e o hCaptcha chama
            // esse callback passando o token quando resolve.
            // Precisamos: 1) setar o textarea, 2) chamar o callback do site.
            // ============================================================
            //final String tokenFinal = token;

            // Passo A: descobre o nome do callback registrado pelo Gov.br
            String callbackName = (String) page.evaluate("""
                () => {
                    // Tenta achar o data-callback no elemento hcaptcha
                    var el = document.querySelector('.h-captcha[data-callback]');
                    if (el) return el.getAttribute('data-callback');

                    // Tenta achar no elemento com data-sitekey
                    el = document.querySelector('[data-sitekey][data-callback]');
                    if (el) return el.getAttribute('data-callback');

                    // Procura no HTML por callback registrado
                    var m = document.documentElement.innerHTML.match(/data-callback=["\'](\\w+)["\'']/);
                    return m ? m[1] : '';
                }
            """);
            System.out.println("\uD83D\uDD0D Callback registrado pelo Gov.br: " + callbackName);

            // Passo B: seta o textarea E chama o callback do site com o token
            page.evaluate("""
                (args) => {
                    var token        = args[0];
                    var callbackName = args[1];

                    // 1. Seta todos os campos textarea do hCaptcha
                    document.querySelectorAll('textarea[name="h-captcha-response"]').forEach(function(el) {
                        el.value = token;
                        el.dispatchEvent(new Event('input',  {bubbles: true}));
                        el.dispatchEvent(new Event('change', {bubbles: true}));
                    });
                    document.querySelectorAll('textarea[name="g-recaptcha-response"]').forEach(function(el) {
                        el.value = token;
                        el.dispatchEvent(new Event('input',  {bubbles: true}));
                        el.dispatchEvent(new Event('change', {bubbles: true}));
                    });

                    // 2. Chama o callback do site (registrado pelo Gov.br)
                    if (callbackName && typeof window[callbackName] === 'function') {
                        console.log('Chamando callback: ' + callbackName);
                        window[callbackName](token);
                    }

                    // 3. Tenta outros callbacks conhecidos do Gov.br
                    var knownCbs = ['onCaptchaSuccess', 'captchaCallback', 'hcaptchaCallback',
                                    'onSuccess', 'verifyCallback', 'captchaVerify'];
                    knownCbs.forEach(function(cb) {
                        if (typeof window[cb] === 'function') {
                            console.log('Chamando callback alternativo: ' + cb);
                            try { window[cb](token); } catch(e) {}
                        }
                    });

                    // 4. Tenta via hcaptcha API interna
                    try {
                        if (window.hcaptcha) {
                            var ids = Object.keys(window.__hCaptchaWidgets__ || {});
                            if (ids.length === 0) ids = ['0'];
                            ids.forEach(function(id) {
                                try { window.hcaptcha.setResponse(id, token); } catch(e) {}
                            });
                        }
                    } catch(e) {}
                }
            """, new Object[]{tokenFinal, callbackName != null ? callbackName : ""});

            System.out.println("\uD83D\uDC89 Token injetado e callbacks disparados");

            System.out.println("\uD83D\uDC89 Token injetado via callback hCaptcha");
            page.waitForTimeout(2000);

            // 5. Aguarda o botão Continuar ficar habilitado (o hCaptcha o desabilita até validar)
            //    Tenta por até 10s antes de desistir
            Locator btnContinuar = page.locator("#enter-account-id");
            boolean botaoHabilitado = false;
            for (int t = 0; t < 10; t++) {
                try {
                    if (btnContinuar.count() > 0 && btnContinuar.isEnabled()) {
                        botaoHabilitado = true;
                        break;
                    }
                } catch (Exception ignored) {}
                page.waitForTimeout(1000);
                System.out.println("\uD83D\uDD04 Aguardando botao Continuar habilitar... " + (t + 1) + "s");
            }

            if (botaoHabilitado) {
                btnContinuar.click();
                System.out.println("\u2705 Clicou em Continuar apos captcha");
                page.waitForTimeout(4000);
            } else {
                // Botão ainda desabilitado — tenta submit direto do formulário via JS
                System.out.println("\u26A0\uFE0F Botao ainda desabilitado, tentando submit via JS...");
                page.evaluate("""
                    () => {
                        var f = document.getElementById('loginData')
                                || document.querySelector('form');
                        if (f) f.submit();
                    }
                """);
                page.waitForTimeout(5000);
            }

            return true;

        } catch (Exception e) {
            System.out.println("\u274C Erro ao resolver hCaptcha: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // =========================
    // CAPTCHA DO PORTAL COMPRASGOV
    // Grid de imagens que aparece ao abrir mensagens
    // =========================

    /**
     * Resolve o captcha do portal ComprasGov via HCaptchaTaskProxyless.
     * Mesmo mecanismo do login — usa token, não grid de imagens.
     * A sitekey é a mesma do Gov.br: 93b08d40-d46c-400a-ba07-6f91cda815b9
     */
    private static boolean resolverCaptchaPortal(Page page) {
        try {
            System.out.println("\uD83D\uDD10 Resolvendo captcha portal via HCaptchaTaskProxyless...");
            page.waitForTimeout(2000);

            // Captura a sitekey — mesma do Gov.br
            String sitekey = capturarSitekey(page);
            if (sitekey == null || sitekey.isEmpty()) {
                // Sitekey conhecida do portal ComprasGov/Gov.br
                sitekey = "93b08d40-d46c-400a-ba07-6f91cda815b9";
                System.out.println("\u26A0\uFE0F Usando sitekey hardcoded do portal: " + sitekey);
            }

            String pageUrl = page.url();
            System.out.println("\uD83D\uDD11 Sitekey: " + sitekey + " | URL: " + pageUrl);

            // Envia HCaptchaTaskProxyless
            ObjectMapper om = new ObjectMapper();
            String corpoJson = om.writeValueAsString(om.createObjectNode()
                    .put("clientKey", TWOCAPTCHA_API_KEY)
                    .set("task", om.createObjectNode()
                            .put("type",       "HCaptchaTaskProxyless")
                            .put("websiteURL", pageUrl)
                            .put("websiteKey", sitekey)
                    )
            );

            String submitResponse = fazerPostHttp("https://api.2captcha.com/createTask", corpoJson);
            System.out.println("TwoCaptcha createTask portal: " + submitResponse);

            JsonNode submitJson = om.readTree(submitResponse);
            if (submitJson.path("errorId").asInt() != 0) {
                System.out.println("\u274C Erro ao criar task portal: " + submitResponse);
                clicarPularCaptcha(page);
                return true;
            }

            String taskId = submitJson.path("taskId").asText();
            System.out.println("\uD83D\uDCE8 Task portal criada. ID: " + taskId);

            // Polling
            String getResultJson = om.writeValueAsString(om.createObjectNode()
                    .put("clientKey", TWOCAPTCHA_API_KEY)
                    .put("taskId",    taskId)
            );

            String token = null;
            for (int tentativa = 0; tentativa < 24; tentativa++) {
                Thread.sleep(5000);
                String resResponse = fazerPostHttp("https://api.2captcha.com/getTaskResult", getResultJson);
                JsonNode resJson   = om.readTree(resResponse);
                System.out.println("Portal polling [" + tentativa + "]: " + resJson.path("status").asText());

                if ("ready".equals(resJson.path("status").asText())) {
                    token = resJson.path("solution").path("gRecaptchaResponse").asText();
                    System.out.println("\u2705 Token portal obtido!");
                    break;
                }
                if (resJson.path("errorId").asInt() != 0) {
                    System.out.println("\u274C Erro 2captcha portal: " + resResponse);
                    clicarPularCaptcha(page);
                    return true;
                }
            }

            if (token == null || token.isEmpty()) {
                System.out.println("\u274C Timeout token portal — clicando Pular");
                clicarPularCaptcha(page);
                return true;
            }

            // Injeta o token e chama callback
            final String tokenFinal = token;

            // Descobre o callback registrado pelo portal
            String callbackName = (String) page.evaluate("""
                () => {
                    var el = document.querySelector('.h-captcha[data-callback]');
                    if (el) return el.getAttribute('data-callback');
                    el = document.querySelector('[data-sitekey][data-callback]');
                    if (el) return el.getAttribute('data-callback');
                    var m = document.documentElement.innerHTML.match(/data-callback=["\'](\\w+)["\'']/);
                    return m ? m[1] : '';
                }
            """);
            System.out.println("\uD83D\uDD0D Callback portal: " + callbackName);

            page.evaluate("""
                (args) => {
                    var token        = args[0];
                    var callbackName = args[1];

                    document.querySelectorAll('textarea[name="h-captcha-response"]').forEach(function(el) {
                        el.value = token;
                        el.dispatchEvent(new Event('input',  {bubbles: true}));
                        el.dispatchEvent(new Event('change', {bubbles: true}));
                    });
                    document.querySelectorAll('textarea[name="g-recaptcha-response"]').forEach(function(el) {
                        el.value = token;
                        el.dispatchEvent(new Event('input',  {bubbles: true}));
                        el.dispatchEvent(new Event('change', {bubbles: true}));
                    });

                    if (callbackName && typeof window[callbackName] === 'function') {
                        window[callbackName](token);
                    }

                    var knownCbs = ['onCaptchaSuccess', 'captchaCallback', 'hcaptchaCallback', 'onSuccess'];
                    knownCbs.forEach(function(cb) {
                        if (typeof window[cb] === 'function') {
                            try { window[cb](token); } catch(e) {}
                        }
                    });

                    try {
                        if (window.hcaptcha) {
                            var ids = Object.keys(window.__hCaptchaWidgets__ || {});
                            if (ids.length === 0) ids = ['0'];
                            ids.forEach(function(id) {
                                try { window.hcaptcha.setResponse(id, token); } catch(e) {}
                            });
                        }
                    } catch(e) {}
                }
            """, new Object[]{tokenFinal, callbackName != null ? callbackName : ""});

            System.out.println("\uD83D\uDC89 Token portal injetado");
            page.waitForTimeout(3000);
            return true;

        } catch (Exception e) {
            System.out.println("\u274C Erro ao resolver captcha portal: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Clica nas células do grid do portal conforme índices do 2captcha.
     * Tenta clicar tanto na página principal quanto dentro dos iframes do hCaptcha.
     */
    private static void clicarCelulasCaptchaPortal(Page page, JsonNode clickArray) {
        try {
            // Tenta dentro dos frames do hCaptcha
            for (Frame frame : page.frames()) {
                if (frame.url().contains("hcaptcha") && frame.url().contains("challenge")) {
                    List<Locator> celulas = frame.locator(
                            ".task-image, .image-wrapper, td, li, [class*='tile'], [class*='image']"
                    ).all();

                    System.out.println("\uD83D\uDD32 Celulas no iframe: " + celulas.size());

                    if (celulas.size() >= 3) {
                        for (JsonNode indiceNode : clickArray) {
                            int pos = indiceNode.asInt() - 1;
                            if (pos >= 0 && pos < celulas.size()) {
                                celulas.get(pos).click();
                                System.out.println("\u2705 Clicou celula " + (pos + 1));
                                frame.waitForTimeout(400 + (int)(Math.random() * 300));
                            }
                        }
                        return;
                    }
                }
            }
            System.out.println("\u26A0\uFE0F Nenhuma celula encontrada nos iframes");
        } catch (Exception e) {
            System.out.println("Erro ao clicar celulas portal: " + e.getMessage());
        }
    }

    /**
     * Clica no botão "Pular" do captcha do portal quando não há correspondência
     * ou quando o 2captcha não consegue resolver.
     */
    private static void clicarPularCaptcha(Page page) {
        try {
            // Tenta na página principal
            Locator btnPular = page.locator("button:has-text('Pular'), button:has-text('Skip')");
            if (btnPular.count() > 0) {
                btnPular.first().click();
                System.out.println("\u23ED\uFE0F Clicou em Pular");
                page.waitForTimeout(2000);
                return;
            }
            // Tenta dentro dos frames
            for (Frame frame : page.frames()) {
                if (frame.url().contains("hcaptcha")) {
                    try {
                        Locator btn = frame.locator("button:has-text('Skip'), .skip-btn, [class*='skip']").first();
                        if (btn.count() > 0) {
                            btn.click();
                            System.out.println("\u23ED\uFE0F Clicou Pular no iframe");
                            page.waitForTimeout(2000);
                            return;
                        }
                    } catch (Exception ignored) {}
                }
            }
            System.out.println("\u26A0\uFE0F Botao Pular nao encontrado");
        } catch (Exception e) {
            System.out.println("Erro ao clicar Pular: " + e.getMessage());
        }
    }

    // =========================
    // HTTP HELPERS
    // =========================

    /**
     * GET simples — usado para endpoints legados se necessário.
     */
    private static String fazerGetHttp(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        byte[] bytes = conn.getInputStream().readAllBytes();
        conn.disconnect();
        return new String(bytes, "UTF-8");
    }

    /**
     * POST JSON — usado pela API v2 do 2captcha (createTask / getTaskResult).
     */
    private static String fazerPostHttp(String urlStr, String jsonBody) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);
        conn.setDoOutput(true);
        conn.getOutputStream().write(jsonBody.getBytes("UTF-8"));
        byte[] bytes = conn.getInputStream().readAllBytes();
        conn.disconnect();
        return new String(bytes, "UTF-8");
    }

    // =========================
    // TELA BRANCA / BLOQUEIO
    // =========================
    private static boolean paginaBrancaOuBloqueada(Page page) {
        try {
            page.waitForTimeout(2000);

            String bodyText = (String) page.evaluate(
                    "() => document.body ? document.body.innerText.trim() : ''");
            if (bodyText != null && bodyText.length() > 30) {
                return false;
            }

            String[] seletoresEsperados = {
                    "input[name='accountId']", "#accountId",
                    "button", "form", "h1, h2", ".login-box", "#loginData"
            };
            for (String sel : seletoresEsperados) {
                if (page.locator(sel).count() > 0) {
                    return false;
                }
            }

            String title = page.title();
            System.out.println("📄 Título: '" + title + "' | Body chars: "
                    + (bodyText != null ? bodyText.length() : 0));
            return true;

        } catch (Exception e) {
            System.out.println("Erro ao verificar tela branca: " + e.getMessage());
            return false;
        }
    }

    // =========================
    // SELECIONAR EMPRESA
    // =========================
    private static void selecionarEmpresaSeNecessario(Page page) {
        try {
            if (page.locator("text=MEDIPHACOS INDUSTRIAS MEDICAS S/A").count() > 0) {
                page.locator("text=MEDIPHACOS INDUSTRIAS MEDICAS S/A").first().click();
                page.locator("input[type='submit'], button:has-text('Confirmar')").click();
                page.waitForTimeout(8000);
                System.out.println("✅ Empresa selecionada");
            }
        } catch (Exception e) {
            System.out.println("⚠️ Erro ao selecionar empresa (não crítico): " + e.getMessage());
        }
    }

    // =========================
    // COMPORTAMENTO HUMANO — MOUSE E DIGITAÇÃO
    // =========================

    /**
     * Move o mouse em trajetória não-linear antes de interagir com a página.
     * O Gov.br e o hCaptcha analisam eventos de mouse para detectar automação.
     * Bots geralmente não movem o mouse antes de clicar — humanos sempre movem.
     */
    private static void moverMouseHumanizado(Page page) {
        try {
            // Dimensões da viewport
            int largura = 1366;
            int altura  = 768;

            // Posição inicial aleatória (simula onde o mouse estava antes)
            int x = 100 + (int)(Math.random() * 400);
            int y = 100 + (int)(Math.random() * 300);

            // Faz 4 a 7 movimentos com curvas irregulares
            int movimentos = 4 + (int)(Math.random() * 4);
            for (int i = 0; i < movimentos; i++) {
                // Destino levemente aleatório — não em linha reta
                x += (int)(Math.random() * 200) - 100;
                y += (int)(Math.random() * 150) - 50;

                // Mantém dentro da viewport
                x = Math.max(50, Math.min(x, largura - 50));
                y = Math.max(50, Math.min(y, altura - 50));

                page.mouse().move(x, y);

                // Delay irregular entre movimentos (humanos não movem na mesma velocidade)
                page.waitForTimeout(80 + (int)(Math.random() * 200));
            }

            System.out.println("🖱️ Movimento de mouse humanizado concluído");
        } catch (Exception e) {
            System.out.println("⚠️ Erro no movimento de mouse (não crítico): " + e.getMessage());
        }
    }

    private static void digitarComoHumano(Page page, Locator campo, String texto) throws Exception {
        campo.click();
        page.waitForTimeout(300 + (int)(Math.random() * 300));
        campo.click(new Locator.ClickOptions().setClickCount(3));
        campo.press("Control+a");
        campo.press("Backspace");
        page.waitForTimeout(200);
        for (char c : texto.toCharArray()) {
            campo.pressSequentially(
                    String.valueOf(c),
                    new Locator.PressSequentiallyOptions()
                            .setDelay(80 + (int)(Math.random() * 120))
            );
        }
    }

    // =========================
    // PAGINAÇÃO CHAT
    // =========================
    private static void capturarMensagensPaginadasComInteracao(
            Oportunidade oportunidade,
            Page page,
            ObjectMapper mapper
    ) {
        try {
            if (ULTIMO_JSON_CHAT != null && !ULTIMO_JSON_CHAT.isEmpty()) {
                processarMensagens(
                        oportunidade.getCliente(),
                        oportunidade.getUasg(),
                        oportunidade.getNumeroPregao(),
                        ULTIMO_JSON_CHAT,
                        mapper,
                        oportunidade
                );
            }

            int totalPages = 15;

            for (int i = 2; i <= totalPages; i++) {

                System.out.println("Valor mensagemExiste: " + mensagemExiste);
                System.out.println("Pagina: " + i + " oportunidade: " + oportunidade.getId());

                if (mensagemExiste) {
                    System.out.println("Mensagem já existente, saindo da paginação. Oportunidade: " + oportunidade.getId());
                    mensagemExiste = false;
                    break;
                }

                try {
                    Locator pageButton = page.locator(
                            "button[data-pc-section='page'][aria-label='Página " + i + "']");

                    if (pageButton.count() == 0) {
                        System.out.println("⚠️ PAGINA NÃO ENCONTRADA: " + i);
                        continue;
                    }

                    System.out.println("\n🔄 INDO PARA PAGINA " + i);
                    ULTIMO_JSON_CHAT = "";
                    pageButton.first().click();
                    page.waitForTimeout(8000);

                    boolean captcha = page.locator(
                            "text=Não foi possível realizar a validação do Captcha").count() > 0;
                    boolean captchaGrid = captchaVisivel(page);

                    if (captcha || captchaGrid) {
                        System.out.println("⚠️ CAPTCHA DETECTADO NA PAGINAÇÃO — tentando resolver...");
                        boolean resolveu = resolverCaptchaPortal(page);
                        if (!resolveu) {
                            System.out.println("❌ Nao foi possivel resolver captcha na paginacao — encerrando");
                            break;
                        }
                        // Aguarda recarregar após resolver o captcha
                        page.waitForTimeout(5000);
                        // Re-clica na página atual após resolver
                        try {
                            pageButton.first().click();
                            page.waitForTimeout(5000);
                        } catch (Exception ignored) {}
                    }

                    if (ULTIMO_JSON_CHAT != null && !ULTIMO_JSON_CHAT.isEmpty()) {
                        processarMensagens(
                                oportunidade.getCliente(),
                                oportunidade.getUasg(),
                                oportunidade.getNumeroPregao(),
                                ULTIMO_JSON_CHAT,
                                mapper,
                                oportunidade
                        );
                    } else {
                        System.out.println("⚠️ JSON NÃO RECEBIDO — PAGINA " + i);
                    }

                } catch (Exception e) {
                    System.out.println("❌ ERRO PAGINA " + i);
                    e.printStackTrace();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =========================
    // PROCESSAR JSON CHAT
    // =========================
    private static void processarMensagens(
            String cliente,
            String uasg,
            String pregao,
            String body,
            ObjectMapper mapper,
            Oportunidade oportunidade
    ) {
        try {
            JsonNode root  = mapper.readTree(body);
            JsonNode lista = root.isArray() ? root : root.path("content");

            if (lista == null || !lista.isArray()) {
                System.out.println("⚠️ LISTA DE MENSAGENS VAZIA");
                return;
            }

            System.out.println("📦 TOTAL MSGS: " + lista.size());

            for (JsonNode msg : lista) {

                String id = getText(msg, "id", "uuid", "chaveMensagemNaOrigem");
                if (id == null || id.isEmpty()) continue;

                String  texto     = getText(msg, "texto", "mensagem", "conteudo");
                String  remetente = getText(msg, "remetente", "tipoRemetente");
                String  data      = getText(msg, "dataHora", "dataEnvio");
                Integer item      = null;

                JsonNode itemNode = msg.path("numeroItem");
                if (!itemNode.isMissingNode() && !itemNode.isNull()) {
                    item = itemNode.asInt();
                }

                boolean inserido = salvarPregaoChat(id, texto, remetente, data, item, oportunidade);

                if (!inserido) {
                    System.out.println("Mensagem já existente, ignorando. Oportunidade: " + oportunidade.getId());
                    break;
                } else if (countNovaMensagem == 1) {
                    dispararWhatsAppNovaMsg(oportunidade);
                    System.out.println("Nova mensagem detectada: " + texto + " — Oportunidade: " + oportunidade.getId());
                }

                if (textoMencionaCliente(texto, oportunidade)) {
                    dispararWhatsApp(oportunidade);
                }
            }

        } catch (Exception e) {
            System.out.println("⚠️ ERRO PROCESSANDO JSON");
            e.printStackTrace();
        }
    }

    // =========================
    // SALVAR H2 (legado)
    // =========================
    private static void salvar(String cliente, String uasg, String pregao,
                               String id, String texto, String remetente,
                               String data, Integer item) {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:./chat-db", "sa", "")) {
            PreparedStatement ps = conn.prepareStatement(
                    "MERGE INTO CHAT_MENSAGEM (ID, CLIENTE, UASG, PREGAO, TEXTO, REMETENTE, DATA, ITEM) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
            ps.setString(1, id);   ps.setString(2, cliente); ps.setString(3, uasg);
            ps.setString(4, pregao); ps.setString(5, texto); ps.setString(6, remetente);
            ps.setString(7, data);  ps.setObject(8, item);
            ps.execute();
            System.out.println("💾 SALVO H2: " + texto);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =========================
    // CRIAR TABELA H2
    // =========================
    private static void criarTabela() throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:h2:./chat-db", "sa", "");
        conn.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS CHAT_MENSAGEM ("
                        + "ID VARCHAR PRIMARY KEY, CLIENTE VARCHAR, UASG VARCHAR, "
                        + "PREGAO VARCHAR, TEXTO CLOB, REMETENTE VARCHAR, DATA VARCHAR, ITEM INT)");
        conn.close();
    }

    // =========================
    // JSON SAFE
    // =========================
    private static String getText(JsonNode node, String... campos) {
        for (String c : campos) {
            JsonNode n = node.get(c);
            if (n != null && !n.isNull()) return n.asText();
        }
        return "";
    }

    // =========================
    // BUSCAR OPORTUNIDADES
    // =========================
    private static List<Oportunidade> buscarOportunidadesDoBanco() {

        List<Oportunidade> lista = new ArrayList<>();

        String sql =
                "SELECT O.ID, O.CLIENTE_ID, CLI.CNPJ, CLI.RAZAO_SOCIAL, CLI.FANTASIA, " +
                        "       O.MODALIDADE_ID, O.NUMERO_EDITAL, O.UASG_OC, O.DATA_CERTAME, " +
                        "       O.STATUS, CLI.FANTASIA AS CLIENTE, O.UASG_OC AS UASG, " +
                        "       O.NUMERO_EDITAL AS NUMERO_PREGAO " +
                        "FROM vista.VISTA_OPORTUNIDADE O " +
                        "JOIN vista.VISTA_CLIENTE CLI ON CLI.ID = O.CLIENTE_ID " +
                        "WHERE 1 = 1 " +
                        "  AND O.UASG_OC IS NOT NULL " +
                        "  AND O.ID IN (SELECT TEXTO FROM vista.VISTA_PARAMETRO_SISTEMA " +
                        "               WHERE PARAMETRO = 'CHAT_COMPRAS_GOV') " +
                        "ORDER BY O.ID DESC";

        Connection        conn = null;
        PreparedStatement ps   = null;
        ResultSet         rs   = null;

        try {
            conn = getConexaoMySQL();
            ps   = conn.prepareStatement(sql);
            rs   = ps.executeQuery();

            while (rs.next()) {
                Long   id           = rs.getLong("ID");
                Long   clienteId    = rs.getLong("CLIENTE_ID");
                String cnpj         = rs.getString("CNPJ");
                String razaoSocial  = rs.getString("RAZAO_SOCIAL");
                String fantasia     = rs.getString("FANTASIA");
                Long   modalidade   = rs.getLong("MODALIDADE_ID");
                String numeroEdital = rs.getString("NUMERO_EDITAL");
                String uasgOc       = rs.getString("UASG_OC");
                String status       = rs.getString("STATUS");

                LocalDate dataCertame = null;
                java.sql.Date sqlDate = rs.getDate("DATA_CERTAME");
                if (sqlDate != null) dataCertame = sqlDate.toLocalDate();

                String cliente      = (fantasia != null && !fantasia.trim().isEmpty())
                        ? fantasia.trim() : razaoSocial;
                String numeroPregao = formatarNumeroPregao(numeroEdital);

                lista.add(new Oportunidade(
                        id, clienteId, cnpj, razaoSocial, fantasia,
                        modalidade, numeroEdital, uasgOc, dataCertame,
                        status, cliente, uasgOc, numeroPregao
                ));
            }

            System.out.println("Oportunidades carregadas do banco: " + lista.size());

        } catch (Exception e) {
            System.out.println("Erro ao buscar oportunidades: " + e.getMessage());
            e.printStackTrace();
        } finally {
            fechar(rs, ps, conn);
        }

        return lista;
    }

    // =========================
    // FORMATAR PREGÃO
    // =========================
    private static String formatarNumeroPregao(String numeroEdital) {
        if (numeroEdital == null || numeroEdital.trim().isEmpty()) return "";
        String s = numeroEdital.trim();
        if (s.contains("/")) return s;
        String apenasNumeros = s.replaceAll("[^0-9]", "");
        if (apenasNumeros.length() <= 4) return s;
        String ano    = apenasNumeros.substring(apenasNumeros.length() - 4);
        String numero = apenasNumeros.substring(0, apenasNumeros.length() - 4);
        return numero + "/" + ano;
    }

    // =========================
    // FECHAR RECURSOS JDBC
    // =========================
    private static void fechar(ResultSet rs, PreparedStatement ps, Connection conn) {
        if (rs   != null) { try { rs.close();   } catch (Exception ignored) {} }
        if (ps   != null) { try { ps.close();   } catch (Exception ignored) {} }
        if (conn != null) { try { conn.close(); } catch (Exception ignored) {} }
    }

    // =========================
    // CONEXÃO MYSQL
    // =========================
    private static Connection getConexaoMySQL() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    // =========================
    // CARREGAR CONFIG BANCO
    // =========================
    private static void carregarConfigBanco() throws Exception {
        Properties props = carregarProperties();
        DB_URL      = props.getProperty("spring.datasource.url");
        DB_USER     = props.getProperty("spring.datasource.username");
        DB_PASSWORD = props.getProperty("spring.datasource.password");
        if (DB_URL == null || DB_URL.trim().isEmpty()) {
            throw new RuntimeException("Propriedade spring.datasource.url nao configurada.");
        }
        System.out.println("Banco configurado: " + DB_URL);
    }

    // =========================
    // CARREGAR PROPERTIES
    // =========================
    private static Properties carregarProperties() throws Exception {
        Properties props = new Properties();
        InputStream is = RPAChatComprasNetFullTesteV5OldNew.class
                .getClassLoader()
                .getResourceAsStream("application.properties");
        if (is == null) {
            File f = new File("application.properties");
            if (f.exists()) is = new FileInputStream(f);
        }
        if (is != null) {
            props.load(is);
            is.close();
        } else {
            throw new RuntimeException("application.properties nao encontrado.");
        }
        return props;
    }

    // =========================
    // SALVAR PREGAO CHAT (MySQL)
    // =========================
    private static boolean salvarPregaoChat(
            String chaveMensagem,
            String texto,
            String remetente,
            String dataStr,
            Integer itemLote,
            Oportunidade oportunidade
    ) {
        Connection        conn     = null;
        PreparedStatement psCheck  = null;
        PreparedStatement psInsert = null;
        ResultSet         rs       = null;

        try {
            conn = getConexaoMySQL();

            psCheck = conn.prepareStatement(
                    "SELECT ID FROM vista.VISTA_PREGAO_CHAT "
                            + "WHERE MENSAGEM = ? AND OPORTUNIDADE_ID = ? AND DATA_CHAT_PREGOEIRO = ?");

            psCheck.setString(1, truncar(montarMensagemChat(remetente, texto), 2000));
            psCheck.setLong(2, oportunidade.getId());
            psCheck.setString(3, truncar(dataStr, 100));

            rs = psCheck.executeQuery();
            System.out.println("Verificando oportunidade ID: " + oportunidade.getId());

            if (rs.next()) {
                mensagemExiste = true;
                return false;
            }

            rs.close();
            psCheck.close();

            LocalDateTime dataChat = parsearDataChat(dataStr);

            psInsert = conn.prepareStatement(
                    "INSERT INTO vista.VISTA_PREGAO_CHAT "
                            + "(DATA_CHAT_PREGOEIRO, DATA_CHAT, MENSAGEM, OPORTUNIDADE_ID, ITEM_LOTE, DATA_CADASTRO) "
                            + "VALUES (?, ?, ?, ?, ?, NOW())");

            psInsert.setString(1, truncar(dataStr, 100));
            if (dataChat != null) {
                psInsert.setTimestamp(2, Timestamp.valueOf(dataChat));
            } else {
                psInsert.setNull(2, Types.TIMESTAMP);
            }
            psInsert.setString(3, truncar(montarMensagemChat(remetente, texto), 2000));
            psInsert.setLong(4, oportunidade.getId());
            if (itemLote != null) {
                psInsert.setLong(5, itemLote.longValue());
            } else {
                psInsert.setNull(5, Types.BIGINT);
            }
            psInsert.executeUpdate();

            System.out.println("SALVO: [" + remetente + "] "
                    + (texto != null ? texto.substring(0, Math.min(60, texto.length())) : ""));

            countNovaMensagem += 1;
            return true;

        } catch (Exception e) {
            System.out.println("Erro ao salvar VISTA_PREGAO_CHAT: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            fechar(rs, psCheck, null);
            fechar(null, psInsert, conn);
        }
    }

    // =========================
    // UTILITÁRIOS
    // =========================
    private static String truncar(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String montarMensagemChat(String remetente, String texto) {
        String r = (remetente != null && !remetente.isEmpty()) ? remetente : "N/A";
        String t = (texto     != null && !texto.isEmpty())     ? texto     : "";
        return "[" + r + "] " + t;
    }

    private static LocalDateTime parsearDataChat(String dataStr) {
        if (dataStr == null || dataStr.trim().isEmpty()) return null;
        String[] formatos = {
                "dd/MM/yyyy HH:mm", "dd/MM/yyyy 'as' HH:mm", "dd/MM/yyyy",
                "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd HH:mm:ss"
        };
        String s = dataStr.trim()
                .replace(" às ", " ").replace(" as ", " ")
                .replaceAll("([+-]\\d{2}:\\d{2})$", "");
        for (String fmt : formatos) {
            try { return LocalDateTime.parse(s, DateTimeFormatter.ofPattern(fmt)); }
            catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    // =========================
    // DEBUG SCREENSHOT
    // =========================
    private static void tirarPrintDebug(Page page, String nomeArquivo) {
        try {
            if (page == null) {
                System.out.println("⚠️ Page null — não foi possível tirar screenshot: " + nomeArquivo);
                return;
            }
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(Paths.get(nomeArquivo)).setFullPage(true));
            System.out.println("📸 Screenshot salvo: " + nomeArquivo);
        } catch (Exception e) {
            System.out.println("Não foi possível salvar screenshot: " + e.getMessage());
        }
    }

    // =========================
    // WHATSAPP
    // =========================
    private static void dispararWhatsAppNovaMsg(Oportunidade oportunidade) {
        String mensagem = "AVISO: Há novas mensagens no chat do edital "
                + oportunidade.getNumeroEdital() + " do cliente "
                + oportunidade.getCliente() + ". Favor verificar!";
        System.out.println("DISPARO WHATSAPP NOVA MENSAGEM: " + mensagem);
        try {
            WhatsAppNotificationService.sendGroupMessage(WHATS_NUMERO, WHATS_GRUPO, mensagem);
        } catch (Exception e) {
            System.out.println("Erro ao enviar WhatsApp de nova mensagem: " + e.getMessage());
        }
    }

    private static void dispararWhatsApp(Oportunidade oportunidade) {
        String mensagem = "URGENTE: Houve alteracoes no chat do edital "
                + oportunidade.getNumeroEdital() + " do cliente "
                + oportunidade.getCliente() + ". Favor verificar!";
        System.out.println("DISPARO WHATSAPP: " + mensagem);
        try {
            WhatsAppNotificationService.sendGroupMessage(WHATS_NUMERO, WHATS_GRUPO, mensagem);
        } catch (Exception e) {
            System.out.println("Erro ao enviar WhatsApp: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // =========================
    // MENCIONA CLIENTE
    // =========================
    private static boolean textoMencionaCliente(String texto, Oportunidade oportunidade) {
        if (texto == null || texto.trim().isEmpty()) return false;
        String textoUpper = texto.toUpperCase().trim();

        String fantasia = oportunidade.getFantasia();
        if (fantasia != null && !fantasia.trim().isEmpty())
            if (textoUpper.contains(fantasia.toUpperCase().trim())) return true;

        String razao = oportunidade.getRazaoSocial();
        if (razao != null && !razao.trim().isEmpty()) {
            String razaoUpper = razao.toUpperCase().trim();
            if (textoUpper.contains(razaoUpper)) return true;
            String[] palavras = razaoUpper.split("\\s+");
            if (palavras.length > 0 && palavras[0].length() >= 4)
                if (textoUpper.contains(palavras[0])) return true;
        }

        String cliente = oportunidade.getCliente();
        if (cliente != null && !cliente.trim().isEmpty() && cliente.length() >= 4)
            if (textoUpper.contains(cliente.toUpperCase().trim())) return true;

        String cnpj = oportunidade.getCnpj();
        if (cnpj != null && !cnpj.trim().isEmpty()) {
            String cnpjNumeros = cnpj.replaceAll("[^0-9]", "");
            if (!cnpjNumeros.isEmpty() && textoUpper.contains(cnpjNumeros)) return true;
            if (cnpjNumeros.length() == 14) {
                String cnpjMascarado =
                        cnpjNumeros.substring(0, 2) + "." + cnpjNumeros.substring(2, 5) + "."
                                + cnpjNumeros.substring(5, 8) + "/" + cnpjNumeros.substring(8, 12)
                                + "-" + cnpjNumeros.substring(12);
                if (textoUpper.contains(cnpjMascarado)) return true;
            }
        }
        return false;
    }

    // =========================
    // DELETAR SESSÃO
    // =========================
    private static void deletarSessao() {
        try {
            java.io.File sessao = new java.io.File(SESSION_FILE);
            if (sessao.exists()) {
                boolean deletado = sessao.delete();
                System.out.println(deletado
                        ? "Arquivo de sessão deletado: " + SESSION_FILE
                        : "Não foi possível deletar: " + SESSION_FILE);
            }
        } catch (Exception e) {
            System.out.println("Erro ao deletar sessão: " + e.getMessage());
        }
    }

    // =========================
    // NAVEGAÇÃO RESILIENTE
    // =========================
    private static void navegarComResiliencia(Page page, String url) throws Exception {
        System.out.println("🌐 Navegando (resiliente) para: " + url);
        try {
            page.navigate(url, new Page.NavigateOptions()
                    .setTimeout(90000)
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
            aguardarNetworkIdleComTolerancia(page, 30000);
            page.waitForTimeout(12000);
            System.out.println("✅ Navegação concluída normalmente");
        } catch (com.microsoft.playwright.TimeoutError te) {
            System.out.println("⚠️ Timeout no navigate — verificando se página carregou mesmo assim...");
            tirarPrintDebug(page, "timeout_navigate_" + System.currentTimeMillis() + ".png");
            if (paginaDeDetalheCarregada(page)) {
                System.out.println("✅ Página carregada apesar do timeout — continuando");
                page.waitForTimeout(5000);
            } else {
                System.out.println("❌ Página não carregou após timeout. Relançando exceção.");
                throw te;
            }
        }
    }

    private static void aguardarNetworkIdleComTolerancia(Page page, int timeoutMs) {
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(timeoutMs));
        } catch (Exception e) {
            System.out.println("⚠️ NETWORKIDLE não atingido (normal em portais gov), continuando: "
                    + e.getMessage());
        }
    }

    private static boolean paginaDeDetalheCarregada(Page page) {
        try {
            String[] seletoresEsperados = {
                    "app-botao-mensagens-da-compra", ".cp-itens-card",
                    "h1, h2, h3", "app-acompanhamento-compra", "[class*='compra']"
            };
            for (String seletor : seletoresEsperados) {
                if (page.locator(seletor).count() > 0) {
                    System.out.println("✅ Seletor encontrado após timeout: " + seletor);
                    return true;
                }
            }
            String urlAtual = page.url();
            if (urlAtual.contains("acompanhamento-compra") || urlAtual.contains("cnetmobile")) {
                System.out.println("✅ URL correta após timeout: " + urlAtual);
                return true;
            }
            return false;
        } catch (Exception e) {
            System.out.println("Erro ao verificar página carregada: " + e.getMessage());
            return false;
        }
    }
}