package br.gov.compras.rpa.service;

import br.gov.compras.rpa.model.Oportunidade;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

public class RPAChatComprasNetFullOld {

    // =========================
    // LOGIN
    // =========================
    private static final String CPF =
            "06303344127";

    private static final String SENHA =
            "Rfmh05046@";

    // =========================
    // SESSION
    // =========================
    private static final String SESSION_FILE =
            "session.json";

    // =========================
    // OPORTUNIDADES
    // =========================
     // =========================
     // OPORTUNIDADES
     // =========================
     private static final List<Oportunidade> OPORTUNIDADES =
             Arrays.asList(

                     new Oportunidade(
                             9377L, // clienteId
                             11L,
                             "98765432000199", // cnpj
                             "Outra Empresa Ltda", // razaoSocial
                             "Fantasia Outra", // fantasia
                              5L, // modalidadeId
                             "900332024", // numeroEdital
                             "160199", // uasgOc
                             LocalDate.of(2023, 11, 15), // dataCertame
                             "Inativo", // status
                             "MEDIPHACOS", // cliente
                             "160199", // uasg
                             "90033/2024" // numeroPregao
                     )
             );

    // =========================
    // JSON CHAT
    // =========================
    private static volatile String ULTIMO_JSON_CHAT =
            "";

    public static void main(String[] args) {

        try {

            // =========================
            // H2
            // =========================
            org.h2.tools.Server
                    .createWebServer(
                            "-web",
                            "-webPort",
                            "8082"
                    )
                    .start();

            criarTabela();
            /*
            try {

               WhatsAppNotificationService.sendGroupMessage(
                        "5534996461183",
                        "ChatComprasGov",
                        "Oie, Eu sou o Robô do comprasGov. " +
                                "TESTE Do Dia: Pregoeiro citou nome cliente Mediphacos no chat do edital 90070/2025. "
                );



            } catch (Exception e) {

                e.printStackTrace();
            }
            */

            // =========================
            // PLAYWRIGHT
            // =========================
            Playwright playwright =
                    Playwright.create();

            Browser browser =
                    playwright.firefox().launch(

                            new BrowserType.LaunchOptions()
                                    .setHeadless(true)
                                    .setSlowMo(0)
                    );

            BrowserContext context;

            // =========================
            // SESSION
            // =========================
            if (Files.exists(
                    Paths.get(SESSION_FILE)
            )) {

                context =
                        browser.newContext(

                                new Browser.NewContextOptions()
                                        .setStorageStatePath(
                                                Paths.get(
                                                        SESSION_FILE
                                                )
                                        )
                        );

                System.out.println(
                        "🔄 Sessão carregada"
                );

            } else {

                context =
                        browser.newContext();

                System.out.println(
                        "⚠️ Nova sessão"
                );
            }

            ObjectMapper mapper =
                    new ObjectMapper();

            // =========================
            // LISTENER CHAT
            // =========================
            context.onResponse(response -> {

                try {

                    if (!response.url()
                            .contains("/v2/chat")) {

                        return;
                    }

                    System.out.println(
                            "\n📥 CHAT RESPONSE"
                    );

                    System.out.println(
                            response.url()
                    );

                    System.out.println(
                            "STATUS: "
                                    + response.status()
                    );

                    String body =
                            response.text();

                    if (body == null
                            || body.isEmpty()) {

                        System.out.println(
                                "⚠️ BODY VAZIO"
                        );

                        return;
                    }

                    ULTIMO_JSON_CHAT =
                            body;

                    System.out.println(
                            "📦 JSON RECEBIDO"
                    );

                } catch (Exception e) {

                    e.printStackTrace();
                }
            });

            // =========================
            // PAGE
            // =========================
            Page page =
                    context.newPage();

            // =========================
            // LOGIN
            // =========================
            realizarLogin(
                    context,
                    page
            );

            // =========================
            // ABRE PORTAL
            // =========================
            Page finalPage = page;

            Page portalPage =
                    context.waitForPage(() -> {

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
            for (int indice = 0;
                 indice < OPORTUNIDADES.size();
                 indice++) {

                Oportunidade oportunidade =
                        OPORTUNIDADES.get(indice);

                try {

                    System.out.println(
                            "\n=================================="
                    );

                    System.out.println(
                            "🚀 PROCESSANDO OPORTUNIDADE"
                    );

                    System.out.println(
                            "CLIENTE: "
                                    + oportunidade.getCliente()
                    );

                    System.out.println(
                            "UASG: "
                                    + oportunidade.getUasg()
                    );

                    System.out.println(
                            "PREGÃO: "
                                    + oportunidade.getNumeroPregao()
                    );

                    System.out.println(
                            "=================================="
                    );

                    // =========================
                    // VOLTA LISTAGEM
                    // =========================
// =========================
// PRIMEIRA OPORTUNIDADE
// (mantém exatamente como está)
// =========================
                    if (indice == 0) {

                        // =========================
                        // AGUARDA CARDS
                        // =========================
                        page.locator(".cp-itens-card")
                                .first()
                                .waitFor(

                                        new Locator.WaitForOptions()
                                                .setTimeout(30000)
                                                .setState(
                                                        WaitForSelectorState.VISIBLE
                                                )
                                );

                        List<Locator> cards =
                                page.locator(".cp-itens-card")
                                        .all();

                        System.out.println(
                                "📦 TOTAL CARDS: "
                                        + cards.size()
                        );

                        boolean encontrou =
                                false;

                        // =========================
                        // PROCURA OPORTUNIDADE
                        // =========================
                        for (Locator card : cards) {

                            try {

                                String texto =
                                        card.innerText();

                                if (texto == null) {
                                    continue;
                                }

                                System.out.println(
                                        "\n-------------------"
                                );

                                System.out.println(
                                        texto
                                );

                                if (texto.contains(
                                        oportunidade.getNumeroPregao()
                                )
                                        &&
                                        texto.contains(
                                                oportunidade.getUasg()
                                        )) {

                                    encontrou = true;

                                    System.out.println(
                                            "✅ OPORTUNIDADE ENCONTRADA"
                                    );

                                    card.locator(
                                            "button:has(i.fa-plus-square)"
                                    ).first().click();

                                    page.waitForTimeout(8000);

                                    break;
                                }

                            } catch (Exception e) {

                                e.printStackTrace();
                            }
                        }

                        if (!encontrou) {

                            System.out.println(
                                    "⚠️ OPORTUNIDADE NÃO ENCONTRADA"
                            );

                            continue;
                        }

                    } else {

                        // =========================
                        // SEGUNDA OPORTUNIDADE+
                        // URL DIRETA
                        // =========================
                        System.out.println(
                                "\n🚀 ABRINDO VIA URL DIRETA"
                        );

                        String uasg =
                                String.format(
                                        "%06d",
                                        Integer.parseInt(
                                                oportunidade.getUasg()
                                        )
                                );

                        String pregao =
                                oportunidade.getNumeroPregao()
                                        .replace("/", "")
                                        .replace("-", "")
                                        .trim();

                        String numeroCompra =
                                uasg
                                        + "05"
                                        + pregao;

                        String urlCompra =
                                "https://cnetmobile.estaleiro.serpro.gov.br/comprasnet-web/seguro/fornecedor/acompanhamento-compra?compra="
                                        + numeroCompra;

                        System.out.println(
                                urlCompra
                        );

                        // fecha chat se aberto
                        Locator fecharChat =
                                page.locator(
                                        "button[aria-label='Close'], button.p-dialog-header-icon"
                                );

                        if (fecharChat.count() > 0) {

                            try {

                                fecharChat.first().click();

                                page.waitForTimeout(3000);

                            } catch (Exception ignored) {
                            }
                        }

                        // navega direto
                        page.navigate(urlCompra);

                        page.waitForLoadState(
                                LoadState.NETWORKIDLE
                        );

                        page.waitForTimeout(12000);

                        System.out.println(
                                "✅ DETALHE CARREGADO"
                        );
                    }

                    // =========================
                    // RELOAD DETALHE
                    // =========================
                    page.reload();

                    page.waitForLoadState(
                            LoadState.NETWORKIDLE
                    );

                    page.waitForTimeout(10000);

                    // =========================
                    // ABRE CHAT
                    // =========================
                    Locator chatBtn =
                            page.locator(
                                    "app-botao-mensagens-da-compra button"
                            ).last();

                    chatBtn.waitFor(
                            new Locator.WaitForOptions()
                                    .setState(
                                            WaitForSelectorState.VISIBLE
                                    )
                    );

                    chatBtn.click();

                    page.waitForLoadState(
                            LoadState.NETWORKIDLE
                    );

                    page.waitForTimeout(10000);

                    System.out.println(
                            "✅ CHAT ABERTO"
                    );

                    // =========================
                    // CAPTURA CHAT
                    // =========================
                    capturarMensagensPaginadasComInteracao(
                            oportunidade,
                            page,
                            mapper
                    );

                } catch (Exception e) {

                    System.out.println(
                            "❌ ERRO OPORTUNIDADE"
                    );

                    e.printStackTrace();
                }
            }

            System.out.println(
                    "\n✅ FINALIZADO"
            );

            Thread.sleep(999999999);

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    // =========================
    // LOGIN
    // =========================
    private static void realizarLogin(
            BrowserContext context,
            Page page
    ) throws Exception {

        page.navigate(
                "https://www.comprasnet.gov.br/seguro/loginFornecedorSelecEmpresa.asp"
        );

        page.waitForTimeout(5000);

        if (page.locator(
                "text=Entrar com Gov.br"
        ).count() > 0) {

            System.out.println(
                    "🔐 Fazendo login"
            );

            page.locator(
                    "text=Entrar com Gov.br"
            ).click();

            page.waitForLoadState();

            page.fill(
                    "input[name='accountId'], #accountId",
                    CPF
            );

            page.locator(
                    "#enter-account-id"
            ).click();

            page.waitForTimeout(3000);

            page.fill(
                    "input[name='password'], #password",
                    SENHA
            );

            page.locator(
                    "#submit-button"
            ).click();

            page.waitForTimeout(10000);

            context.storageState(

                    new BrowserContext.StorageStateOptions()
                            .setPath(
                                    Paths.get(
                                            SESSION_FILE
                                    )
                            )
            );

            System.out.println(
                    "✅ Sessão salva"
            );
        }

        // =========================
        // EMPRESA
        // =========================
        if (page.locator(
                "text=MEDIPHACOS INDUSTRIAS MEDICAS S/A"
        ).count() > 0) {

            page.locator(
                    "text=MEDIPHACOS INDUSTRIAS MEDICAS S/A"
            ).first().click();

            page.locator(
                    "input[type='submit'], button:has-text('Confirmar')"
            ).click();

            page.waitForTimeout(8000);
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

            // =========================
            // PAGINA 1
            // =========================
            if (ULTIMO_JSON_CHAT != null
                    && !ULTIMO_JSON_CHAT.isEmpty()) {

                processarMensagens(
                        oportunidade.getCliente(),
                        oportunidade.getUasg(),
                        oportunidade.getNumeroPregao(),
                        ULTIMO_JSON_CHAT,
                        mapper
                );
            }

            int totalPages = 15;

            // =========================
            // LOOP PAGINAS
            // =========================
            for (int i = 2;
                 i <= totalPages;
                 i++) {

                try {

                    Locator pageButton =
                            page.locator(
                                    "button[data-pc-section='page'][aria-label='Página "
                                            + i
                                            + "']"
                            );

                    if (pageButton.count() == 0) {

                        System.out.println(
                                "⚠️ PAGINA NÃO ENCONTRADA "
                                        + i
                        );

                        continue;
                    }

                    System.out.println(
                            "\n🔄 INDO PAGINA "
                                    + i
                    );

                    ULTIMO_JSON_CHAT =
                            "";

                    pageButton.first().click();

                    page.waitForTimeout(8000);

                    boolean captcha =
                            page.locator(
                                    "text=Não foi possível realizar a validação do Captcha"
                            ).count() > 0;

                    if (captcha) {

                        System.out.println(
                                "⚠️ CAPTCHA DETECTADO"
                        );

                        break;
                    }

                    if (ULTIMO_JSON_CHAT != null
                            && !ULTIMO_JSON_CHAT.isEmpty()) {

                        processarMensagens(
                                oportunidade.getCliente(),
                                oportunidade.getUasg(),
                                oportunidade.getNumeroPregao(),
                                ULTIMO_JSON_CHAT,
                                mapper
                        );

                    } else {

                        System.out.println(
                                "⚠️ JSON NÃO RECEBIDO PAGINA "
                                        + i
                        );
                    }

                } catch (Exception e) {

                    System.out.println(
                            "❌ ERRO PAGINA "
                                    + i
                    );

                    e.printStackTrace();
                }
            }

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    // =========================
    // PROCESSAR JSON
    // =========================
    private static void processarMensagens(
            String cliente,
            String uasg,
            String pregao,
            String body,
            ObjectMapper mapper
    ) {

        try {

            JsonNode root =
                    mapper.readTree(body);

            JsonNode lista =
                    root.isArray()
                            ? root
                            : root.path("content");

            if (lista == null
                    || !lista.isArray()) {

                System.out.println(
                        "⚠️ LISTA VAZIA"
                );

                return;
            }

            System.out.println(
                    "📦 TOTAL MSGS: "
                            + lista.size()
            );

            for (JsonNode msg : lista) {

                String id =
                        getText(
                                msg,
                                "id",
                                "uuid",
                                "chaveMensagemNaOrigem"
                        );

                if (id == null
                        || id.isEmpty()) {

                    continue;
                }

                String texto =
                        getText(
                                msg,
                                "texto",
                                "mensagem",
                                "conteudo"
                        );

                String remetente =
                        getText(
                                msg,
                                "remetente",
                                "tipoRemetente"
                        );

                String data =
                        getText(
                                msg,
                                "dataHora",
                                "dataEnvio"
                        );

                Integer item =
                        null;

                JsonNode itemNode =
                        msg.path("numeroItem");

                if (!itemNode.isMissingNode()
                        && !itemNode.isNull()) {

                    item =
                            itemNode.asInt();
                }

                salvar(
                        cliente,
                        uasg,
                        pregao,
                        id,
                        texto,
                        remetente,
                        data,
                        item
                );
            }

        } catch (Exception e) {

            System.out.println(
                    "⚠️ ERRO PROCESSANDO JSON"
            );

            e.printStackTrace();
        }
    }

    // =========================
    // SALVAR
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

        try (Connection conn =
                     DriverManager.getConnection(
                             "jdbc:h2:./chat-db",
                             "sa",
                             ""
                     )) {

            PreparedStatement ps =
                    conn.prepareStatement(
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

            System.out.println(
                    "💾 SALVO: "
                            + texto
            );

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    // =========================
    // TABELA
    // =========================
    private static void criarTabela()
            throws Exception {

        Connection conn =
                DriverManager.getConnection(
                        "jdbc:h2:./chat-db",
                        "sa",
                        ""
                );

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
    private static String getText(
            JsonNode node,
            String... campos
    ) {

        for (String c : campos) {

            JsonNode n =
                    node.get(c);

            if (n != null
                    && !n.isNull()) {

                return n.asText();
            }
        }

        return "";
    }
}