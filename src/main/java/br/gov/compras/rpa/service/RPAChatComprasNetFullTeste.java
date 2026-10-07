package br.gov.compras.rpa.service;

import br.gov.compras.rpa.model.Oportunidade;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class RPAChatComprasNetFullTeste {

    private static String CPF;
    private static String SENHA;

    private static final String USER_DATA_DIR = "playwright-profile-comprasnet";
    private static final String SESSION_FILE = "session.json";

    private static String DB_URL;
    private static String DB_USER;
    private static String DB_PASSWORD;

    private static final String WHATS_NUMERO = "5534996461183";
    private static final String WHATS_GRUPO = "ChatComprasGov";

    private static volatile String ULTIMO_JSON_CHAT = "";
    private static boolean novaMensagem = false;

    public static void main(String[] args) {

        Playwright playwright = null;
        BrowserContext context = null;

        try {
            carregarConfigBanco();
            carregarConfigLogin();
            iniciarConsoleH2SePossivel();

            List<Oportunidade> oportunidades = buscarOportunidadesDoBanco();

            if (oportunidades.isEmpty()) {
                System.out.println("Nenhuma oportunidade encontrada no banco. Encerrando.");
                return;
            }

            System.out.println("Total de oportunidades carregadas: " + oportunidades.size());

            playwright = Playwright.create();

            context = playwright.firefox().launchPersistentContext(
                    Paths.get(USER_DATA_DIR),
                    new BrowserType.LaunchPersistentContextOptions()
                            .setHeadless(false)
                            .setSlowMo(300)
                            .setViewportSize(1366, 768)
            );

            System.out.println("Perfil persistente carregado em: " + USER_DATA_DIR);

            registrarListenerGlobalChat(context);

            Page page;

            if (context.pages() != null && !context.pages().isEmpty()) {
                page = context.pages().get(0);
            } else {
                page = context.newPage();
            }

            realizarLogin(context, page);
            salvarSessaoDebug(context);

            page = abrirPortalComprasNet(context, page);
            salvarSessaoDebug(context);

            ObjectMapper mapper = new ObjectMapper();

            for (int indice = 0; indice < oportunidades.size(); indice++) {

                novaMensagem = false;
                ULTIMO_JSON_CHAT = "";

                Oportunidade oportunidade = oportunidades.get(indice);

                try {
                    System.out.println("\n==================================");
                    System.out.println("PROCESSANDO OPORTUNIDADE");
                    System.out.println("INDICE: " + indice);
                    System.out.println("CLIENTE: " + oportunidade.getCliente());
                    System.out.println("UASG: " + oportunidade.getUasg());
                    System.out.println("PREGAO: " + oportunidade.getNumeroPregao());
                    System.out.println("OPORTUNIDADE ID: " + oportunidade.getId());
                    System.out.println("URL ATUAL ANTES: " + page.url());
                    System.out.println("==================================");

                    page = garantirPaginaPrincipal(context, page);

                    if (indice == 0) {
                        abrirPrimeiraOportunidadePorCard(page, oportunidade);
                    } else {
                        abrirOportunidadePorUrlDireta(context, page, oportunidade);
                    }

                    validarSessaoNaoCaiu(page, "apos_abrir_oportunidade_" + oportunidade.getId());

                    page.waitForTimeout(8000);

                    abrirChat(page);

                    capturarMensagensPaginadasComInteracao(
                            oportunidade,
                            page,
                            mapper
                    );

                    dispararWhatsAppNovaMsg(oportunidade, novaMensagem);

                    salvarSessaoDebug(context);

                } catch (Exception e) {
                    System.out.println("ERRO OPORTUNIDADE");
                    e.printStackTrace();
                    tirarPrintDebug(page, "erro_oportunidade_" + indice + ".png");
                }
            }

            System.out.println("\nFINALIZADO");
            System.out.println("Perfil persistente mantido em: " + USER_DATA_DIR);
            System.out.println("Session debug mantida em: " + SESSION_FILE);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (context != null) {
                    context.close();
                }
            } catch (Exception e) {
                System.out.println("Erro ao fechar contexto: " + e.getMessage());
            }

            try {
                if (playwright != null) {
                    playwright.close();
                }
            } catch (Exception e) {
                System.out.println("Erro ao fechar playwright: " + e.getMessage());
            }
        }
    }

    private static void iniciarConsoleH2SePossivel() {
        try {
            org.h2.tools.Server
                    .createWebServer("-web", "-webPort", "8082")
                    .start();

            System.out.println("Console H2 iniciado na porta 8082.");
        } catch (Exception e) {
            System.out.println("Nao foi possivel iniciar console H2 ou ele ja esta ativo: " + e.getMessage());
        }
    }

    private static void registrarListenerGlobalChat(BrowserContext context) {
        context.onResponse(response -> {
            try {
                if (!response.url().contains("/v2/chat")) {
                    return;
                }

                System.out.println("\n==============================");
                System.out.println("CHAT RESPONSE GLOBAL");
                System.out.println("==============================");
                System.out.println("URL: " + response.url());
                System.out.println("STATUS: " + response.status());

                if (response.status() == 204) {
                    System.out.println("BODY VAZIO / STATUS 204");
                    return;
                }

                String body = response.text();

                if (body == null || body.trim().isEmpty()) {
                    System.out.println("BODY VAZIO");
                    return;
                }

                ULTIMO_JSON_CHAT = body;
                System.out.println("JSON RECEBIDO GLOBAL");

            } catch (Exception e) {
                System.out.println("Erro no listener global do chat: " + e.getMessage());
            }
        });
    }

    private static void realizarLogin(BrowserContext context, Page page) throws Exception {
        System.out.println("VALIDANDO LOGIN / SESSAO");

        page.navigate(
                "https://www.comprasnet.gov.br/seguro/loginFornecedorSelecEmpresa.asp",
                new Page.NavigateOptions().setTimeout(120000)
        );

        page.waitForTimeout(8000);

        System.out.println("URL LOGIN: " + page.url());

        if (estaNaTelaEmpresa(page)) {
            System.out.println("Tela de empresa encontrada.");
            selecionarEmpresaSeAparecer(context, page);
            salvarSessaoDebug(context);
            return;
        }

        if (estaDentroComprasNet(page)) {
            System.out.println("Sessao ja esta dentro do ComprasNet.");
            salvarSessaoDebug(context);
            return;
        }

        if (page.locator("text=Entrar com Gov.br").count() > 0) {
            System.out.println("Botao Gov.br encontrado. Clicando...");
            page.locator("text=Entrar com Gov.br").first().click();
            page.waitForTimeout(8000);
        }

        if (estaNaTelaEmpresa(page)) {
            selecionarEmpresaSeAparecer(context, page);
            salvarSessaoDebug(context);
            return;
        }

        if (estaDentroComprasNet(page)) {
            salvarSessaoDebug(context);
            return;
        }

        if (page.locator("input[name='accountId'], #accountId").count() > 0) {
            System.out.println("Campo CPF encontrado. Preenchendo CPF...");
            page.locator("input[name='accountId'], #accountId").first().fill(CPF);

            if (page.locator("#enter-account-id").count() > 0) {
                page.locator("#enter-account-id").first().click();
            } else {
                page.keyboard().press("Enter");
            }

            page.waitForTimeout(8000);
        }

        if (exigeIntervencaoManualGovBr(page)) {
            tirarPrintDebug(page, "govbr_pediu_captcha_ou_validacao.png");
            throw new RuntimeException(
                    "Gov.br pediu captcha/validacao. Para execucao automatica, o perfil persistente precisa estar autenticado. " +
                            "Print salvo: govbr_pediu_captcha_ou_validacao.png"
            );
        }

        if (page.locator("input[name='password'], #password, input[type='password']").count() > 0) {
            System.out.println("Campo senha encontrado. Preenchendo senha...");
            page.locator("input[name='password'], #password, input[type='password']").first().fill(SENHA);

            if (page.locator("#submit-button, button[type='submit'], input[type='submit']").count() > 0) {
                page.locator("#submit-button, button[type='submit'], input[type='submit']").first().click();
            } else {
                page.keyboard().press("Enter");
            }

            page.waitForTimeout(12000);
        }

        if (estaNaTelaEmpresa(page)) {
            selecionarEmpresaSeAparecer(context, page);
            salvarSessaoDebug(context);
            return;
        }

        if (estaDentroComprasNet(page)) {
            salvarSessaoDebug(context);
            return;
        }

        tirarPrintDebug(page, "login_nao_confirmado.png");
        throw new RuntimeException("Nao foi possivel confirmar login. Print salvo: login_nao_confirmado.png. URL: " + page.url());
    }

    private static boolean estaNaTelaEmpresa(Page page) {
        try {
            return page.locator("text=MEDIPHACOS INDUSTRIAS MEDICAS S/A").count() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean estaDentroComprasNet(Page page) {
        try {
            String url = page.url();

            if (url != null && url.contains("comprasnet-web/seguro")) return true;
            if (url != null && url.contains("assinadas/dispensa_eletronica.asp")) return true;
            if (page.locator("text=Acompanhamento de Compra").count() > 0) return true;
            if (page.locator("app-botao-mensagens-da-compra").count() > 0) return true;
            if (page.locator(".cp-itens-card").count() > 0) return true;

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean exigeIntervencaoManualGovBr(Page page) {
        try {
            String texto = page.locator("body").innerText().toLowerCase();

            return texto.contains("captcha")
                    || texto.contains("validação")
                    || texto.contains("validacao")
                    || texto.contains("código")
                    || texto.contains("codigo")
                    || texto.contains("verificação")
                    || texto.contains("verificacao")
                    || texto.contains("não sou um robô")
                    || texto.contains("nao sou um robo");
        } catch (Exception e) {
            return false;
        }
    }

    private static void selecionarEmpresaSeAparecer(BrowserContext context, Page page) {
        try {
            page.waitForTimeout(3000);

            if (page.locator("text=MEDIPHACOS INDUSTRIAS MEDICAS S/A").count() > 0) {
                System.out.println("Selecionando empresa MEDIPHACOS...");
                page.locator("text=MEDIPHACOS INDUSTRIAS MEDICAS S/A").first().click();
                page.waitForTimeout(2000);

                Locator confirmar = page.locator(
                        "input[type='submit'], button:has-text('Confirmar'), button:has-text('Selecionar')"
                );

                if (confirmar.count() > 0) {
                    confirmar.first().click();
                    page.waitForTimeout(8000);
                }

                salvarSessaoDebug(context);
                System.out.println("Empresa selecionada.");
            }
        } catch (Exception e) {
            System.out.println("Nao foi possivel selecionar empresa automaticamente: " + e.getMessage());
        }
    }

    private static void salvarSessaoDebug(BrowserContext context) {
        try {
            context.storageState(new BrowserContext.StorageStateOptions().setPath(Paths.get(SESSION_FILE)));
            System.out.println("SESSION JSON salvo em: " + SESSION_FILE);
        } catch (Exception e) {
            System.out.println("Nao foi possivel salvar session.json: " + e.getMessage());
        }
    }

    private static void validarSessaoNaoCaiu(Page page, String contexto) {
        try {
            if (page.locator("text=Entrar com Gov.br").count() > 0
                    || page.locator("input[name='accountId'], #accountId").count() > 0
                    || page.url().contains("sso.acesso.gov.br")) {

                tirarPrintDebug(page, "sessao_perdida_" + contexto + ".png");
                throw new RuntimeException("Sessao perdida em: " + contexto + ". Print salvo: sessao_perdida_" + contexto + ".png");
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            System.out.println("Nao foi possivel validar sessao em " + contexto + ": " + e.getMessage());
        }
    }

    private static Page abrirPortalComprasNet(BrowserContext context, Page page) {
        try {
            System.out.println("Abrindo portal antigo ComprasNet...");

            page = paginaVivaOuNova(context, page);
            page.bringToFront();

            int totalAntes = context.pages().size();

            page.navigate(
                    "https://www.comprasnet.gov.br/assinadas/dispensa_eletronica.asp",
                    new Page.NavigateOptions().setTimeout(120000)
            );

            page.waitForTimeout(12000);

            Page paginaComConteudo = localizarMelhorPaginaComprasNet(context, page, totalAntes);
            paginaComConteudo.bringToFront();

            System.out.println("URL apos abrir portal antigo: " + paginaComConteudo.url());

            validarSessaoNaoCaiu(paginaComConteudo, "abrir_portal_antigo");

            boolean carregouCards = aguardarCardsOuTelaCompras(paginaComConteudo, 60000);

            if (!carregouCards) {
                tirarPrintDebug(paginaComConteudo, "portal_antigo_sem_cards.png");
                System.out.println("Portal antigo abriu, mas os cards nao apareceram dentro do tempo.");
            }

            salvarSessaoDebug(context);
            return paginaComConteudo;

        } catch (Exception e) {
            throw new RuntimeException("Erro ao abrir portal ComprasNet: " + e.getMessage(), e);
        }
    }

    private static Page paginaVivaOuNova(BrowserContext context, Page page) {
        try {
            if (page != null && !page.isClosed()) {
                return page;
            }
        } catch (Exception ignored) {
        }

        if (context.pages() != null && !context.pages().isEmpty()) {
            for (Page p : context.pages()) {
                try {
                    if (!p.isClosed()) return p;
                } catch (Exception ignored) {
                }
            }
        }

        return context.newPage();
    }

    private static Page localizarMelhorPaginaComprasNet(BrowserContext context, Page fallback, int totalAntes) {
        List<Page> paginas = context.pages();

        for (int i = paginas.size() - 1; i >= 0; i--) {
            Page p = paginas.get(i);
            try {
                if (!p.isClosed() && p.locator(".cp-itens-card").count() > 0) {
                    System.out.println("Pagina com cards localizada: " + p.url());
                    return p;
                }
            } catch (Exception ignored) {
            }
        }

        for (int i = paginas.size() - 1; i >= 0; i--) {
            Page p = paginas.get(i);
            try {
                String url = p.url();
                if (!p.isClosed()
                        && url != null
                        && (url.contains("assinadas/dispensa_eletronica.asp") || url.contains("comprasnet-web/seguro"))) {
                    System.out.println("Pagina ComprasNet localizada: " + p.url());
                    return p;
                }
            } catch (Exception ignored) {
            }
        }

        if (paginas.size() > totalAntes) {
            Page ultima = paginas.get(paginas.size() - 1);
            System.out.println("Usando ultima aba aberta: " + ultima.url());
            return ultima;
        }

        return fallback;
    }

    private static boolean aguardarCardsOuTelaCompras(Page page, long timeoutMs) {
        long inicio = System.currentTimeMillis();

        while ((System.currentTimeMillis() - inicio) < timeoutMs) {
            try {
                if (page.locator(".cp-itens-card").count() > 0) {
                    System.out.println("Cards encontrados na pagina ativa.");
                    return true;
                }

                if (page.locator("text=Acompanhamento de Compra").count() > 0) {
                    System.out.println("Tela de acompanhamento detectada.");
                    return true;
                }

                if (page.locator("app-botao-mensagens-da-compra").count() > 0) {
                    System.out.println("Botao de mensagens detectado.");
                    return true;
                }

                page.waitForTimeout(1000);
            } catch (Exception ignored) {
            }
        }

        return false;
    }

    private static Page garantirPaginaPrincipal(BrowserContext context, Page pageAtual) {
        try {
            pageAtual = paginaVivaOuNova(context, pageAtual);

            List<Page> paginas = context.pages();
            System.out.println("Total de abas abertas: " + paginas.size());

            for (Page p : paginas) {
                try {
                    if (!p.isClosed() && p.locator(".cp-itens-card").count() > 0) {
                        System.out.println("Usando aba com cards: " + p.url());
                        p.bringToFront();
                        return p;
                    }
                } catch (Exception ignored) {
                }
            }

            for (Page p : paginas) {
                try {
                    if (!p.isClosed() && p.locator("app-botao-mensagens-da-compra").count() > 0) {
                        System.out.println("Usando aba com botao de chat: " + p.url());
                        p.bringToFront();
                        return p;
                    }
                } catch (Exception ignored) {
                }
            }

            for (Page p : paginas) {
                try {
                    String url = p.url();
                    if (!p.isClosed() && url != null && url.contains("comprasnet-web/seguro")) {
                        System.out.println("Usando aba comprasnet-web/seguro: " + p.url());
                        p.bringToFront();
                        return p;
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            System.out.println("Nao foi possivel garantir aba ativa: " + e.getMessage());
        }

        return pageAtual;
    }

    private static void abrirPrimeiraOportunidadePorCard(Page page, Oportunidade oportunidade) {
        System.out.println("Abrindo primeira oportunidade por card...");

        page.locator(".cp-itens-card").first().waitFor(
                new Locator.WaitForOptions()
                        .setTimeout(90000)
                        .setState(WaitForSelectorState.VISIBLE)
        );

        List<Locator> cards = page.locator(".cp-itens-card").all();
        System.out.println("TOTAL CARDS: " + cards.size());

        boolean encontrou = false;

        for (Locator card : cards) {
            try {
                String texto = card.innerText();

                if (texto == null) continue;

                if (texto.contains(oportunidade.getNumeroPregao()) && texto.contains(oportunidade.getUasg())) {
                    encontrou = true;
                    System.out.println("OPORTUNIDADE ENCONTRADA POR CARD");
                    card.locator("button:has(i.fa-plus-square)").first().click();
                    page.waitForTimeout(10000);
                    break;
                }
            } catch (Exception e) {
                System.out.println("Erro lendo card: " + e.getMessage());
            }
        }

        if (!encontrou) {
            tirarPrintDebug(page, "primeira_oportunidade_nao_encontrada.png");
            throw new RuntimeException("OPORTUNIDADE NAO ENCONTRADA NO CARD: " + oportunidade.getUasg() + " - " + oportunidade.getNumeroPregao());
        }
    }

    private static void abrirOportunidadePorUrlDireta(BrowserContext context, Page page, Oportunidade oportunidade) {
        System.out.println("ABRINDO OPORTUNIDADE VIA URL DIRETA");

        String numeroCompra = gerarNumeroCompra(oportunidade);

        String urlCompra =
                "https://cnetmobile.estaleiro.serpro.gov.br/comprasnet-web/seguro/"
                        + "fornecedor/acompanhamento-compra?compra="
                        + numeroCompra;

        System.out.println("URL COMPRA: " + urlCompra);

        fecharChatSeAberto(page);

        page = paginaVivaOuNova(context, page);
        page.bringToFront();

        page.evaluate("window.location.href = '" + urlCompra + "'");
        page.waitForTimeout(15000);

        Page paginaCompra = localizarPaginaDaCompra(context, page, numeroCompra);
        paginaCompra.bringToFront();

        System.out.println("URL APOS ABRIR COMPRA: " + paginaCompra.url());

        validarSessaoNaoCaiu(paginaCompra, "url_direta_" + oportunidade.getId());

        if (paginaCompra.locator("text=Não autorizado").count() > 0
                || paginaCompra.locator("text=Nao autorizado").count() > 0
                || paginaCompra.locator("text=Acesso negado").count() > 0) {

            tirarPrintDebug(paginaCompra, "acesso_negado_" + oportunidade.getId() + ".png");
            throw new RuntimeException("Acesso negado na oportunidade " + oportunidade.getId() + ". Verifique se a empresa selecionada tem acesso a essa compra.");
        }

        aguardarCardsOuTelaCompras(paginaCompra, 60000);
        salvarSessaoDebug(context);

        System.out.println("DETALHE DA COMPRA CARREGADO.");
    }

    private static Page localizarPaginaDaCompra(BrowserContext context, Page fallback, String numeroCompra) {
        List<Page> paginas = context.pages();

        for (int i = paginas.size() - 1; i >= 0; i--) {
            Page p = paginas.get(i);
            try {
                String url = p.url();
                if (!p.isClosed() && url != null && url.contains(numeroCompra)) {
                    return p;
                }
            } catch (Exception ignored) {
            }
        }

        for (int i = paginas.size() - 1; i >= 0; i--) {
            Page p = paginas.get(i);
            try {
                if (!p.isClosed() && p.locator("app-botao-mensagens-da-compra").count() > 0) {
                    return p;
                }
            } catch (Exception ignored) {
            }
        }

        return fallback;
    }

    private static String gerarNumeroCompra(Oportunidade oportunidade) {
        String uasg = String.format("%06d", Integer.parseInt(oportunidade.getUasg()));

        String pregao = oportunidade.getNumeroPregao()
                .replace("/", "")
                .replace("-", "")
                .replace(".", "")
                .replace(" ", "")
                .trim();

        return uasg + "05" + pregao;
    }

    private static void abrirChat(Page page) {
        System.out.println("Tentando abrir chat...");
        System.out.println("URL antes de abrir chat: " + page.url());

        page.bringToFront();
        page.waitForTimeout(5000);

        Locator chatBtn = page.locator("app-botao-mensagens-da-compra button");

        if (chatBtn.count() == 0) {
            System.out.println("Botao padrao do chat nao encontrado. Tentando seletores alternativos...");
            chatBtn = page.locator(
                    "button:has-text('Mensagem'), " +
                            "button:has-text('Mensagens'), " +
                            "button:has-text('Chat'), " +
                            "app-botao-mensagens-da-compra"
            );
        }

        try {
            chatBtn.last().waitFor(
                    new Locator.WaitForOptions()
                            .setTimeout(90000)
                            .setState(WaitForSelectorState.VISIBLE)
            );

            chatBtn.last().click();

            try {
                page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(15000));
            } catch (Exception e) {
                System.out.println("NETWORKIDLE apos abrir chat nao estabilizou. Continuando.");
            }

            page.waitForTimeout(12000);
            System.out.println("CHAT ABERTO");

        } catch (Exception e) {
            tirarPrintDebug(page, "botao_chat_nao_encontrado.png");

            System.out.println("HTML parcial da pagina:");
            try {
                String html = page.content();
                System.out.println(html.substring(0, Math.min(3000, html.length())));
            } catch (Exception ignored) {
            }

            throw new RuntimeException("Nao foi possivel encontrar/clicar no botao do chat. Print salvo: botao_chat_nao_encontrado.png", e);
        }
    }

    private static void fecharChatSeAberto(Page page) {
        try {
            Locator fecharChat = page.locator("button[aria-label='Close'], button.p-dialog-header-icon");

            if (fecharChat.count() > 0) {
                fecharChat.first().click();
                page.waitForTimeout(3000);
                System.out.println("CHAT FECHADO");
            }
        } catch (Exception e) {
            System.out.println("Nao foi possivel fechar chat. Continuando.");
        }
    }

    private static void capturarMensagensPaginadasComInteracao(Oportunidade oportunidade, Page page, ObjectMapper mapper) {
        try {
            System.out.println("INICIANDO CAPTURA PAGINADA DO CHAT COM INTERACAO HUMANA");

            page.bringToFront();
            page.waitForTimeout(5000);

            /*
             * IMPORTANTE:
             * No passado o captcha apareceu quando a troca de pagina foi feita via JS/click artificial.
             * Aqui voltamos para uma estrategia mais humana:
             * 1) localizar o botao real da paginacao;
             * 2) scrollar ate ele;
             * 3) mover o mouse ate o centro;
             * 4) clicar com mouse real do Playwright;
             * 5) aguardar o listener global capturar o /v2/chat.
             */

            if (ULTIMO_JSON_CHAT != null && !ULTIMO_JSON_CHAT.trim().isEmpty()) {
                System.out.println("PROCESSANDO PAGINA 1 PELO JSON GLOBAL");
                processarMensagens(oportunidade, ULTIMO_JSON_CHAT, mapper);
            } else {
                System.out.println("PAGINA 1 SEM JSON GLOBAL. TENTANDO FORCAR LEITURA DA TELA...");
                page.waitForTimeout(8000);

                if (ULTIMO_JSON_CHAT != null && !ULTIMO_JSON_CHAT.trim().isEmpty()) {
                    processarMensagens(oportunidade, ULTIMO_JSON_CHAT, mapper);
                } else {
                    System.out.println("NAO FOI POSSIVEL PROCESSAR PAGINA 1 PELO JSON GLOBAL.");
                    tirarPrintDebug(page, "chat_pagina_1_sem_json.png");
                }
            }

            int totalPaginas = descobrirTotalPaginas(page);
            System.out.println("Total de paginas visiveis do chat: " + totalPaginas);

            if (totalPaginas <= 1) {
                System.out.println("CHAT POSSUI APENAS UMA PAGINA VISIVEL.");
                return;
            }

            for (int i = 2; i <= totalPaginas; i++) {
                try {
                    System.out.println(" INDO PARA PAGINA " + i + " DO CHAT");

                            ULTIMO_JSON_CHAT = "";

                    boolean clicou = clicarProximaPaginaChatComoUsuario(page, i);

                    if (!clicou) {
                        System.out.println("Nao conseguiu clicar na pagina " + i + " do chat.");
                        tirarPrintDebug(page, "pagina_chat_nao_clicada_" + i + ".png");
                        continue;
                    }

                    aguardarJsonChatAposClique(page, i, 30000);

                    if (captchaChatApareceu(page)) {
                        System.out.println("CAPTCHA DETECTADO NA PAGINA " + i + ". TENTANDO RECUPERACAO SUAVE...");
                        tirarPrintDebug(page, "captcha_chat_pagina_" + i + ".png");

                        boolean recuperou = recuperarChatAposCaptcha(page, i);

                        if (!recuperou) {
                            System.out.println("Nao foi possivel recuperar apos captcha na pagina " + i + ". Encerrando paginacao desta oportunidade.");
                            break;
                        }

                        aguardarJsonChatAposClique(page, i, 30000);
                    }

                    if (ULTIMO_JSON_CHAT != null && !ULTIMO_JSON_CHAT.trim().isEmpty()) {
                        System.out.println("JSON RECEBIDO NA PAGINA " + i);
                        processarMensagens(oportunidade, ULTIMO_JSON_CHAT, mapper);
                    } else {
                        System.out.println("JSON NAO RECEBIDO PAGINA " + i);
                        tirarPrintDebug(page, "json_nao_recebido_pagina_" + i + ".png");
                    }

                    page.waitForTimeout(3500);

                } catch (Exception e) {
                    System.out.println("ERRO PAGINA " + i + ": " + e.getMessage());
                    tirarPrintDebug(page, "erro_chat_pagina_" + i + ".png");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean clicarProximaPaginaChatComoUsuario(Page page, int numeroPaginaEsperada) {
        try {
            page.bringToFront();
            page.waitForTimeout(2500);

            /*
             * AJUSTE IMPORTANTE:
             * O erro de captcha voltava quando clicavamos diretamente no numero da pagina,
             * principalmente no botao "2" da paginacao PrimeNG.
             *
             * A versao que funcionou melhor anteriormente foi navegar como usuario:
             * clicar no botao PROXIMA PAGINA do paginador, e nao no numero direto.
             * Isso preserva melhor o fluxo interno do componente e evita acionar a validacao indevida.
             */

            Locator modalOuBody = page.locator(".p-dialog, .p-dialog-content, body").first();
            try {
                modalOuBody.click(new Locator.ClickOptions().setTimeout(5000));
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
                System.out.println("Botao de proxima pagina nao encontrado. Tentando fallback pelo numero " + numeroPaginaEsperada);
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

                System.out.println("Clique no botao PROXIMA PAGINA realizado para chegar na pagina " + numeroPaginaEsperada);
            } else {
                alvo.click(new Locator.ClickOptions().setTimeout(15000));
                System.out.println("Clique normal no botao PROXIMA PAGINA realizado para chegar na pagina " + numeroPaginaEsperada);
            }

            page.waitForTimeout(7000);
            return true;

        } catch (Exception e) {
            System.out.println("Erro ao clicar em proxima pagina para chegar na pagina " + numeroPaginaEsperada + ": " + e.getMessage());
            return false;
        }
    }

    private static boolean clicarPaginaChatComoHumanoFallback(Page page, int numeroPagina) {
        try {
            page.bringToFront();
            page.waitForTimeout(1500);

            Locator botao = localizarBotaoPaginaChat(page, numeroPagina);

            if (botao == null || botao.count() == 0) {
                System.out.println("Botao da pagina " + numeroPagina + " nao localizado.");
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
                System.out.println("BoundingBox nulo para pagina " + numeroPagina + ". Tentando click normal.");
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

            System.out.println("Clique fallback realizado na pagina " + numeroPagina + " em x=" + x + " y=" + y);
            page.waitForTimeout(5000);

            return true;

        } catch (Exception e) {
            System.out.println("Erro no clique fallback da pagina " + numeroPagina + ": " + e.getMessage());
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

    private static boolean captchaChatApareceu(Page page) {
        try {
            String texto = page.locator("body").innerText().toLowerCase();

            return texto.contains("captcha")
                    || texto.contains("não foi possível realizar a validação")
                    || texto.contains("nao foi possivel realizar a validacao")
                    || texto.contains("validação do captcha")
                    || texto.contains("validacao do captcha");
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean recuperarChatAposCaptcha(Page page, int numeroPagina) {
        try {
            System.out.println("Executando recuperacao suave do chat apos captcha...");

            page.waitForTimeout(8000);

            Locator fechar = page.locator("button[aria-label='Close'], button.p-dialog-header-icon, .p-dialog-header-close");

            if (fechar.count() > 0) {
                fechar.first().click();
                page.waitForTimeout(5000);
            }

            abrirChat(page);
            page.waitForTimeout(8000);

            if (numeroPagina <= 1) {
                return true;
            }

            return clicarProximaPaginaChatComoUsuario(page, numeroPagina);

        } catch (Exception e) {
            System.out.println("Erro na recuperacao suave do chat: " + e.getMessage());
            return false;
        }
    }

    private static void aguardarJsonChatAposClique(Page page, int numeroPagina, long timeoutMs) {
        long inicio = System.currentTimeMillis();

        while ((System.currentTimeMillis() - inicio) < timeoutMs) {
            if (ULTIMO_JSON_CHAT != null && !ULTIMO_JSON_CHAT.trim().isEmpty()) {
                return;
            }

            try {
                page.waitForTimeout(500);
            } catch (Exception ignored) {
            }
        }

        System.out.println("Timeout aguardando JSON da pagina " + numeroPagina);
    }

    private static boolean clicarPaginaViaJS(Page page, int numeroPagina) {
        try {
            Object resultado = page.evaluate(
                    "function(num) {"
                            + "  var seletores = ["
                            + "    'button[data-pc-section=\"page\"][aria-label=\"Página ' + num + '\"]',"
                            + "    'button[data-pc-section=\"page\"][aria-label=\"Pagina ' + num + '\"]',"
                            + "    'button.p-paginator-page[aria-label=\"Página ' + num + '\"]',"
                            + "    'button.p-paginator-page[aria-label=\"Pagina ' + num + '\"]'"
                            + "  ];"
                            + "  for (var s = 0; s < seletores.length; s++) {"
                            + "    var btns = document.querySelectorAll(seletores[s]);"
                            + "    for (var b = 0; b < btns.length; b++) {"
                            + "      var btn = btns[b];"
                            + "      if (btn) {"
                            + "        btn.scrollIntoView({block: 'center', inline: 'center'});"
                            + "        btn.click();"
                            + "        return true;"
                            + "      }"
                            + "    }"
                            + "  }"
                            + "  var todos = document.querySelectorAll('button[data-pc-section=\"page\"], button.p-paginator-page');"
                            + "  for (var t = 0; t < todos.length; t++) {"
                            + "    if (todos[t].textContent.trim() === String(num)) {"
                            + "      todos[t].scrollIntoView({block: 'center', inline: 'center'});"
                            + "      todos[t].click();"
                            + "      return true;"
                            + "    }"
                            + "  }"
                            + "  return false;"
                            + "}",
                    numeroPagina
            );

            return Boolean.TRUE.equals(resultado);

        } catch (Exception e) {
            System.out.println("Erro ao clicar pagina " + numeroPagina + " via JS: " + e.getMessage());
            return false;
        }
    }

    private static int descobrirTotalPaginas(Page page) {
        try {
            Object resultado = page.evaluate(
                    "function() {"
                            + "  var botoes = document.querySelectorAll('button[data-pc-section=\"page\"], button.p-paginator-page');"
                            + "  var max = 1;"
                            + "  for (var i = 0; i < botoes.length; i++) {"
                            + "    var txt = botoes[i].textContent ? botoes[i].textContent.trim() : '';"
                            + "    var num = parseInt(txt, 10);"
                            + "    if (!isNaN(num) && num > max) max = num;"
                            + "  }"
                            + "  return max;"
                            + "}"
            );

            if (resultado instanceof Number) {
                return ((Number) resultado).intValue();
            }
        } catch (Exception e) {
            System.out.println("Erro ao descobrir total de paginas: " + e.getMessage());
        }

        return 1;
    }

    private static void processarMensagens(Oportunidade oportunidade, String body, ObjectMapper mapper) {
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode lista = root.isArray() ? root : root.path("content");

            if (lista == null || !lista.isArray()) {
                System.out.println("LISTA VAZIA");
                return;
            }

            System.out.println("TOTAL MSGS: " + lista.size());

            for (JsonNode msg : lista) {
                String id = getText(msg, "id", "uuid", "chaveMensagemNaOrigem");

                if (id == null || id.isEmpty()) {
                    id = montarIdAlternativo(msg);
                }

                String texto = getText(msg, "texto", "mensagem", "conteudo");
                String remetente = getText(msg, "remetente", "tipoRemetente", "origem");
                String data = getText(msg, "dataHora", "dataEnvio", "data");

                Integer item = null;
                JsonNode itemNode = msg.path("numeroItem");

                if (!itemNode.isMissingNode() && !itemNode.isNull()) {
                    item = itemNode.asInt();
                }

                boolean inserido = salvarPregaoChat(id, texto, remetente, data, item, oportunidade);

                if (!inserido) {
                    System.out.println("Mensagem ja existente. Parando esta pagina.");
                    break;
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

    private static String montarIdAlternativo(JsonNode msg) {
        String texto = getText(msg, "texto", "mensagem", "conteudo");
        String data = getText(msg, "dataHora", "dataEnvio", "data");
        String remetente = getText(msg, "remetente", "tipoRemetente", "origem");
        return (data + "|" + remetente + "|" + texto).hashCode() + "";
    }

    private static boolean salvarPregaoChat(String chaveMensagem, String texto, String remetente, String dataStr, Integer itemLote, Oportunidade oportunidade) {
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

            novaMensagem = true;
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

    private static List<Oportunidade> buscarOportunidadesDoBanco() {
        List<Oportunidade> lista = new ArrayList<Oportunidade>();

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

                String cliente = (fantasia != null && !fantasia.trim().isEmpty()) ? fantasia.trim() : razaoSocial;
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

    private static void dispararWhatsAppNovaMsg(Oportunidade oportunidade, boolean novaMensagem) {
        if (!novaMensagem) {
            return;
        }

        String mensagem =
                "AVISO: Ha novas mensagens no chat do edital "
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
                "URGENTE: Houve alteracoes no chat do edital "
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

            String[] palavras = razaoUpper.split("\\s+");
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

    private static void tirarPrintDebug(Page page, String nomeArquivo) {
        try {
            page.screenshot(
                    new Page.ScreenshotOptions()
                            .setPath(Paths.get(nomeArquivo))
                            .setFullPage(true)
            );

            System.out.println("Print salvo para debug: " + nomeArquivo);
        } catch (Exception e) {
            System.out.println("Nao foi possivel salvar print debug: " + e.getMessage());
        }
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

    private static String truncar(String s, int max) {
        if (s == null) {
            return null;
        }

        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String getText(JsonNode node, String... campos) {
        if (node == null || campos == null) {
            return "";
        }

        for (String c : campos) {
            JsonNode n = node.get(c);
            if (n != null && !n.isNull()) {
                return n.asText();
            }
        }

        return "";
    }

    private static Connection getConexaoMySQL() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    private static void carregarConfigBanco() throws Exception {
        Properties props = carregarProperties();

        DB_URL = props.getProperty("spring.datasource.url");
        DB_USER = props.getProperty("spring.datasource.username");
        DB_PASSWORD = props.getProperty("spring.datasource.password");

        if (DB_URL == null || DB_URL.trim().isEmpty()) {
            throw new RuntimeException("Propriedade spring.datasource.url nao configurada.");
        }

        System.out.println("Banco configurado: " + DB_URL);
    }

    private static void carregarConfigLogin() throws Exception {
        Properties props = carregarProperties();

        CPF = props.getProperty("app.govbr.cpf", "").trim();
        SENHA = props.getProperty("app.govbr.senha", "").trim();

        if (CPF.isEmpty() || SENHA.isEmpty()) {
            System.out.println("Aviso: app.govbr.cpf/app.govbr.senha nao configurados.");
            System.out.println("Se a sessao persistente ja estiver valida, pode funcionar mesmo assim.");
        }
    }

    private static Properties carregarProperties() throws Exception {
        Properties props = new Properties();

        InputStream is = RPAChatComprasNetFullTeste.class
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
}
