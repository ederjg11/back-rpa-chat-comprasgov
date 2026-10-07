package br.gov.compras.rpa.service;

import br.gov.compras.rpa.model.Oportunidade;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.BoundingBox;
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
import java.util.Properties;



@Component
@EnableScheduling
public class RPAChatComprasNetFullTesteV3 {

    // ============================================================
    // CONFIGURAÇÕES GERAIS
    // ============================================================

    private static String CPF;
    private static String SENHA;

    private static String DB_URL;
    private static String DB_USER;
    private static String DB_PASSWORD;
    private static final String TWOCAPTCHA_API_KEY = "52067df225b4ea06685c5ea1d898f88b"; // ← substitua pela sua chave
    private static final String HCAPTCHA_SITEKEY   = "93b08d40-d46c-400a-ba07-6f91cda815b9";

    private static int countNovaMensagem = 0;
    private static boolean mensagemExiste = false;

    private static final String WHATS_NUMERO = "5534996461183";
    private static final String WHATS_GRUPO = "ChatComprasGov";

    private static final String SESSION_FILE = "session.json";

    private static List<Oportunidade> OPORTUNIDADES;

    // Último JSON recebido pelo listener global do /v2/chat
    private static volatile String ULTIMO_JSON_CHAT = "";

    // ============================================================
    // RUN PRINCIPAL
    // ============================================================

