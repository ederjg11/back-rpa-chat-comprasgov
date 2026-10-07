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
public class RPAChatComprasNetFullTesteV2 {

    // =========================
// LOGIN
// =========================
    private static final String CPF =
            "06303344127";

    private static final String SENHA =
            "Rfmh05046@";

    private static String DB_URL;
    private static String DB_USER;
    private static String DB_PASSWORD;
    private static int countNovaMensagem = 0;
    private static boolean mensagemExiste = false;
    private static final String WHATS_NUMERO = "5534996461183";
    private static final String WHATS_GRUPO = "ChatComprasGov";

    // =========================
// SESSION
// =========================
    private static final String SESSION_FILE =
            "session.json";

    // =========================
// OPORTUNIDADES
// =========================
    private static List<Oportunidade> OPORTUNIDADES;
    /*
    private static final List<Oportunidades> OPORTUNIDADES =
            Arrays.asList(

                    new Oportunidades(
                            "MEDIPHACOS",
                            "155126",
                            "90070/2025"
                    ),

                    new Oportunidades(
                            "MEDIPHACOS",
                            "160199",
                            "90033/2024"
                    ),
                    new Oportunidades(
                            "MEDIPHACOS",
                            "926769",
                            "90183/2025"
                    )
            );

     */

    // =========================
// JSON CHAT
// =========================
    private static volatile String ULTIMO_JSON_CHAT =
            "";

    public void run() {

        try {

            // =========================
            // H2
            // =========================
            /*
            org.h2.tools.Server
                    .createWebServer(
                            "-web",
                            "-webPort",
                            "8082"
                    )
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

                //Toda nova oportunidade o flag de mensagem existente é resetado para evitar que mensagens antigas sejam processadas como novas
                mensagemExiste = false;
                countNovaMensagem = 0;

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
                                System.out.println(" Erro ao processar card, mas continuando: " + e.getMessage());
                                tirarPrintDebug(page, "erro_oportunidade_" + indice + ".png");
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
                                "\n🚀 ABRINDO VIA URL DIRETA DA OPORTUNIDADE: " + oportunidade.getId()
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

                                page.waitForTimeout(8000);

                            } catch (Exception ignored) {
                                System.out.println("ERRO AO ABRIR URL DIRETA, mas continuando: " + ignored.getMessage());
                                tirarPrintDebug(page, "erro_oportunidade_" + indice + ".png");
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

                    //aqui valida mensagem para sair do loop

                    if (mensagemExiste) {
                        System.out.println("Mensagem ja existente no banco, Saindo do loop. Oportunidade: " + oportunidade.getId());
                        mensagemExiste = false;
                        continue;
                    }


                } catch (Exception e) {

                    System.out.println(
                            "❌ ERRO OPORTUNIDADE: " + oportunidade.getId()
                    );
                    tirarPrintDebug(page, "erro_oportunidade_" + indice + ".png");

                    e.printStackTrace();
                }
            }

            System.out.println(
                    "\n✅ FINALIZADO"
            );
            deletarSessao();
            Thread.sleep(999999999);

        } catch (Exception e) {
            System.out.println("ERRO CRITICO: " + e.getMessage());
            deletarSessao();
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
        try {
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
        }catch(Exception e){
            System.out.println("Erro ao carregar pagina de login, mas continuando: " + e.getMessage());
            tirarPrintDebug(page, "erro_login.png");
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
                        mapper,
                        oportunidade
                );
            }

            int totalPages = 15;

            // =========================
            // LOOP PAGINAS
            // =========================
            for (int i = 2;
                 i <= totalPages;
                 i++) {

                System.out.println("Valor mensagemExiste: " + mensagemExiste);
                System.out.println("Pagina: " + i + "oportunidade: " + oportunidade.getId());

                if(mensagemExiste){
                    System.out.println("Mensagem ja existente no banco, Saindo do loop da paginação. Oportunidade: " + oportunidade.getId());
                    mensagemExiste = false;
                    break;
                }

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
                                mapper,
                                oportunidade);

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
            ObjectMapper mapper,
            Oportunidade oportunidade) {

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

                //Método para inserir novas mensagens no banco de dados
                boolean inserido = salvarPregaoChat(id, texto, remetente, data, item, oportunidade);

                if (!inserido) {
                    System.out.println("Mensagem ja existente no banco, ignorando. Oportunidade: " + oportunidade.getId());
                    break;
                } else if (countNovaMensagem == 1) { // Limite de mensagens novas para evitar flood em casos de muitos registros
                    dispararWhatsAppNovaMsg(oportunidade);
                    System.out.println("Nova mensagem enviada por what'up " + texto + " Oportunidade: " + oportunidade.getId());
                }

                if (textoMencionaCliente(texto, oportunidade)) {
                    dispararWhatsApp(oportunidade);
                }

                /*
                //Metodo para salvar no banco h2
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

                 */
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

    private static void fechar(ResultSet rs, PreparedStatement ps, Connection conn) {
        if (rs != null) {
            try {
                rs.close();
            } catch (Exception ignored) {
                System.out.println("Erro ao fechar ResultSet: " + ignored.getMessage());
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
            System.out.println("Nao foi possivel salvar print debug: " + e.getMessage());
        }
    }
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
    private static void deletarSessao() {
        try {
            java.io.File sessao = new java.io.File(SESSION_FILE);
            if (sessao.exists()) {
                boolean deletado = sessao.delete();
                System.out.println(deletado
                        ? "Arquivo de sessao deletado: " + SESSION_FILE
                        : "Nao foi possivel deletar: "   + SESSION_FILE);
            }
        } catch (Exception e) {
            System.out.println("Erro ao deletar sessao: " + e.getMessage());
        }
    }

}