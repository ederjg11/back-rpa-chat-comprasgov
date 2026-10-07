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
public class RPAChatComprasNetFullTesteV4 {

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
    // Preencha se quiser rotear via proxy BR para evitar bloqueio de IP AWS.
    // Deixe null para desabilitar.
    // =========================
    private static final String PROXY_SERVER   = null; // ex: "http://proxy.exemplo.com:8080"
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
                    .setHeadless(false)
                    .setSlowMo(600)
                    // Desativa verificação SSL do Firefox para proxies que fazem interceptação
                    // HTTPS (MITM). Sem isso o Firefox exibe "Potential Security Risk Ahead"
                    // e bloqueia a navegação quando o proxy apresenta seu próprio certificado.
                    .setArgs(List.of("--ignore-certificate-errors"));

            // Proxy residencial, se configurado
            if (PROXY_SERVER != null && !PROXY_SERVER.isEmpty()) {
                com.microsoft.playwright.options.Proxy proxy =
                        new com.microsoft.playwright.options.Proxy(PROXY_SERVER);
                if (PROXY_USERNAME != null) proxy.setUsername(PROXY_USERNAME);
                if (PROXY_PASSWORD != null) proxy.setPassword(PROXY_PASSWORD);
                launchOptions.setProxy(proxy);
                System.out.println("🌐 Proxy residencial configurado: " + PROXY_SERVER);
            }

            Browser browser = playwright.firefox().launch(launchOptions);

            // Cria contexto com configurações anti-fingerprint
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

            // =========================
            // LOGIN ROBUSTO
            // =========================
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

                    // =========================
                    // PRIMEIRA OPORTUNIDADE
                    // =========================
                    if (indice == 0) {

                        page.locator(".cp-itens-card")
                                .first()
                                .waitFor(
                                        new Locator.WaitForOptions()
                                                .setTimeout(30000)
                                                .setState(WaitForSelectorState.VISIBLE)
                                );

                        List<Locator> cards = page.locator(".cp-itens-card").all();

                        System.out.println("📦 TOTAL CARDS: " + cards.size());

                        boolean encontrou = false;

                        for (Locator card : cards) {

                            try {

                                String texto = card.innerText();

                                if (texto == null) {
                                    continue;
                                }

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

                        // =========================
                        // SEGUNDA OPORTUNIDADE+
                        // URL DIRETA
                        // =========================
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
                                "button[aria-label='Close'], button.p-dialog-header-icon"
                        );

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

                    // =========================
                    // AGUARDA CARREGAMENTO
                    // =========================
                    page.waitForLoadState(LoadState.NETWORKIDLE);
                    page.waitForTimeout(10000);

                    // =========================
                    // ABRE CHAT
                    // =========================
                    Locator chatBtn = page.locator("app-botao-mensagens-da-compra button").last();

                    chatBtn.waitFor(
                            new Locator.WaitForOptions()
                                    .setState(WaitForSelectorState.VISIBLE)
                    );

                    chatBtn.click();
                    page.waitForLoadState(LoadState.NETWORKIDLE);
                    page.waitForTimeout(10000);

                    System.out.println("✅ CHAT ABERTO");

                    // =========================
                    // CAPTURA CHAT
                    // =========================
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
            Thread.sleep(999999999);

        } catch (Exception e) {
            System.out.println("ERRO CRITICO: " + e.getMessage());
            tirarPrintDebug(imagemErroCritico, "erro_critico_11.png");
            deletarSessao();
            e.printStackTrace();
        }
    }

    // =========================
    // CONTEXTO ANTI-FINGERPRINT
    // Simula browser humano real para evitar detecção pelo ComprasGov
    // =========================

    /**
     * Cria um BrowserContext configurado para minimizar sinais de automação:
     *  - User-agent real do Firefox
     *  - Viewport, locale e timezone brasileiros
     *  - Headers HTTP naturais
     *  - Remove navigator.webdriver e outros flags do Playwright
     *  - Simula plugins, hardware concurrency e device memory reais
     */
    private static BrowserContext criarContextoHumano(Browser browser) throws Exception {

        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) "
                + "Gecko/20100101 Firefox/125.0";

        Browser.NewContextOptions opts = new Browser.NewContextOptions()
                .setUserAgent(userAgent)
                .setViewportSize(1366, 768)
                .setLocale("pt-BR")
                .setTimezoneId("America/Sao_Paulo")
                // Ignora erros de certificado SSL no nível do contexto.
                // Cobre o caso do proxy MITM que apresenta certificado próprio,
                // evitando o "Potential Security Risk Ahead" do Firefox/Nightly.
                .setIgnoreHTTPSErrors(true)
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