    public void run() {
        Playwright playwright = null;
        Browser browser = null;

        try {
            // ============================================================
            // BANCO / CONFIG
            // ============================================================
            /*
            org.h2.tools.Server
                    .createWebServer("-web", "-webPort", "8082")
                    .start();

            carregarConfigBanco();
            criarTabela();

             */
            carregarConfigBanco();
            List<Oportunidade> oportunidades = buscarOportunidadesDoBanco();

            if (oportunidades.isEmpty()) {
                System.out.println("Nenhuma oportunidade encontrada no banco. Encerrando.");
                return;
            }

            System.out.println("Total de oportunidades carregadas: " + oportunidades.size());
            OPORTUNIDADES = oportunidades;

            // ============================================================
            // PLAYWRIGHT
            // ============================================================
            playwright = Playwright.create();

            browser = playwright.firefox().launch(
                    new BrowserType.LaunchOptions()
                            // Para testar local no Mac, deixe false.
                            // Para AWS sem interface gráfica, você pode trocar para true depois.
                            .setHeadless(true)
                            .setSlowMo(0)
            );

            BrowserContext context;

            if (Files.exists(Paths.get(SESSION_FILE))) {
                context = browser.newContext(
                        new Browser.NewContextOptions()
                                .setStorageStatePath(Paths.get(SESSION_FILE))
                );

                System.out.println("Sessão carregada de " + SESSION_FILE);
            } else {
                context = browser.newContext();
                System.out.println("Nova sessão. Será necessário login.");
            }

            ObjectMapper mapper = new ObjectMapper();

            registrarListenerGlobalChat(context);

            Page page = context.newPage();

            // ============================================================
            // LOGIN
            // ============================================================
            realizarLogin(context, page);

            // ============================================================
            // ABRE PORTAL ANTIGO SEM PERDER ABA
            // ============================================================
            page = navegarPortalAntigoSemPerderAba(context, page);

            // ============================================================
            // LOOP OPORTUNIDADES
            // ============================================================
            for (int indice = 0; indice < OPORTUNIDADES.size(); indice++) {
                mensagemExiste = false;
                countNovaMensagem = 0;
                ULTIMO_JSON_CHAT = "";

                Oportunidade oportunidade = OPORTUNIDADES.get(indice);

                try {
                    System.out.println("==================================");
                    System.out.println("PROCESSANDO OPORTUNIDADE");
                    System.out.println("ID: " + oportunidade.getId());
                    System.out.println("CLIENTE: " + oportunidade.getCliente());
                    System.out.println("UASG: " + oportunidade.getUasg());
                    System.out.println("PREGÃO: " + oportunidade.getNumeroPregao());
                    System.out.println("==================================");

                    if (indice == 0) {
                        // Primeira oportunidade mantém o fluxo por card,
                        // pois foi o caminho que você validou que funcionava.
                        boolean abriu = abrirPrimeiraOportunidadePorCard(page, oportunidade, indice);

                        if (!abriu) {
                            System.out.println("OPORTUNIDADE NÃO ENCONTRADA PELO CARD: " + oportunidade.getId());
                            continue;
                        }

                        aguardarTelaComprasCarregada(page, 90000);
                    } else {
                        // Demais oportunidades usam URL direta para evitar voltar página,
                        // perder sessão ou depender da listagem antiga.
                        page = abrirOportunidadePorUrlDireta(context, page, oportunidade);
                    }

                    // IMPORTANTE:
                    // Não usamos page.reload() aqui.
                    // A tela dos prints mostra que ela carrega corretamente.
                    // O reload e o NETWORKIDLE estavam causando travas/intermitência.
                    aguardarTelaComprasCarregada(page, 90000);

                    abrirChatComSeguranca(page);

                    capturarMensagensPaginadasComInteracao(oportunidade, page, mapper);

                    if (mensagemExiste) {
                        System.out.println("Mensagem já existente no banco. Seguindo para próxima oportunidade: " + oportunidade.getId());
                        mensagemExiste = false;
                    }

                } catch (Exception e) {
                    System.out.println("ERRO OPORTUNIDADE: " + oportunidade.getId());
                    tirarPrintDebug(page, "erro_oportunidade_" + indice + ".png");
                    e.printStackTrace();
                }
            }

            System.out.println("FINALIZADO");

                    // Mantive a sessão para reaproveitar nas próximas execuções.
                    // Se quiser forçar novo login sempre, descomente:
                    // deletarSessao();

                    // Para você conseguir ver a tela no teste local.
                    Thread.sleep(999999999);

        } catch (Exception e) {
            System.out.println("ERRO CRÍTICO: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Em teste local, deixei sem fechar para você conseguir ver a tela.
            // Em produção, pode fechar browser/playwright aqui.
        }
    }

    // ============================================================
    // LISTENER GLOBAL DO CHAT
    // ============================================================

    private static void registrarListenerGlobalChat(BrowserContext context) {
        context.onResponse(response -> {
            try {
                if (response.url() == null || !response.url().contains("/v2/chat")) {
                    return;
                }

                System.out.println("CHAT RESPONSE");
                        System.out.println(response.url());
                System.out.println("STATUS: " + response.status());

                String body = response.text();

                if (body == null || body.trim().isEmpty()) {
                    System.out.println("BODY VAZIO");
                    return;
                }

                ULTIMO_JSON_CHAT = body;
                System.out.println("JSON RECEBIDO DO CHAT");

            } catch (Exception e) {
                System.out.println("Erro no listener global do chat: " + e.getMessage());
            }
        });
    }

    // ============================================================
    // LOGIN
    // ============================================================

    private static void realizarLogin(
            BrowserContext context,
            Page page
    ) throws Exception {

        page.navigate(
                "https://www.comprasnet.gov.br/seguro/loginFornecedorSelecEmpresa.asp"
        );

        page.waitForTimeout(5000);

        try {
            if (page.locator("text=Entrar com Gov.br").count() > 0) {

                System.out.println("🔐 Fazendo login");

                page.locator("text=Entrar com Gov.br").click();
                page.waitForLoadState();

                page.fill("input[name='accountId'], #accountId", CPF);
                page.locator("#enter-account-id").click();
                page.waitForTimeout(3000);

                page.fill("input[name='password'], #password", SENHA);
                page.locator("#submit-button").click();
                page.waitForTimeout(10000);

                context.storageState(
                        new BrowserContext.StorageStateOptions()
                                .setPath(Paths.get(SESSION_FILE))
                );

                System.out.println("✅ Sessão salva");
            }
        } catch (Exception e) {
            System.out.println("Erro ao carregar pagina de login, mas continuando: " + e.getMessage());
            validaCapthaAposLoginFalho(context, page);
            tirarPrintDebug(page, "erro_login.png");
        }

        // =========================
        // EMPRESA
        // =========================
        if (page.locator("text=MEDIPHACOS INDUSTRIAS MEDICAS S/A").count() > 0) {

            page.locator("text=MEDIPHACOS INDUSTRIAS MEDICAS S/A").first().click();

            page.locator("input[type='submit'], button:has-text('Confirmar')").click();

            page.waitForTimeout(8000);
        }
    }

    // ============================================================
    // NAVEGAÇÃO / ABAS / TELAS
    // ============================================================

    private static Page navegarPortalAntigoSemPerderAba(BrowserContext context, Page page) {
        try {
            System.out.println("Abrindo portal antigo ComprasNet...");

            page.bringToFront();

            int totalAbasAntes = context.pages().size();

            page.navigate(
                    "https://www.comprasnet.gov.br/assinadas/dispensa_eletronica.asp",
                    new Page.NavigateOptions().setTimeout(120000)
            );

            page.waitForTimeout(12000);

            Page melhorPagina = localizarPaginaComprasNet(context, page, totalAbasAntes);
            melhorPagina.bringToFront();

            System.out.println("Página ativa após abrir portal: " + melhorPagina.url());

            aguardarTelaComprasCarregada(melhorPagina, 90000);

            return melhorPagina;

        } catch (Exception e) {
            tirarPrintDebug(page, "erro_abrir_portal_antigo.png");
            throw new RuntimeException("Erro ao abrir portal antigo: " + e.getMessage(), e);
        }
    }

    private static Page localizarPaginaComprasNet(BrowserContext context, Page fallback, int totalAbasAntes) {
        List<Page> paginas = context.pages();

        for (int i = paginas.size() - 1; i >= 0; i--) {
            Page p = paginas.get(i);

            try {
                if (p.isClosed()) {
                    continue;
                }

                String url = p.url();

                if (url != null &&
                        (
                                url.contains("assinadas/dispensa_eletronica.asp")
                                        || url.contains("comprasnet-web/seguro")
                                        || url.contains("acompanhamento-compra")
                        )) {
                    return p;
                }
            } catch (Exception ignored) {
            }
        }

        if (paginas.size() > totalAbasAntes) {
            return paginas.get(paginas.size() - 1);
        }

        return fallback;
    }

    private static void aguardarTelaComprasCarregada(Page page, int timeoutMs) {
        long inicio = System.currentTimeMillis();

        while ((System.currentTimeMillis() - inicio) < timeoutMs) {
            try {
                if (page.locator("app-botao-mensagens-da-compra button").count() > 0) {
                    System.out.println("Tela carregada: botão de mensagens encontrado.");
                    return;
                }

                if (page.locator("text=Acompanhamento seleção de fornecedores").count() > 0) {
                    System.out.println("Tela carregada: título de acompanhamento encontrado.");
                    return;
                }

                if (page.locator(".cp-itens-card").count() > 0) {
                    System.out.println("Tela carregada: cards encontrados.");
                    return;
                }

                if (page.locator("button:has-text('Mensagens da compra')").count() > 0) {
                    System.out.println("Tela carregada: botão mensagens encontrado por texto.");
                    return;
                }

                page.waitForTimeout(1000);

            } catch (Exception ignored) {
            }
        }

        tirarPrintDebug(page, "tela_compras_nao_confirmada.png");
        throw new RuntimeException("Tela do ComprasNet não confirmou carregamento dentro do tempo.");
    }

    // ============================================================
    // ABERTURA DE OPORTUNIDADE
    // ============================================================

    private static boolean abrirPrimeiraOportunidadePorCard(Page page, Oportunidade oportunidade, int indice) {
        try {
            page.locator(".cp-itens-card")
                    .first()
                    .waitFor(
                            new Locator.WaitForOptions()
                                    .setTimeout(90000)
                                    .setState(WaitForSelectorState.VISIBLE)
                    );

            List<Locator> cards = page.locator(".cp-itens-card").all();
            System.out.println("TOTAL CARDS: " + cards.size());

            for (Locator card : cards) {
                try {
                    String texto = card.innerText();

                    if (texto == null) {
                        continue;
                    }

                    System.out.println("-------------------");
                            System.out.println(texto);

                    if (texto.contains(oportunidade.getNumeroPregao())
                            && texto.contains(oportunidade.getUasg())) {

                        System.out.println("OPORTUNIDADE ENCONTRADA PELO CARD");

                        Locator botaoExpandir = card.locator("button:has(i.fa-plus-square), button:has(.fa-plus-square), button").first();

                        botaoExpandir.scrollIntoViewIfNeeded();
                        page.waitForTimeout(1000);
                        botaoExpandir.click(new Locator.ClickOptions().setTimeout(30000));

                        page.waitForTimeout(8000);
                        return true;
                    }

                } catch (Exception e) {
                    System.out.println("Erro ao processar card, continuando: " + e.getMessage());
                    tirarPrintDebug(page, "erro_card_oportunidade_" + indice + ".png");
                }
            }

            return false;

        } catch (Exception e) {
            tirarPrintDebug(page, "erro_primeira_oportunidade_card_" + indice + ".png");
            throw new RuntimeException("Erro ao abrir primeira oportunidade por card: " + e.getMessage(), e);
        }
    }

    private static Page abrirOportunidadePorUrlDireta(BrowserContext context, Page page, Oportunidade oportunidade) {
        try {
            System.out.println("Abrindo via URL direta da oportunidade: " + oportunidade.getId());

            String uasg = String.format("%06d", Integer.parseInt(oportunidade.getUasg()));

            String pregao = oportunidade.getNumeroPregao()
                    .replace("/", "")
                    .replace("-", "")
                    .trim();

            String numeroCompra = uasg + "05" + pregao;

            String urlCompra =
                    "https://cnetmobile.estaleiro.serpro.gov.br/comprasnet-web/seguro/fornecedor/acompanhamento-compra?compra="
                            + numeroCompra;

            System.out.println(urlCompra);

            fecharChatSeAberto(page);

            int totalAbasAntes = context.pages().size();

            page.bringToFront();

            page.navigate(
                    urlCompra,
                    new Page.NavigateOptions().setTimeout(120000)
            );

            page.waitForTimeout(10000);

            Page paginaCompra = localizarPaginaComprasNet(context, page, totalAbasAntes);
            paginaCompra.bringToFront();

            System.out.println("URL ativa após abrir compra: " + paginaCompra.url());

            aguardarTelaComprasCarregada(paginaCompra, 90000);

            System.out.println("Detalhe carregado.");

            return paginaCompra;

        } catch (Exception e) {
            tirarPrintDebug(page, "erro_url_direta_oportunidade_" + oportunidade.getId() + ".png");
            throw new RuntimeException("Erro ao abrir oportunidade por URL direta: " + e.getMessage(), e);
        }
    }

    private static void fecharChatSeAberto(Page page) {
        try {
            Locator fecharChat = page.locator(
                    "button[aria-label='Close'], " +
                            "button.p-dialog-header-icon, " +
                            ".p-dialog-header-close"
            );

            if (fecharChat.count() > 0) {
                fecharChat.first().click();
                page.waitForTimeout(3000);
                System.out.println("Chat fechado.");
            }
        } catch (Exception e) {
            System.out.println("Não foi possível fechar chat, seguindo: " + e.getMessage());
        }
    }

    // ============================================================
    // CHAT
    // ============================================================

    private static void abrirChatComSeguranca(Page page) {
        try {
            System.out.println("Tentando abrir chat...");

            page.bringToFront();
            aguardarTelaComprasCarregada(page, 90000);

            Locator chatBtn = page.locator("app-botao-mensagens-da-compra button").last();

            if (chatBtn.count() == 0) {
                chatBtn = page.locator(
                        "button:has-text('Mensagens da compra'), " +
                                "button:has-text('Mensagens'), " +
                                "button:has-text('Mensagem'), " +
                                "button[title*='mensagem'], " +
                                "button[aria-label*='mensagem'], " +
                                "button[title*='Mensagem'], " +
                                "button[aria-label*='Mensagem']"
                ).last();
            }

            chatBtn.waitFor(
                    new Locator.WaitForOptions()
                            .setTimeout(90000)
                            .setState(WaitForSelectorState.VISIBLE)
            );

            chatBtn.scrollIntoViewIfNeeded();
            page.waitForTimeout(1000);

            chatBtn.click(new Locator.ClickOptions().setTimeout(30000));

            page.waitForTimeout(10000);

            System.out.println("Chat aberto.");

        } catch (Exception e) {
            tirarPrintDebug(page, "erro_abrir_chat.png");
            throw new RuntimeException("Não foi possível abrir o chat: " + e.getMessage(), e);
        }
    }

    private static void capturarMensagensPaginadasComInteracao(
            Oportunidade oportunidade,
            Page page,
            ObjectMapper mapper
    ) {
        try {
            System.out.println("Iniciando captura paginada do chat. Oportunidade: " + oportunidade.getId());

            page.bringToFront();
            page.waitForTimeout(3000);

            if (ULTIMO_JSON_CHAT != null && !ULTIMO_JSON_CHAT.trim().isEmpty()) {
                System.out.println("Processando página 1 pelo JSON global.");
                processarMensagens(
                        oportunidade.getCliente(),
                        oportunidade.getUasg(),
                        oportunidade.getNumeroPregao(),
                        ULTIMO_JSON_CHAT,
                        mapper,
                        oportunidade
                );
            } else {
                System.out.println("Página 1 ainda sem JSON. Aguardando mais um pouco...");
                aguardarJsonChatAposClique(page, 1, 30000);

                if (ULTIMO_JSON_CHAT != null && !ULTIMO_JSON_CHAT.trim().isEmpty()) {
                    processarMensagens(
                            oportunidade.getCliente(),
                            oportunidade.getUasg(),
                            oportunidade.getNumeroPregao(),
                            ULTIMO_JSON_CHAT,
                            mapper,
                            oportunidade
                    );
                } else {
                    System.out.println("Não foi possível processar página 1 pelo JSON global.");
                    tirarPrintDebug(page, "chat_pagina_1_sem_json_" + oportunidade.getId() + ".png");
                }
            }

            int totalPages = descobrirTotalPaginasChat(page);
            System.out.println("Total de páginas detectadas no chat: " + totalPages);

            if (totalPages <= 1) {
                System.out.println("Chat possui apenas uma página ou paginação não foi detectada.");
                return;
            }

            for (int i = 2; i <= totalPages; i++) {
                System.out.println("Valor mensagemExiste: " + mensagemExiste);
                System.out.println("Página: " + i + " | oportunidade: " + oportunidade.getId());

                if (mensagemExiste) {
                    System.out.println("Mensagem já existente no banco. Saindo da paginação. Oportunidade: " + oportunidade.getId());
                    mensagemExiste = false;
                    break;
                }

                try {
                    System.out.println("Indo para página " + i + " do chat.");

                    ULTIMO_JSON_CHAT = "";

                    boolean clicou = clicarProximaPaginaChatComoUsuario(page, i);

                    if (!clicou) {
                        System.out.println("Não conseguiu clicar para chegar na página " + i + ".");
                        tirarPrintDebug(page, "pagina_chat_nao_clicada_" + i + "_op_" + oportunidade.getId() + ".png");
                        continue;
                    }

                    page.waitForTimeout(3000);

                    if (captchaChatApareceu(page)) {
                        System.out.println("CAPTCHA DETECTADO NA PÁGINA " + i + ". Encerrando paginação desta oportunidade sem insistir.");
                        tirarPrintDebug(page, "captcha_chat_pagina_" + i + "_op_" + oportunidade.getId() + ".png");
                        break;
                    }

                    aguardarJsonChatAposClique(page, i, 30000);

                    if (captchaChatApareceu(page)) {
                        System.out.println("CAPTCHA DETECTADO APÓS AGUARDAR JSON NA PÁGINA " + i + ". Encerrando paginação desta oportunidade.");
                        tirarPrintDebug(page, "captcha_chat_pos_json_pagina_" + i + "_op_" + oportunidade.getId() + ".png");
                        break;
                    }

                    if (ULTIMO_JSON_CHAT != null && !ULTIMO_JSON_CHAT.trim().isEmpty()) {
                        System.out.println("JSON recebido na página " + i + ".");

                        processarMensagens(
                                oportunidade.getCliente(),
                                oportunidade.getUasg(),
                                oportunidade.getNumeroPregao(),
                                ULTIMO_JSON_CHAT,
                                mapper,
                                oportunidade
                        );
                    } else {
                        System.out.println("JSON não recebido na página " + i + ".");
                        tirarPrintDebug(page, "json_nao_recebido_pagina_" + i + "_op_" + oportunidade.getId() + ".png");
                    }

                    page.waitForTimeout(3500);

                } catch (Exception e) {
                    System.out.println("Erro na página " + i + ": " + e.getMessage());
                    tirarPrintDebug(page, "erro_chat_pagina_" + i + "_op_" + oportunidade.getId() + ".png");
                    e.printStackTrace();
                }
            }

        } catch (Exception e) {
            System.out.println("Erro geral na captura paginada: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static int descobrirTotalPaginasChat(Page page) {
        try {
            int maior = 1;

            Locator botoes = page.locator(
                    "button[data-pc-section='page'], " +
                            "button.p-paginator-page, " +
                            ".p-paginator button"
            );

            int count = botoes.count();

            for (int i = 0; i < count; i++) {
                try {
                    String texto = botoes.nth(i).innerText();

                    if (texto == null) {
                        continue;
                    }

                    texto = texto.trim();

                    if (texto.matches("\\d+")) {
                        int n = Integer.parseInt(texto);
                        if (n > maior) {
                            maior = n;
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            return maior;

        } catch (Exception e) {
            System.out.println("Não foi possível descobrir total de páginas do chat: " + e.getMessage());
            return 1;
        }
    }

    private static boolean clicarProximaPaginaChatComoUsuario(Page page, int numeroPaginaEsperada) {
        try {
            page.bringToFront();
            page.waitForTimeout(2500);

            // A estratégia mais estável é clicar em PRÓXIMA PÁGINA,
            // não diretamente no número da página.
            try {
                Locator area = page.locator(".p-dialog, .p-dialog-content, body").first();
                area.click(new Locator.ClickOptions().setTimeout(5000));
            } catch (Exception ignored) {
            }

            page.waitForTimeout(1200);

            Locator proxima = page.locator(
                    "button.p-paginator-next:not([disabled]), " +
                            "button[aria-label='Next Page']:not([disabled]), " +
                            "button[aria-label='Próxima página']:not([disabled]), " +
                            "button[aria-label='Proxima pagina']:not([disabled]), " +
                            ".p-paginator-next:not(.p-disabled)"
            );

            if (proxima.count() == 0) {
                System.out.println("Botão próxima página não encontrado. Tentando fallback pelo número " + numeroPaginaEsperada);
                return clicarPaginaChatComoHumanoFallback(page, numeroPaginaEsperada);
            }

            Locator alvo = proxima.first();
            alvo.scrollIntoViewIfNeeded();
            page.waitForTimeout(1000);

            try {
                alvo.hover(new Locator.HoverOptions().setTimeout(10000));
            } catch (Exception ignored) {
            }

            page.waitForTimeout(800);

            BoundingBox box = alvo.boundingBox();

            if (box != null) {
                double x = box.x + (box.width / 2);
                double y = box.y + (box.height / 2);

                page.mouse().move(x - 8, y - 4);
                page.waitForTimeout(250);
                page.mouse().move(x, y);
                page.waitForTimeout(700);
                page.mouse().down();
                page.waitForTimeout(180);
                page.mouse().up();

                System.out.println("Clique no botão PRÓXIMA PÁGINA realizado para chegar na página " + numeroPaginaEsperada);
            } else {
                alvo.click(new Locator.ClickOptions().setTimeout(15000));
                System.out.println("Clique normal no botão PRÓXIMA PÁGINA realizado para chegar na página " + numeroPaginaEsperada);
            }

            page.waitForTimeout(7000);
            return true;

        } catch (Exception e) {
            System.out.println("Erro ao clicar em próxima página para chegar na página " + numeroPaginaEsperada + ": " + e.getMessage());
            return false;
        }
    }

    private static boolean clicarPaginaChatComoHumanoFallback(Page page, int numeroPagina) {
        try {
            page.bringToFront();
            page.waitForTimeout(1500);

            Locator botao = localizarBotaoPaginaChat(page, numeroPagina);

            if (botao == null || botao.count() == 0) {
                System.out.println("Botão da página " + numeroPagina + " não localizado.");
                return false;
            }

            Locator alvo = botao.first();
            alvo.scrollIntoViewIfNeeded();
            page.waitForTimeout(1000);

            try {
                alvo.hover(new Locator.HoverOptions().setTimeout(10000));
            } catch (Exception ignored) {
            }

            BoundingBox box = alvo.boundingBox();

            if (box == null) {
                alvo.click(new Locator.ClickOptions().setTimeout(10000));
                return true;
            }

            double x = box.x + (box.width / 2);
            double y = box.y + (box.height / 2);

            page.mouse().move(x - 6, y - 3);
            page.waitForTimeout(250);
            page.mouse().move(x, y);
            page.waitForTimeout(700);
            page.mouse().down();
            page.waitForTimeout(150);
            page.mouse().up();

            System.out.println("Clique fallback realizado na página " + numeroPagina + " em x=" + x + " y=" + y);
            page.waitForTimeout(5000);

            return true;

        } catch (Exception e) {
            System.out.println("Erro no clique fallback da página " + numeroPagina + ": " + e.getMessage());
            return false;
        }
    }

    private static Locator localizarBotaoPaginaChat(Page page, int numeroPagina) {
        String n = String.valueOf(numeroPagina);

        String[] seletores = new String[]{
                "button.p-paginator-page:has-text('" + n + "')",
                "button[data-pc-section='page']:has-text('" + n + "')",
                ".p-paginator button:has-text('" + n + "')",
                "p-paginator button:has-text('" + n + "')",
                "button[aria-label='Página " + n + "']",
                "button[aria-label='Pagina " + n + "']",
                "button[aria-label='Page " + n + "']"
        };

        for (String seletor : seletores) {
            try {
                Locator loc = page.locator(seletor);
                if (loc.count() > 0) {
                    return loc;
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private static void aguardarJsonChatAposClique(Page page, int pagina, int timeoutMs) {
        long inicio = System.currentTimeMillis();

        while ((System.currentTimeMillis() - inicio) < timeoutMs) {
            try {
                if (ULTIMO_JSON_CHAT != null && !ULTIMO_JSON_CHAT.trim().isEmpty()) {
                    return;
                }

                if (captchaChatApareceu(page)) {
                    return;
                }

                page.waitForTimeout(1000);
            } catch (Exception ignored) {
            }
        }

        System.out.println("Timeout aguardando JSON do chat na página " + pagina);
    }

    private static boolean captchaChatApareceu(Page page) {
        try {
            String texto = page.locator("body").innerText().toLowerCase();

            return texto.contains("não foi possível realizar a validação do captcha")
                    || texto.contains("nao foi possivel realizar a validacao do captcha")
                    || texto.contains("validação do captcha")
                    || texto.contains("validacao do captcha");
        } catch (Exception e) {
            return false;
        }
    }

    // ============================================================
    // PROCESSAR JSON
    // ============================================================

    private static void processarMensagens(
            String cliente,
            String uasg,
            String pregao,
            String body,
            ObjectMapper mapper,
            Oportunidade oportunidade
    ) {
        try {
            JsonNode root = mapper.readTree(body);

            JsonNode lista = root.isArray()
                    ? root
                    : root.path("content");

            if (lista == null || !lista.isArray()) {
                System.out.println("LISTA VAZIA");
                return;
            }

            System.out.println("TOTAL MSGS: " + lista.size());

            for (JsonNode msg : lista) {
                String id = getText(msg, "id", "uuid", "chaveMensagemNaOrigem");

                if (id == null || id.isEmpty()) {
                    continue;
                }

                String texto = getText(msg, "texto", "mensagem", "conteudo");
                String remetente = getText(msg, "remetente", "tipoRemetente");
                String data = getText(msg, "dataHora", "dataEnvio");

                Integer item = null;
                JsonNode itemNode = msg.path("numeroItem");

                if (!itemNode.isMissingNode() && !itemNode.isNull()) {
                    item = itemNode.asInt();
                }

                boolean inserido = salvarPregaoChat(id, texto, remetente, data, item, oportunidade);

                if (!inserido) {
                    System.out.println("Mensagem já existente no banco, ignorando. Oportunidade: " + oportunidade.getId());
                    break;
                } else if (countNovaMensagem == 1) {
                    dispararWhatsAppNovaMsg(oportunidade);
                    System.out.println("Nova mensagem enviada por WhatsApp. Oportunidade: " + oportunidade.getId());
                }

                if (textoMencionaCliente(texto, oportunidade)) {
                    dispararWhatsApp(oportunidade);
                }
            }

        } catch (Exception e) {
            System.out.println("ERRO PROCESSANDO JSON");
            e.printStackTrace();
        }
    }

    // ============================================================
    // BANCO H2 LOCAL DE TESTE
    // ============================================================

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

    // ============================================================
    // BANCO MYSQL / OPORTUNIDADES
    // ============================================================

    private static List<Oportunidade> buscarOportunidadesDoBanco() {
        List<Oportunidade> lista = new ArrayList<>();

        String sql =
                "SELECT O.ID, O.CLIENTE_ID, CLI.CNPJ, CLI.RAZAO_SOCIAL, CLI.FANTASIA, "
                        + "       O.MODALIDADE_ID, O.NUMERO_EDITAL, O.UASG_OC, O.DATA_CERTAME, "
                        + "       O.STATUS, CLI.FANTASIA AS CLIENTE, O.UASG_OC AS UASG, "
                        + "       O.NUMERO_EDITAL AS NUMERO_PREGAO "
                        + "FROM vista.VISTA_OPORTUNIDADE O "
                        + "JOIN vista.VISTA_CLIENTE CLI ON CLI.ID = O.CLIENTE_ID "
                        + "WHERE O.PLATAFORMA_ID IN (1, 35) "
                        + "  AND O.UASG_OC IS NOT NULL "
                        + "  AND O.ID IN (9377,9383,9381,9379) "
                        + "  AND O.STATUS NOT IN ('F', 'C', 'H', 'S') "
                        + "ORDER BY O.ID DESC";

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            conn = getConexaoMySQL();
            ps = conn.prepareStatement(sql);
            rs = ps.executeQuery();

            while (rs.next()) {
                Long id = rs.getLong("ID");
                Long clienteId = rs.getLong("CLIENTE_ID");
                String cnpj = rs.getString("CNPJ");
                String razaoSocial = rs.getString("RAZAO_SOCIAL");
                String fantasia = rs.getString("FANTASIA");
                Long modalidade = rs.getLong("MODALIDADE_ID");
                String numeroEdital = rs.getString("NUMERO_EDITAL");
                String uasgOc = rs.getString("UASG_OC");
                String status = rs.getString("STATUS");

                LocalDate dataCertame = null;
                java.sql.Date sqlDate = rs.getDate("DATA_CERTAME");

                if (sqlDate != null) {
                    dataCertame = sqlDate.toLocalDate();
                }

                String cliente = (fantasia != null && !fantasia.trim().isEmpty())
                        ? fantasia.trim()
                        : razaoSocial;

                String numeroPregao = formatarNumeroPregao(numeroEdital);

                lista.add(
                        new Oportunidade(
                                id,
                                clienteId,
                                cnpj,
                                razaoSocial,
                                fantasia,
                                modalidade,
                                numeroEdital,
                                uasgOc,
                                dataCertame,
                                status,
                                cliente,
                                uasgOc,
                                numeroPregao
                        )
                );
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

        String ano = apenasNumeros.substring(apenasNumeros.length() - 4);
        String numero = apenasNumeros.substring(0, apenasNumeros.length() - 4);

        return numero + "/" + ano;
    }

    private static boolean salvarPregaoChat(
            String chaveMensagem,
            String texto,
            String remetente,
            String dataStr,
            Integer itemLote,
            Oportunidade oportunidade
    ) {
        Connection conn = null;
        PreparedStatement psCheck = null;
        PreparedStatement psInsert = null;
        ResultSet rs = null;

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

            System.out.println("SALVO: [" + remetente + "] " + (texto != null ? texto.substring(0, Math.min(60, texto.length())) : ""));
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

    private static Connection getConexaoMySQL() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    private static void carregarConfigBanco() throws Exception {
        Properties props = carregarProperties();

        DB_URL = props.getProperty("spring.datasource.url");
        DB_USER = props.getProperty("spring.datasource.username");
        DB_PASSWORD = props.getProperty("spring.datasource.password");

        CPF = primeiroValorNaoVazio(
                System.getenv("COMPRASNET_CPF"),
                props.getProperty("comprasnet.cpf"),
                props.getProperty("app.comprasnet.cpf")
        );

        SENHA = primeiroValorNaoVazio(
                System.getenv("COMPRASNET_SENHA"),
                props.getProperty("comprasnet.senha"),
                props.getProperty("app.comprasnet.senha")
        );

        if (DB_URL == null || DB_URL.trim().isEmpty()) {
            throw new RuntimeException("Propriedade spring.datasource.url não configurada.");
        }

        if (CPF == null || CPF.trim().isEmpty()) {
            throw new RuntimeException("CPF do ComprasNet não configurado. Configure comprasnet.cpf ou variável COMPRASNET_CPF.");
        }

        if (SENHA == null || SENHA.trim().isEmpty()) {
            throw new RuntimeException("Senha do ComprasNet não configurada. Configure comprasnet.senha ou variável COMPRASNET_SENHA.");
        }

        System.out.println("Banco configurado: " + DB_URL);
    }

    private static String primeiroValorNaoVazio(String... valores) {
        if (valores == null) {
            return null;
        }

        for (String v : valores) {
            if (v != null && !v.trim().isEmpty()) {
                return v.trim();
            }
        }

        return null;
    }

    private static Properties carregarProperties() throws Exception {
        Properties props = new Properties();

        InputStream is = RPAChatComprasNetFullTesteV2.class
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
            throw new RuntimeException("application.properties não encontrado.");
        }

        return props;
    }

    private static void fechar(ResultSet rs, PreparedStatement ps, Connection conn) {
        if (rs != null) {
            try {
                rs.close();
            } catch (Exception ignored) {
            }
        }

        if (ps != null) {
            try {
                ps.close();
            } catch (Exception ignored) {
            }
        }

        if (conn != null) {
            try {
                conn.close();
            } catch (Exception ignored) {
            }
        }
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private static String getText(JsonNode node, String... campos) {
        for (String c : campos) {
            JsonNode n = node.get(c);

            if (n != null && !n.isNull()) {
                return n.asText();
            }
        }

        return "";
    }

    private static String truncar(String s, int max) {
        if (s == null) {
            return null;
        }

        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String montarMensagemChat(String remetente, String texto) {
        String r = (remetente != null && !remetente.isEmpty()) ? remetente : "N/A";
        String t = (texto != null && !texto.isEmpty()) ? texto : "";
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
            } catch (DateTimeParseException ignored) {
            }
        }

        return null;
    }

    private static void tirarPrintDebug(Page page, String nomeArquivo) {
        try {
            page.screenshot(
                    new Page.ScreenshotOptions()
                            .setPath(Paths.get(nomeArquivo))
                            .setFullPage(true)
            );

            System.out.println("Print salvo para debug: " + nomeArquivo);
        } catch (Exception e) {
            System.out.println("Não foi possível salvar print debug: " + e.getMessage());
        }
    }

    private static void deletarSessao() {
        try {
            File sessao = new File(SESSION_FILE);

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

    // ============================================================
    // WHATSAPP
    // ============================================================

    private static void dispararWhatsAppNovaMsg(Oportunidade oportunidade) {
        String mensagem =
                "AVISO: Há novas mensagens no chat do edital "
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
        String mensagem =
                "URGENTE: Houve alterações no chat do edital "
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

    private static boolean textoMencionaCliente(String texto, Oportunidade oportunidade) {
        if (texto == null || texto.trim().isEmpty()) {
            return false;
        }

        String textoUpper = texto.toUpperCase().trim();

        String fantasia = oportunidade.getFantasia();
        if (fantasia != null && !fantasia.trim().isEmpty()) {
            if (textoUpper.contains(fantasia.toUpperCase().trim())) {
                return true;
            }
        }

        String razao = oportunidade.getRazaoSocial();
        if (razao != null && !razao.trim().isEmpty()) {
            String razaoUpper = razao.toUpperCase().trim();

            if (textoUpper.contains(razaoUpper)) {
                return true;
            }

            String[] palavras = razaoUpper.split("\s+");
            if (palavras.length > 0 && palavras[0].length() >= 4) {
                if (textoUpper.contains(palavras[0])) {
                    return true;
                }
            }
        }

        String cliente = oportunidade.getCliente();
        if (cliente != null && !cliente.trim().isEmpty() && cliente.length() >= 4) {
            if (textoUpper.contains(cliente.toUpperCase().trim())) {
                return true;
            }
        }

        String cnpj = oportunidade.getCnpj();
        if (cnpj != null && !cnpj.trim().isEmpty()) {
            String cnpjNumeros = cnpj.replaceAll("[^0-9]", "");

            if (!cnpjNumeros.isEmpty() && textoUpper.contains(cnpjNumeros)) {
                return true;
            }

            if (cnpjNumeros.length() == 14) {
                String cnpjMascarado =
                        cnpjNumeros.substring(0, 2)
                                + "."
                                + cnpjNumeros.substring(2, 5)
                                + "."
                                + cnpjNumeros.substring(5, 8)
                                + "/"
                                + cnpjNumeros.substring(8, 12)
                                + "-"
                                + cnpjNumeros.substring(12);

                if (textoUpper.contains(cnpjMascarado)) {
                    return true;
                }
            }
        }

        return false;
    }
    private static void validaCapthaAposLoginFalho(BrowserContext context, Page page) {
        System.out.println("🔐 Validando presença de hCaptcha após falha no login...");
        // Aguarda o captcha renderizar caso tenha acabado de aparecer
        page.waitForTimeout(6000);

        if (hCaptchaVisivel(page)) {

            System.out.println("🔐 hCaptcha detectado! Tentando resolver via TwoCaptcha...");

            boolean resolveu = resolverHCaptcha(page);

            if (resolveu) {
                System.out.println("✅ hCaptcha resolvido. Continuando fluxo de login...");

                try {
                    // Se ainda está na tela do CPF, clica em Continuar
                    Locator btnContinuar = page.locator("#enter-account-id");
                    if (btnContinuar.count() > 0 && btnContinuar.isEnabled()) {
                        System.out.println("🖱️ Clicando em Continuar após captcha...");
                        btnContinuar.click();
                        page.waitForTimeout(5000);
                    }

                    // Se avançou para tela de senha, preenche e submete
                    Locator campoSenha = page.locator("input[name='password'], #password");
                    if (campoSenha.count() > 0) {
                        System.out.println("🔑 Preenchendo senha...");
                        campoSenha.fill(SENHA);
                        page.locator("#submit-button").click();
                        page.waitForTimeout(10000);

                        // Salva sessão após login bem-sucedido
                        context.storageState(
                                new BrowserContext.StorageStateOptions()
                                        .setPath(Paths.get(SESSION_FILE))
                        );
                        System.out.println("✅ Sessão salva após resolver captcha");
                    }

                    // Seleção de empresa, se necessário
                    if (page.locator("text=MEDIPHACOS INDUSTRIAS MEDICAS S/A").count() > 0) {
                        page.locator("text=MEDIPHACOS INDUSTRIAS MEDICAS S/A").first().click();
                        page.locator("input[type='submit'], button:has-text('Confirmar')").click();
                        page.waitForTimeout(8000);
                    }

                } catch (Exception ex) {
                    System.out.println("⚠️ Erro ao continuar login após captcha: " + ex.getMessage());
                    tirarPrintDebug(page, "erro_login_pos_captcha.png");
                }

            } else {
                System.out.println("❌ Não foi possível resolver o hCaptcha via TwoCaptcha.");
                tirarPrintDebug(page, "erro_captcha_nao_resolvido.png");
                throw new RuntimeException("Falha ao resolver hCaptcha via TwoCaptcha. Processo abortado.");
            }

        } else {
            // Sem captcha visível — repassa a exceção original
            System.out.println("⚠️ hCaptcha não detectado. Repassando erro original.");
            throw new RuntimeException("Falha ao resolver hCaptcha via TwoCaptcha. Processo abortado.");
        }
    }
    private static boolean hCaptchaVisivel(Page page) {
        try {
            return page.locator("iframe[src*='hcaptcha.com']").count() > 0;
        } catch (Exception e) {
            System.out.println("Erro ao verificar hCaptcha: " + e.getMessage());
            return false;
        }
    }
    private static boolean resolverHCaptcha(Page page) {
        try {
            System.out.println("🔐 Iniciando resolução hCaptcha via TwoCaptcha...");

            String pageUrl = page.url();

            // -----------------------------------------
            // 1. Envia o captcha para o TwoCaptcha
            // -----------------------------------------
            String submitUrl = "https://2captcha.com/in.php"
                    + "?key="     + TWOCAPTCHA_API_KEY
                    + "&method=hcaptcha"
                    + "&sitekey=" + HCAPTCHA_SITEKEY
                    + "&pageurl=" + pageUrl
                    + "&json=1";

            String submitResponse = fazerGetHttp(submitUrl);
            System.out.println("TwoCaptcha submit response: " + submitResponse);

            ObjectMapper om  = new ObjectMapper();
            JsonNode submitJson = om.readTree(submitResponse);

            if (submitJson.path("status").asInt() != 1) {
                System.out.println("❌ Erro ao enviar captcha para TwoCaptcha: " + submitResponse);
                return false;
            }

            String captchaId = submitJson.path("request").asText();
            System.out.println("📨 Captcha enviado ao TwoCaptcha. ID: " + captchaId);

            // -----------------------------------------
            // 2. Polling para buscar o token resolvido
            //    (máx 120s = 24 tentativas × 5s)
            // -----------------------------------------
            String token    = null;
            String resultUrl = "https://2captcha.com/res.php"
                    + "?key="    + TWOCAPTCHA_API_KEY
                    + "&action=get"
                    + "&id="     + captchaId
                    + "&json=1";

            for (int tentativa = 0; tentativa < 24; tentativa++) {

                Thread.sleep(5000);

                String resResponse = fazerGetHttp(resultUrl);
                JsonNode resJson   = om.readTree(resResponse);

                System.out.println("TwoCaptcha polling [" + tentativa + "]: " + resResponse);

                if (resJson.path("status").asInt() == 1) {
                    token = resJson.path("request").asText();
                    System.out.println("✅ Token hCaptcha obtido com sucesso!");
                    break;
                }

                String requestText = resJson.path("request").asText();

                // CAPCHA_NOT_READY = ainda processando, aguarda próxima tentativa
                if (!"CAPCHA_NOT_READY".equals(requestText)) {
                    System.out.println("❌ Erro inesperado no TwoCaptcha: " + resResponse);
                    return false;
                }
            }

            if (token == null) {
                System.out.println("❌ Timeout aguardando resolução do TwoCaptcha (120s)");
                return false;
            }

            // -----------------------------------------
            // 3. Injeta o token na página via JavaScript
            // -----------------------------------------
            final String tokenFinal = token;

            page.evaluate("""
                (token) => {
                    // Injeta no textarea padrão do hCaptcha (campo oculto)
                    document.querySelectorAll('textarea[name="h-captcha-response"]')
                            .forEach(el => { el.value = token; });

                    // Injeta também no campo g-recaptcha-response caso exista compatibilidade
                    document.querySelectorAll('textarea[name="g-recaptcha-response"]')
                            .forEach(el => { el.value = token; });

                    // Tenta injetar no formulário principal
                    const form = document.getElementById('loginData');
                    if (form) {
                        const campos = form.querySelectorAll(
                            'textarea[name="h-captcha-response"], textarea[name="g-recaptcha-response"]'
                        );
                        campos.forEach(el => { el.value = token; });
                    }

                    // Tenta disparar o callback registrado pelo hCaptcha
                    try {
                        if (window.hcaptcha && typeof window.hcaptcha.execute === 'function') {
                            // apenas sinaliza que o token está disponível
                            console.log('hcaptcha token injetado via JS');
                        }
                    } catch(e) {
                        console.warn('hcaptcha inject warn:', e);
                    }
                }
            """, tokenFinal);

            System.out.println("💉 Token hCaptcha injetado na página");
            page.waitForTimeout(2000);
            return true;

        } catch (Exception e) {
            System.out.println("❌ Erro ao resolver hCaptcha: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    private static String fazerGetHttp(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        byte[] bytes = conn.getInputStream().readAllBytes();
        conn.disconnect();
        return new String(bytes);
    }
}
