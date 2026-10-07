package br.gov.compras.rpa.service;

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
import java.util.List;

public class RPAChatComprasNetFullTesteV1 {

    private static final String CPF = "06303344127";
    private static final String SENHA = "Rfmh05046@";

    private static final String UASG = "160199";
    private static final String NUMERO_PREGAO = "90033/2024";

    private static final String SESSION_FILE = "session.json";

    // URL CHAT
    private static String CHAT_URL_BASE = "";

    // JSON CAPTURADO
    private static volatile String ULTIMO_JSON_CHAT = "";

    public static void main(String[] args) {

        try {

            // =========================
            // H2
            // =========================
            org.h2.tools.Server
                    .createWebServer("-web", "-webPort", "8082")
                    .start();

            criarTabela();

            // =========================
            // PLAYWRIGHT
            // =========================
            Playwright playwright = Playwright.create();

            Browser browser = playwright.firefox().launch(
                    new BrowserType.LaunchOptions()
                            .setHeadless(true)
                            .setSlowMo(0)
            );

            BrowserContext context;

            // =========================
            // SESSION
            // =========================
            if (Files.exists(Paths.get(SESSION_FILE))) {

                context = browser.newContext(
                        new Browser.NewContextOptions()
                                .setStorageStatePath(
                                        Paths.get(SESSION_FILE)
                                )
                );

                System.out.println("🔄 Sessão carregada");

            } else {

                context = browser.newContext();

                System.out.println("⚠️ Nova sessão");
            }

            ObjectMapper mapper = new ObjectMapper();

            // =========================
            // REQUEST
            // =========================
            context.onRequest(request -> {

                try {

                    if (request.url().contains("/v2/chat")) {

                        CHAT_URL_BASE = request.url();

                        System.out.println("\n📤 CHAT REQUEST");
                        System.out.println(request.url());
                    }

                } catch (Exception e) {

                    e.printStackTrace();
                }
            });

            // =========================
            // RESPONSE
            // =========================
            context.onResponse(response -> {

                try {

                    if (!response.url().contains("/v2/chat")) {
                        return;
                    }

                    System.out.println("\n📥 CHAT RESPONSE");
                    System.out.println(response.url());

                    System.out.println(
                            "STATUS: "
                                    + response.status()
                    );

                    String body = response.text();

                    if (body == null || body.isEmpty()) {

                        System.out.println(
                                "⚠️ BODY VAZIO"
                        );

                        return;
                    }

                    // salva último json
                    ULTIMO_JSON_CHAT = body;

                    System.out.println(
                            "📦 JSON RECEBIDO"
                    );

                } catch (Exception e) {

                    e.printStackTrace();
                }
            });

            Page page = context.newPage();

            // =========================
            // LOGIN
            // =========================
            page.navigate(
                    "https://www.comprasnet.gov.br/seguro/loginFornecedorSelecEmpresa.asp"
            );

            page.waitForTimeout(5000);

            if (page.locator(
                    "text=Entrar com Gov.br"
            ).count() > 0) {

                System.out.println("🔐 Fazendo login");

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
                                        Paths.get(SESSION_FILE)
                                )
                );

                System.out.println("✅ Sessão salva");
            }

            // =========================
            // EMPRESA
            // =========================
            page.locator(
                    "text=MEDIPHACOS INDUSTRIAS MEDICAS S/A"
            ).first().click();

            page.locator(
                    "input[type='submit'], button:has-text('Confirmar')"
            ).click();

            page.waitForTimeout(8000);

            // =========================
            // NOVA ABA
            // =========================
            Page finalPage = page;

            Page novaPagina =
                    context.waitForPage(() -> {

                        finalPage.navigate(
                                "https://www.comprasnet.gov.br/assinadas/dispensa_eletronica.asp"
                        );
                    });

            page = novaPagina;

            page.waitForLoadState();

            page.waitForTimeout(8000);

            // =========================
            // LOCALIZA PREGÃO
            // =========================
            List<Locator> cards =
                    page.locator(".cp-itens-card")
                            .all();