        // Injeta scripts de camuflagem em TODAS as páginas abertas pelo contexto
        context.addInitScript("""
            // Remove o flag mais óbvio de automação
            Object.defineProperty(navigator, 'webdriver', {
                get: () => undefined,
                configurable: true
            });

            // Remove flags internos do Playwright
            delete window.__playwright;
            delete window.__pw_manual;
            delete window.__pwInitScripts;

            // Garante que chrome não está definido (Firefox real não tem)
            window.chrome = undefined;

            // Simula plugins reais que um Firefox normal teria
            Object.defineProperty(navigator, 'plugins', {
                get: () => {
                    const arr = [
                        { name: 'PDF Viewer',        filename: 'internal-pdf-viewer',           description: 'Portable Document Format' },
                        { name: 'Chrome PDF Viewer', filename: 'mhjfbmdgcfjbbpaeojofohoefgiehjai', description: 'Portable Document Format' },
                        { name: 'Chromium PDF Viewer',filename: 'internal-pdf-viewer',           description: 'Portable Document Format' }
                    ];
                    arr.item   = (i) => arr[i];
                    arr.namedItem = (n) => arr.find(p => p.name === n) || null;
                    arr.refresh   = () => {};
                    return arr;
                },
                configurable: true
            });

            // Hardware realista
            Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => 4, configurable: true });
            Object.defineProperty(navigator, 'deviceMemory',        { get: () => 8, configurable: true });

            // Profundidade de cor padrão
            Object.defineProperty(screen, 'colorDepth',  { get: () => 24, configurable: true });
            Object.defineProperty(screen, 'pixelDepth',  { get: () => 24, configurable: true });

            // Mimetypes básicos
            Object.defineProperty(navigator, 'mimeTypes', {
                get: () => {
                    const arr = [
                        { type: 'application/pdf',         suffixes: 'pdf', description: '' },
                        { type: 'application/x-google-chrome-pdf', suffixes: 'pdf', description: '' }
                    ];
                    arr.item      = (i) => arr[i];
                    arr.namedItem = (n) => arr.find(m => m.type === n) || null;
                    return arr;
                },
                configurable: true
            });

            // Evita que Permission API retorne 'denied' instantaneamente (comportamento de bot)
            const originalQuery = window.navigator.permissions
                ? window.navigator.permissions.query.bind(window.navigator.permissions)
                : null;
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
    // ENUM ESTADO PÓS-CPF
    // =========================

    /**
     * Após submeter o CPF, detecta qual tela apareceu dentro do prazo maxEsperaMs.
     * Elimina a ambiguidade que causava loop infinito de captcha.
     */
    private static EstadoPosCpf detectarEstadoPosCpf(Page page, int maxEsperaMs) {

        System.out.println("🔍 Aguardando estado pós-CPF (max " + maxEsperaMs + "ms)...");
        long inicio = System.currentTimeMillis();

        while (System.currentTimeMillis() - inicio < maxEsperaMs) {
            try {

                // Prioridade 1: campo de senha visível = seguiu sem captcha
                Locator campoSenha = page.locator("input[name='password'], #password");
                if (campoSenha.count() > 0 && campoSenha.first().isVisible()) {
                    System.out.println("✅ Campo de senha detectado — fluxo sem captcha");
                    return EstadoPosCpf.CAMPO_SENHA_VISIVEL;
                }

                // Prioridade 2: captcha visível — hCaptcha (iframe) OU grid de imagens (gov.br)
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
    // Substitui realizarLogin + realizarLoginComCaptcha + validaCapthaAposLoginFalho
    // =========================

    /**
     * Login robusto com:
     *  - Verificação de sessão válida antes de tentar login
     *  - Digitação humanizada (delays aleatórios por tecla)
     *  - Detecção de estado pós-CPF (senha x captcha) antes de prosseguir
     *  - Resolução de hCaptcha via TwoCaptcha quando necessário
     *  - Seleção de empresa após login
     *  - Screenshots de debug em todo ponto de falha
     */
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

        // ============================================================
        // DETECÇÃO DE TELA BRANCA NO SSO GOV.BR
        // O sso.acesso.gov.br do SERPRO bloqueia IPs de proxy/datacenter
        // retornando uma página em branco silenciosa.
        // Se detectar tela branca, tenta forçar reload e aguarda conteúdo.
        // ============================================================
        if (paginaBrancaOuBloqueada(page)) {
            System.out.println("⚠️ Tela branca detectada no SSO Gov.br — tentando recuperação...");
            tirarPrintDebug(page, "tela_branca_sso_" + System.currentTimeMillis() + ".png");

            // Tenta reload até 3 vezes com espera crescente
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
                // Último recurso: navega diretamente para o SSO sem passar pelo comprasnet
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
                                    + "O IP do proxy pode estar bloqueado pelo SERPRO. "
                                    + "Tente um proxy residencial brasileiro diferente ou rode sem proxy.");
                }
            }
        }

        // Aguarda campo CPF
        Locator campoCpf = page.locator("input[name='accountId'], #accountId");
        campoCpf.waitFor(new Locator.WaitForOptions()
                .setTimeout(20000)
                .setState(WaitForSelectorState.VISIBLE));

        // Digita CPF com comportamento humano
        digitarComoHumano(page, campoCpf, CPF);
        page.waitForTimeout(500 + (int)(Math.random() * 700));

        // Clica em Continuar
        page.locator("#enter-account-id").click();

        // ============================================
        // PONTO CRÍTICO: detecta o que apareceu após CPF
        // ============================================
        EstadoPosCpf estado = detectarEstadoPosCpf(page, 20000);

        switch (estado) {

            case CAPTCHA_VISIVEL -> {
                System.out.println("🔐 Captcha detectado após CPF — resolvendo via TwoCaptcha...");
                boolean resolveu = resolverHCaptcha(page);

                if (!resolveu) {
                    tirarPrintDebug(page, "captcha_nao_resolvido_" + System.currentTimeMillis() + ".png");
                    throw new RuntimeException("Falha ao resolver hCaptcha. Abortando login.");
                }

                System.out.println("✅ hCaptcha resolvido. Clicando em Continuar novamente...");

                // Após injetar token, tenta clicar em Continuar
                Locator btnContinuar = page.locator("#enter-account-id");
                if (btnContinuar.count() > 0 && btnContinuar.isVisible()) {
                    btnContinuar.click();
                    page.waitForTimeout(3000);
                }

                // Aguarda campo de senha aparecer após captcha resolvido
                EstadoPosCpf estadoPosCaptcha = detectarEstadoPosCpf(page, 20000);
                if (estadoPosCaptcha != EstadoPosCpf.CAMPO_SENHA_VISIVEL) {
                    tirarPrintDebug(page, "pos_captcha_sem_senha_" + System.currentTimeMillis() + ".png");
                    throw new RuntimeException(
                            "Captcha resolvido mas campo de senha não apareceu. Estado: " + estadoPosCaptcha);
                }

                System.out.println("✅ Campo de senha disponível após resolver captcha");
                // Continua naturalmente para o preenchimento da senha abaixo
            }

            case CAMPO_SENHA_VISIVEL -> {
                // Fluxo feliz: sem captcha
                System.out.println("✅ Fluxo sem captcha");
            }

            case ERRO_DESCONHECIDO -> {
                tirarPrintDebug(page, "estado_desconhecido_pos_cpf_" + System.currentTimeMillis() + ".png");
                throw new RuntimeException(
                        "Estado desconhecido após submeter CPF. Screenshot salvo para diagnóstico.");
            }
        }

        // Preenche senha com digitação humanizada
        Locator campoSenha = page.locator("input[name='password'], #password");
        campoSenha.waitFor(new Locator.WaitForOptions()
                .setTimeout(15000)
                .setState(WaitForSelectorState.VISIBLE));

        digitarComoHumano(page, campoSenha, SENHA);
        page.waitForTimeout(600 + (int)(Math.random() * 600));

        page.locator("#submit-button").click();
        page.waitForTimeout(10000);

        // Salva sessão
        context.storageState(new BrowserContext.StorageStateOptions()
                .setPath(Paths.get(SESSION_FILE)));
        System.out.println("✅ Sessão salva em " + SESSION_FILE);

        selecionarEmpresaSeNecessario(page);
    }

    /**
     * Detecta se a página está em branco ou foi bloqueada silenciosamente.
     * O SSO do Gov.br (SERPRO) retorna página vazia quando detecta proxy/datacenter.
     *
     * Critérios de "tela branca":
     *  - body sem texto visível (menos de 30 chars)
     *  - nenhum elemento de formulário ou conteúdo visível
     *  - URL ainda é do SSO mas não renderizou nada
     */
    private static boolean paginaBrancaOuBloqueada(Page page) {
        try {
            // Aguarda um tempo mínimo para o JS carregar
            page.waitForTimeout(2000);

            // Checa se há conteúdo mínimo no body
            String bodyText = (String) page.evaluate(
                    "() => document.body ? document.body.innerText.trim() : ''");
            if (bodyText != null && bodyText.length() > 30) {
                return false; // tem conteúdo, não está branca
            }

            // Checa seletores que indicam SSO carregado corretamente
            String[] seletoresEsperados = {
                    "input[name='accountId']",  // campo CPF
                    "#accountId",
                    "button",                   // qualquer botão
                    "form",                     // qualquer formulário
                    "h1, h2",                   // qualquer título
                    ".login-box",
                    "#loginData"
            };
            for (String sel : seletoresEsperados) {
                if (page.locator(sel).count() > 0) {
                    return false; // encontrou conteúdo real
                }
            }

            // Loga título e tamanho do body para diagnóstico
            String title = page.title();
            System.out.println("📄 Título: '" + title + "' | Body chars: "
                    + (bodyText != null ? bodyText.length() : 0));

            return true; // sem conteúdo = tela branca / bloqueio silencioso

        } catch (Exception e) {
            System.out.println("Erro ao verificar tela branca: " + e.getMessage());
            return false; // na dúvida não bloqueia o fluxo
        }
    }

    /**
     * Seleciona a empresa MEDIPHACOS no portal se a tela de seleção aparecer.
     */
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

    /**
     * Digita texto caractere a caractere com delays aleatórios entre 80–200ms,
     * simulando velocidade humana de digitação para evitar detecção por comportamento.
     */
    private static void digitarComoHumano(Page page, Locator campo, String texto) throws Exception {
        campo.click();
        page.waitForTimeout(300 + (int)(Math.random() * 300));

        // Seleciona tudo e apaga — triple-click seleciona o conteúdo do campo,
        // depois Control+A garante seleção completa mesmo em campos com texto longo
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
    // HCAPTCHA — TwoCaptcha
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
            // hCaptcha iframe
            if (page.locator("iframe[src*='hcaptcha.com']").count() > 0) {
                return true;
            }
            return false;
        } catch (Exception e) {
            System.out.println("Erro ao verificar captcha: " + e.getMessage());
            return false;
        }
    }

    /**
     * Resolve o captcha de grid de imagens do Gov.br via 2captcha (API v2 / GridTask).
     *
     * Fluxo:
     *  1. Tira screenshot da área do captcha
     *  2. Envia para 2captcha como GridTask (base64)
     *  3. Polling até receber os índices das células corretas
     *  4. Clica nas células indicadas dentro do browser
     *  5. Clica em Avançar/Verificar para submeter
     *
     * Retorna true se resolveu com sucesso.
     */
    private static boolean resolverHCaptcha(Page page) {
        try {
            System.out.println("🔐 Iniciando resolução captcha grid via TwoCaptcha...");

            // 1. Tira screenshot da área do captcha e converte para base64
            Locator areaCaptcha = page.locator(".challenge-container, .rc-imageselect, [class*='challenge']").first();
            byte[] screenshotBytes;
            if (areaCaptcha.count() > 0) {
                screenshotBytes = areaCaptcha.screenshot();
            } else {
                // Fallback: screenshot da página inteira se não achar o container
                screenshotBytes = page.screenshot(new Page.ScreenshotOptions().setFullPage(false));
            }

            String imagemBase64 = java.util.Base64.getEncoder().encodeToString(screenshotBytes);
            System.out.println("📸 Screenshot do captcha capturado (" + screenshotBytes.length + " bytes)");

            // 2. Captura o texto da instrução do captcha para enviar ao 2captcha
            String instrucao = "select all matching images";
            try {
                String textoInstrucao = page.locator(
                        ".challenge-prompt, .rc-imageselect-desc, [class*='prompt'], [class*='instruction']"
                ).first().innerText();
                if (textoInstrucao != null && !textoInstrucao.trim().isEmpty()) {
                    instrucao = textoInstrucao.trim();
                }
            } catch (Exception ignored) {}
            System.out.println("📝 Instrução do captcha: " + instrucao);

            // 3. Envia para o 2captcha como GridTask (API v2)
            ObjectMapper om = new ObjectMapper();

            String corpoJson = om.writeValueAsString(om.createObjectNode()
                    .put("clientKey", TWOCAPTCHA_API_KEY)
                    .set("task", om.createObjectNode()
                            .put("type",    "GridTask")
                            .put("body",    imagemBase64)
                            .put("comment", instrucao)
                            .put("rows",    3)
                            .put("columns", 3)
                    )
            );

            String submitResponse = fazerPostHttp("https://api.2captcha.com/createTask", corpoJson);
            System.out.println("TwoCaptcha createTask: " + submitResponse);

            JsonNode submitJson = om.readTree(submitResponse);
            if (submitJson.path("errorId").asInt() != 0) {
                System.out.println("❌ Erro ao criar task no 2captcha: " + submitResponse);
                return false;
            }

            String taskId = submitJson.path("taskId").asText();
            System.out.println("📨 Task criada. ID: " + taskId);

            // 4. Polling getTaskResult (máx 120s = 24 × 5s)
            String getResultJson = om.writeValueAsString(om.createObjectNode()
                    .put("clientKey", TWOCAPTCHA_API_KEY)
                    .put("taskId",    taskId)
            );

            JsonNode solution = null;
            for (int tentativa = 0; tentativa < 24; tentativa++) {
                Thread.sleep(5000);

                String resResponse = fazerPostHttp("https://api.2captcha.com/getTaskResult", getResultJson);
                JsonNode resJson   = om.readTree(resResponse);

                System.out.println("TwoCaptcha polling [" + tentativa + "]: status=" + resJson.path("status").asText());

                if ("ready".equals(resJson.path("status").asText())) {
                    solution = resJson.path("solution");
                    System.out.println("✅ Solução recebida: " + solution);
                    break;
                }

                if (resJson.path("errorId").asInt() != 0) {
                    System.out.println("❌ Erro no 2captcha: " + resResponse);
                    return false;
                }
            }

            if (solution == null) {
                System.out.println("❌ Timeout aguardando 2captcha (120s)");
                return false;
            }

            // 5. Clica nas células indicadas pelo 2captcha
            // A resposta vem como array "click": [1, 3, 7]
            // Índices começam em 1 (top-left), ordem left-to-right, top-to-bottom
            JsonNode clickArray = solution.path("click");
            if (!clickArray.isArray() || clickArray.size() == 0) {
                System.out.println("⚠️ Nenhuma célula para clicar — captcha pode não ter match");
                // Mesmo sem cliques, tenta avançar
            } else {
                System.out.println("🖱️ Clicando nas células: " + clickArray);
                clicarCelulasCaptcha(page, clickArray);
            }

            page.waitForTimeout(1000);

            // 6. Clica em Avançar para submeter o captcha
            Locator btnAvancar = page.locator("button:has-text('Avançar'), button:has-text('Verificar'), button:has-text('Next')");
            if (btnAvancar.count() > 0) {
                btnAvancar.first().click();
                System.out.println("✅ Clicou em Avançar");
                page.waitForTimeout(3000);
            }

            return true;

        } catch (Exception e) {
            System.out.println("❌ Erro ao resolver captcha grid: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Clica nas células do captcha de grid conforme os índices retornados pelo 2captcha.
     * Índice 1 = célula top-left, incrementa da esquerda para direita, de cima para baixo.
     * Ex: grid 3x3 → índice 5 = célula central.
     */
    private static void clicarCelulasCaptcha(Page page, JsonNode clickArray) {
        try {
            // Localiza todas as células clicáveis do grid
            // O Gov.br usa estrutura de tabela ou divs com as imagens
            List<Locator> celulas = page.locator(
                    ".challenge-container td, .rc-imageselect-table td, " +
                            "[class*='challenge'] td, [class*='challenge'] li, " +
                            "[class*='tile'], .captcha-grid-item"
            ).all();

            System.out.println("🔲 Total de células encontradas: " + celulas.size());

            for (JsonNode indiceNode : clickArray) {
                int indice = indiceNode.asInt(); // 1-based
                int posicao = indice - 1;        // 0-based

                if (posicao >= 0 && posicao < celulas.size()) {
                    celulas.get(posicao).click();
                    System.out.println("✅ Clicou na célula " + indice);
                    page.waitForTimeout(400 + (int)(Math.random() * 300));
                } else {
                    System.out.println("⚠️ Índice " + indice + " fora do range (" + celulas.size() + " células)");
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao clicar células: " + e.getMessage());
        }
    }

    /**
     * Executa uma requisição HTTP GET simples e retorna o corpo da resposta.
     */
    private static String fazerGetHttp(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        byte[] bytes = conn.getInputStream().readAllBytes();
        conn.disconnect();
        return new String(bytes);
    }

    /**
     * Executa uma requisição HTTP POST com body JSON e retorna o corpo da resposta.
     * Usado pela API v2 do 2captcha (createTask / getTaskResult).
     */
    private static String fazerPostHttp(String urlStr, String jsonBody) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setDoOutput(true);
        conn.getOutputStream().write(jsonBody.getBytes("UTF-8"));
        byte[] bytes = conn.getInputStream().readAllBytes();
        conn.disconnect();
        return new String(bytes, "UTF-8");
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
                            "button[data-pc-section='page'][aria-label='Página " + i + "']"
                    );

                    if (pageButton.count() == 0) {
                        System.out.println("⚠️ PAGINA NÃO ENCONTRADA: " + i);
                        continue;
                    }

                    System.out.println("\n🔄 INDO PARA PAGINA " + i);

                    ULTIMO_JSON_CHAT = "";

                    pageButton.first().click();
                    page.waitForTimeout(8000);

                    boolean captcha = page.locator(
                            "text=Não foi possível realizar a validação do Captcha"
                    ).count() > 0;

                    if (captcha) {
                        System.out.println("⚠️ CAPTCHA DETECTADO NA PAGINAÇÃO — encerrando paginação");
                        break;
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

                if (id == null || id.isEmpty()) {
                    continue;
                }

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
                    // dispararWhatsAppNovaMsg(oportunidade);
                    System.out.println("Nova mensagem detectada: " + texto + " — Oportunidade: " + oportunidade.getId());
                }

                if (textoMencionaCliente(texto, oportunidade)) {
                    // dispararWhatsApp(oportunidade);
                }
            }

        } catch (Exception e) {
            System.out.println("⚠️ ERRO PROCESSANDO JSON");
            e.printStackTrace();
        }
    }

    // =========================
    // SALVAR H2 (legado — mantido para compatibilidade)
    // =========================
    private static void salvar(
            String cliente,
            String uasg,
            String pregao,
            String id,
            String texto,
            String remetente,
            String data,
            Integer item
    ) {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:./chat-db", "sa", "")) {

            PreparedStatement ps = conn.prepareStatement(
                    "MERGE INTO CHAT_MENSAGEM "
                            + "(ID, CLIENTE, UASG, PREGAO, TEXTO, REMETENTE, DATA, ITEM) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
            );

            ps.setString(1, id);
            ps.setString(2, cliente);
            ps.setString(3, uasg);
            ps.setString(4, pregao);
            ps.setString(5, texto);
            ps.setString(6, remetente);
            ps.setString(7, data);
            ps.setObject(8, item);
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
                        + "ID VARCHAR PRIMARY KEY, "
                        + "CLIENTE VARCHAR, "
                        + "UASG VARCHAR, "
                        + "PREGAO VARCHAR, "
                        + "TEXTO CLOB, "
                        + "REMETENTE VARCHAR, "
                        + "DATA VARCHAR, "
                        + "ITEM INT)"
        );

        conn.close();
    }

    // =========================
    // JSON SAFE
    // =========================
    private static String getText(JsonNode node, String... campos) {
        for (String c : campos) {
            JsonNode n = node.get(c);
            if (n != null && !n.isNull()) {
                return n.asText();
            }
        }
        return "";
    }

    // =========================
    // BUSCAR OPORTUNIDADES DO BANCO
    // =========================
    private static List<Oportunidade> buscarOportunidadesDoBanco() {

        List<Oportunidade> lista = new ArrayList<>();

        String sql =
                "SELECT  O.ID, O.CLIENTE_ID, CLI.CNPJ, CLI.RAZAO_SOCIAL, CLI.FANTASIA, " +
                        "                              O.MODALIDADE_ID, O.NUMERO_EDITAL, O.UASG_OC, O.DATA_CERTAME, " +
                        "                              O.STATUS, CLI.FANTASIA AS CLIENTE, O.UASG_OC AS UASG, " +
                        "                              O.NUMERO_EDITAL AS NUMERO_PREGAO " +
                        "                       FROM vista.VISTA_OPORTUNIDADE O " +
                        "                       JOIN vista.VISTA_CLIENTE CLI ON CLI.ID = O.CLIENTE_ID " +
                        "                       WHERE 1 = 1 " +
                        "                         AND O.UASG_OC IS NOT NULL " +
                        "                          AND O.ID IN (SELECT TEXTO FROM vista.VISTA_PARAMETRO_SISTEMA " +
                        "                                         WHERE PARAMETRO = 'CHAT_COMPRAS_GOV') " +
                        "                       ORDER BY O.ID DESC";

        Connection       conn = null;
        PreparedStatement ps  = null;
        ResultSet         rs  = null;

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
                if (sqlDate != null) {
                    dataCertame = sqlDate.toLocalDate();
                }

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
    // FORMATAR NÚMERO PREGÃO
    // =========================
    private static String formatarNumeroPregao(String numeroEdital) {

        if (numeroEdital == null || numeroEdital.trim().isEmpty()) {
            return "";
        }

        String s = numeroEdital.trim();

        if (s.contains("/")) {
            return s;
        }

        String apenasNumeros = s.replaceAll("[^0-9]", "");

        if (apenasNumeros.length() <= 4) {
            return s;
        }

        String ano    = apenasNumeros.substring(apenasNumeros.length() - 4);
        String numero = apenasNumeros.substring(0, apenasNumeros.length() - 4);

        return numero + "/" + ano;
    }

    // =========================
    // FECHAR RECURSOS JDBC
    // =========================
    private static void fechar(ResultSet rs, PreparedStatement ps, Connection conn) {

        if (rs != null) {
            try { rs.close(); } catch (Exception ignored) {
                System.out.println("Erro ao fechar ResultSet: " + ignored.getMessage());
            }
        }
        if (ps != null) {
            try { ps.close(); } catch (Exception ignored) {}
        }
        if (conn != null) {
            try { conn.close(); } catch (Exception ignored) {}
        }
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

        InputStream is = RPAChatComprasNetFullTesteV4.class
                .getClassLoader()
                .getResourceAsStream("application.properties");

        if (is == null) {
            File f = new File("application.properties");
            if (f.exists()) {
                is = new FileInputStream(f);
            }
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

        Connection        conn      = null;
        PreparedStatement psCheck   = null;
        PreparedStatement psInsert  = null;
        ResultSet         rs        = null;

        try {
            conn = getConexaoMySQL();

            psCheck = conn.prepareStatement(
                    "SELECT ID FROM vista.VISTA_PREGAO_CHAT "
                            + "WHERE MENSAGEM = ? "
                            + "AND OPORTUNIDADE_ID = ? "
                            + "AND DATA_CHAT_PREGOEIRO = ? "
            );

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
                            + "VALUES (?, ?, ?, ?, ?, NOW())"
            );

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

        if (dataStr == null || dataStr.trim().isEmpty()) {
            return null;
        }

        String[] formatos = {
                "dd/MM/yyyy HH:mm",
                "dd/MM/yyyy 'as' HH:mm",
                "dd/MM/yyyy",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd HH:mm:ss"
        };

        String s = dataStr.trim()
                .replace(" às ", " ")
                .replace(" as ", " ")
                .replaceAll("([+-]\\d{2}:\\d{2})$", "");

        for (String fmt : formatos) {
            try {
                return LocalDateTime.parse(s, DateTimeFormatter.ofPattern(fmt));
            } catch (DateTimeParseException ignored) {}
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
            page.screenshot(
                    new Page.ScreenshotOptions()
                            .setPath(Paths.get(nomeArquivo))
                            .setFullPage(true)
            );
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
                + oportunidade.getNumeroEdital()
                + " do cliente "
                + oportunidade.getCliente()
                + ". Favor verificar!";

        System.out.println("DISPARO WHATSAPP NOVA MENSAGEM: " + mensagem);

        try {
            WhatsAppNotificationService.sendGroupMessage(WHATS_NUMERO, WHATS_GRUPO, mensagem);
        } catch (Exception e) {
            System.out.println("Erro ao enviar WhatsApp de nova mensagem: " + e.getMessage());
        }
    }

    private static void dispararWhatsApp(Oportunidade oportunidade) {

        String mensagem = "URGENTE: Houve alteracoes no chat do edital "
                + oportunidade.getNumeroEdital()
                + " do cliente "
                + oportunidade.getCliente()
                + ". Favor verificar!";

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

        if (texto == null || texto.trim().isEmpty()) {
            return false;
        }

        String textoUpper = texto.toUpperCase().trim();

        String fantasia = oportunidade.getFantasia();
        if (fantasia != null && !fantasia.trim().isEmpty()) {
            if (textoUpper.contains(fantasia.toUpperCase().trim())) return true;
        }

        String razao = oportunidade.getRazaoSocial();
        if (razao != null && !razao.trim().isEmpty()) {
            String razaoUpper = razao.toUpperCase().trim();
            if (textoUpper.contains(razaoUpper)) return true;

            String[] palavras = razaoUpper.split("\\s+");
            if (palavras.length > 0 && palavras[0].length() >= 4) {
                if (textoUpper.contains(palavras[0])) return true;
            }
        }

        String cliente = oportunidade.getCliente();
        if (cliente != null && !cliente.trim().isEmpty() && cliente.length() >= 4) {
            if (textoUpper.contains(cliente.toUpperCase().trim())) return true;
        }

        String cnpj = oportunidade.getCnpj();
        if (cnpj != null && !cnpj.trim().isEmpty()) {
            String cnpjNumeros = cnpj.replaceAll("[^0-9]", "");

            if (!cnpjNumeros.isEmpty() && textoUpper.contains(cnpjNumeros)) return true;

            if (cnpjNumeros.length() == 14) {
                String cnpjMascarado =
                        cnpjNumeros.substring(0, 2) + "."
                                + cnpjNumeros.substring(2, 5) + "."
                                + cnpjNumeros.substring(5, 8) + "/"
                                + cnpjNumeros.substring(8, 12) + "-"
                                + cnpjNumeros.substring(12);

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
                        : "Não foi possível deletar: "   + SESSION_FILE);
            }
        } catch (Exception e) {
            System.out.println("Erro ao deletar sessão: " + e.getMessage());
        }
    }

    // =========================
    // NAVEGAÇÃO RESILIENTE
    // =========================

    /**
     * Navega para uma URL tolerando TimeoutError do Playwright.
     * O portal cnetmobile frequentemente não dispara o evento "load"
     * dentro do timeout padrão, mas a página já está utilizável.
     *
     * Estratégia:
     *  1. Tenta navigate com timeout estendido (90s) e waitUntil=DOMCONTENTLOADED
     *  2. Se der timeout, verifica se o conteúdo esperado está na página
     *  3. Se a página estiver vazia/erro real, relança a exceção
     */
    private static void navegarComResiliencia(Page page, String url) throws Exception {

        System.out.println("🌐 Navegando (resiliente) para: " + url);

        try {
            page.navigate(url, new Page.NavigateOptions()
                    .setTimeout(90000)
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
            );

            aguardarNetworkIdleComTolerancia(page, 30000);
            page.waitForTimeout(12000);
            System.out.println("✅ Navegação concluída normalmente");

        } catch (com.microsoft.playwright.TimeoutError te) {

            System.out.println("⚠️ Timeout no navigate — verificando se página carregou mesmo assim...");
            tirarPrintDebug(page, "timeout_navigate_" + System.currentTimeMillis() + ".png");

            boolean paginaCarregada = paginaDeDetalheCarregada(page);

            if (paginaCarregada) {
                System.out.println("✅ Página carregada apesar do timeout — continuando");
                page.waitForTimeout(5000);
            } else {
                System.out.println("❌ Página não carregou após timeout. Relançando exceção.");
                throw te;
            }
        }
    }

    /**
     * Tenta aguardar NETWORKIDLE mas não falha se demorar.
     * Portais gov frequentemente mantêm conexões abertas indefinidamente.
     */
    private static void aguardarNetworkIdleComTolerancia(Page page, int timeoutMs) {
        try {
            page.waitForLoadState(
                    LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(timeoutMs)
            );
        } catch (Exception e) {
            System.out.println("⚠️ NETWORKIDLE não atingido (normal em portais gov), continuando: "
                    + e.getMessage());
        }
    }

    /**
     * Verifica se a página de detalhe do comprasnet carregou com conteúdo válido.
     * Checa múltiplos seletores para ser resiliente a variações no portal.
     */
    private static boolean paginaDeDetalheCarregada(Page page) {
        try {
            String[] seletoresEsperados = {
                    "app-botao-mensagens-da-compra",
                    ".cp-itens-card",
                    "h1, h2, h3",
                    "app-acompanhamento-compra",
                    "[class*='compra']",
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