            for (Locator card : cards) {

                String texto = card.innerText();

                if (texto != null
                        && texto.contains(NUMERO_PREGAO)
                        && texto.contains(UASG)) {

                    card.locator(
                            "button:has(i.fa-plus-square)"
                    ).first().click();

                    break;
                }
            }

            page.waitForTimeout(8000);

            // =========================
            // RELOAD ÚNICO
            // =========================
            page.reload();

            page.waitForLoadState(
                    LoadState.NETWORKIDLE
            );

            page.waitForTimeout(10000);

            // =========================
            // ABRE CHAT
            // =========================
            Locator chatBtn = page.locator(
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

            System.out.println("✅ PAGINA 1 OK");

            // =========================
            // PAGINAÇÃO
            // =========================
            capturarMensagensPaginadasComInteracao(
                    page,
                    mapper
            );

            System.out.println("\n✅ FINALIZADO");

            Thread.sleep(999999999);

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    // =========================
    // PAGINAÇÃO
    // =========================
    private static void capturarMensagensPaginadasComInteracao(
            Page page,
            ObjectMapper mapper
    ) {

        try {

            // =========================
            // PROCESSA PAGINA 1
            // =========================
            if (ULTIMO_JSON_CHAT != null
                    && !ULTIMO_JSON_CHAT.isEmpty()) {

                System.out.println(
                        "\n📄 PROCESSANDO PAGINA 1"
                );

                processarMensagens(
                        ULTIMO_JSON_CHAT,
                        mapper
                );
            }

            int totalPages = 15;

            System.out.println(
                    "\n🔄 TOTAL PAGINAS: "
                            + totalPages
            );

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

                    // limpa json anterior
                    ULTIMO_JSON_CHAT = "";

                    // =========================
                    // CLICK NORMAL
                    // =========================
                    pageButton.first().click();

                    // importante
                    page.waitForTimeout(8000);

                    // =========================
                    // CAPTCHA
                    // =========================
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

                    // =========================
                    // PROCESSA JSON
                    // =========================
                    if (ULTIMO_JSON_CHAT != null
                            && !ULTIMO_JSON_CHAT.isEmpty()) {

                        System.out.println(
                                "📦 PROCESSANDO PAGINA "
                                        + i
                        );

                        processarMensagens(
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

                String id = getText(
                        msg,
                        "id",
                        "uuid",
                        "chaveMensagemNaOrigem"
                );

                if (id == null
                        || id.isEmpty()) {

                    System.out.println(
                            "⚠️ ID NÃO ENCONTRADO"
                    );

                    continue;
                }

                String texto = getText(
                        msg,
                        "texto",
                        "mensagem",
                        "conteudo"
                );

                String remetente = getText(
                        msg,
                        "remetente",
                        "tipoRemetente"
                );

                String data = getText(
                        msg,
                        "dataHora",
                        "dataEnvio"
                );

                Integer item = null;

                JsonNode itemNode =
                        msg.path("numeroItem");

                if (!itemNode.isMissingNode()
                        && !itemNode.isNull()) {

                    item = itemNode.asInt();
                }

                salvar(
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
    // SALVAR H2
    // =========================
    private static void salvar(
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
                            "MERGE INTO CHAT "
                                    + "(ID, TEXTO, REMETENTE, DATA, ITEM) "
                                    + "VALUES (?, ?, ?, ?, ?)"
                    );

            ps.setString(1, id);
            ps.setString(2, texto);
            ps.setString(3, remetente);
            ps.setString(4, data);
            ps.setObject(5, item);

            ps.execute();

            System.out.println(
                    "💾 SALVO: "
                            + texto
            );

        } catch (Exception e) {

            System.out.println(
                    "⚠️ ERRO SALVANDO"
            );

            e.printStackTrace();
        }
    }

    // =========================
    // CRIAR TABELA
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
                "CREATE TABLE IF NOT EXISTS CHAT ("
                        + "ID VARCHAR PRIMARY KEY, "
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

            JsonNode n = node.get(c);

            if (n != null
                    && !n.isNull()) {

                return n.asText();
            }
        }

        return "";
    }
}