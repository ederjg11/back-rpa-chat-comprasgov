package br.gov.compras.rpa.service;

import br.gov.compras.rpa.model.Oportunidade;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;


//TO DO: QUANDO O BOT PEGAR A 1 MENSAGEM JA VALIDA SE TA NO BANCO E SE SIM JA ABORTA
//E ENVIA UMA MENSAGEM NOVA DE WHATS SE HOUVER UMA NOVA MENSAGEM
public class RPAChatComprasNetFull {

    // ================================================================
    // LOGIN
    // ================================================================
    private static final String CPF   = "06303344127";
    private static final String SENHA = "Rfmh05046@";

    // ================================================================
    // SESSION
    // ================================================================
    private static final String SESSION_FILE = "session.json";

    // ================================================================
    // BANCO MYSQL — lido do application.properties via System.getProperty
    // Configure no application.properties:
    //   db.url=jdbc:mysql://SEU_HOST:3306/vista?serverTimezone=America/Sao_Paulo
    //   db.user=SEU_USUARIO
    //   db.password=SUA_SENHA
    // ================================================================
    private static String DB_URL;
    private static String DB_USER;
    private static String DB_PASSWORD;

    // ================================================================
    // WHATSAPP — valores fixos conforme solicitado
    // ================================================================
    private static final String WHATS_NUMERO = "5534996461183";
    private static final String WHATS_GRUPO  = "ChatComprasGov";

    // ================================================================
    // ÚLTIMO JSON DO CHAT (compartilhado com o listener)
    // ================================================================
    private static volatile String ULTIMO_JSON_CHAT = "";
    private static boolean novaMensagem = false;

    // ================================================================
    // MAIN
    // ================================================================
    public static void main(String[] args) {

        try {
            //variavel de envio quando há uma nova mensagem

            // Carrega configuracoes do banco
            carregarConfigBanco();

            // Inicia H2 para debug local (pode comentar em producao)
            org.h2.tools.Server
                    .createWebServer("-web", "-webPort", "8082")
                    .start();

            // PASSO 1: Busca oportunidades do banco MySQL
            List<Oportunidade> oportunidades = buscarOportunidadesDoBanco();

            if (oportunidades.isEmpty()) {
                System.out.println("Nenhuma oportunidade encontrada no banco. Encerrando.");
                return;
            }

            System.out.println("Total de oportunidades carregadas: " + oportunidades.size());

            // Playwright
            Playwright playwright = Playwright.create();

            Browser browser = playwright.firefox().launch(
                    new BrowserType.LaunchOptions()
                            .setHeadless(false)
                            .setSlowMo(3600)
            );

            BrowserContext context;

            if (Files.exists(Paths.get(SESSION_FILE))) {
                context = browser.newContext(
                        new Browser.NewContextOptions()
                                .setStorageStatePath(Paths.get(SESSION_FILE))
                );
                System.out.println("Sessao carregada");
            } else {
                context = browser.newContext();
                System.out.println("Nova sessao");
            }

            ObjectMapper mapper = new ObjectMapper();

            // ================================================================
            // LISTENER — captura o JSON do chat
            // ================================================================
            context.onResponse(response -> {
                try {
                    if (!response.url().contains("/v2/chat")) return;

                    System.out.println("CHAT RESPONSE: " + response.url());
                    System.out.println("STATUS: " + response.status());

                    String body = response.text();
                    if (body == null || body.isEmpty()) {
                        System.out.println("BODY VAZIO");
                        return;
                    }

                    ULTIMO_JSON_CHAT = body;
                    System.out.println("JSON RECEBIDO");

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            Page page = context.newPage();

            // Login
            realizarLogin(context, page);

            // Abre portal
            Page finalPage = page;
            Page portalPage = context.waitForPage(() -> {
                finalPage.navigate(
                        "https://www.comprasnet.gov.br/assinadas/dispensa_eletronica.asp"
                );
            });

            page = portalPage;
            page.waitForLoadState();
            page.waitForTimeout(10000);

            // ================================================================
            // LOOP OPORTUNIDADES
            // ================================================================
            for (int indice = 0; indice < oportunidades.size(); indice++) {
                novaMensagem = false;
                Oportunidade oportunidade = oportunidades.get(indice);

                try {
                    System.out.println("\n==================================");
                    System.out.println("PROCESSANDO OPORTUNIDADE");
                    System.out.println("CLIENTE: "  + oportunidade.getCliente());
                    System.out.println("UASG: "     + oportunidade.getUasg());
                    System.out.println("PREGAO: "   + oportunidade.getNumeroPregao());
                    System.out.println("==================================");

                    if (indice == 0) {

                        // Primeira oportunidade — navega pela listagem
                        page.locator(".cp-itens-card").first().waitFor(
                                new Locator.WaitForOptions()
                                        .setTimeout(30000)
                                        .setState(WaitForSelectorState.VISIBLE)
                        );

                        List<Locator> cards = page.locator(".cp-itens-card").all();
                        System.out.println("TOTAL CARDS: " + cards.size());

                        boolean encontrou = false;

                        for (Locator card : cards) {
                            try {
                                String texto = card.innerText();
                                if (texto == null) continue;

                                if (texto.contains(oportunidade.getNumeroPregao())
                                        && texto.contains(oportunidade.getUasg())) {

                                    encontrou = true;
                                    System.out.println("OPORTUNIDADE ENCONTRADA");
                                    card.locator("button:has(i.fa-plus-square)").first().click();
                                    page.waitForTimeout(8000);
                                    break;
                                }

                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        if (!encontrou) {
                            System.out.println("OPORTUNIDADE NAO ENCONTRADA");
                            continue;
                        }

                    } else {

                        // Demais oportunidades — URL direta
                        System.out.println("ABRINDO VIA URL DIRETA");

                        String uasg = String.format("%06d",
                                Integer.parseInt(oportunidade.getUasg()));

                        String pregao = oportunidade.getNumeroPregao()
                                .replace("/", "").replace("-", "").trim();

                        String numeroCompra = uasg + "05" + pregao;

                        String urlCompra =
                                "https://cnetmobile.estaleiro.serpro.gov.br/comprasnet-web/seguro/" +
                                        "fornecedor/acompanhamento-compra?compra=" + numeroCompra;

                        System.out.println(urlCompra);

                        // Fecha chat se aberto
                        Locator fecharChat = page.locator(
                                "button[aria-label='Close'], button.p-dialog-header-icon");
                        if (fecharChat.count() > 0) {
                            try {
                                fecharChat.first().click();
                                page.waitForTimeout(3000);
                            } catch (Exception ignored) {}
                        }

                        page.navigate(urlCompra);
                        page.waitForLoadState(LoadState.NETWORKIDLE);
                        page.waitForTimeout(12000);
                        System.out.println("DETALHE CARREGADO");
                    }

                    // Reload
                    page.reload();
                    page.waitForLoadState(LoadState.NETWORKIDLE);
                    page.waitForTimeout(10000);

                    // Abre chat
                    Locator chatBtn = page.locator(
                            "app-botao-mensagens-da-compra button").last();
                    chatBtn.waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE));
                    chatBtn.click();
                    page.waitForLoadState(LoadState.NETWORKIDLE);
                    page.waitForTimeout(10000);
                    System.out.println("CHAT ABERTO");

                    // Captura mensagens paginadas
                    capturarMensagensPaginadasComInteracao(oportunidade, page, mapper);
                    dispararWhatsAppNovaMsg(oportunidade,novaMensagem);

                } catch (Exception e) {
                    System.out.println("ERRO OPORTUNIDADE");
                    e.printStackTrace();
                }
            }

            System.out.println("\nFINALIZADO");
            deletarSessao();
            Thread.sleep(999999999);

        } catch (Exception e) {
            deletarSessao();
            e.printStackTrace();
        }
    }

    private static void dispararWhatsAppNovaMsg(Oportunidade oportunidade, boolean novaMensagem) {
        String mensagem =
                "AVISO: Há novas mensagens no chat do edital "
                        + oportunidade.getNumeroEdital()
                        + "do cliente " + oportunidade.getCliente()
                        + " Favor verificar!";

        System.out.println("DISPARO WHATSAPP NOVA MENSAGEM: " + mensagem);
/*
        try {
            WhatsAppNotificationService.sendGroupMessage(
                    WHATS_NUMERO,
                    WHATS_GRUPO,
                    mensagem
            );
        } catch (Exception e) {
            System.out.println("Erro ao enviar WhatsApp: " + e.getMessage());
            e.printStackTrace();
        }

 */

    }

    // ================================================================
    // PASSO 1: BUSCA OPORTUNIDADES DO BANCO MYSQL
    // ================================================================
    private static List<Oportunidade> buscarOportunidadesDoBanco() {

        List<Oportunidade> lista = new ArrayList<Oportunidade>();

        String sql =
                "SELECT O.ID, O.CLIENTE_ID, CLI.CNPJ, CLI.RAZAO_SOCIAL, CLI.FANTASIA, " +
                        "       O.MODALIDADE_ID, O.NUMERO_EDITAL, O.UASG_OC, O.DATA_CERTAME, " +
                        "       O.STATUS, CLI.FANTASIA AS CLIENTE, O.UASG_OC AS UASG, " +
                        "       O.NUMERO_EDITAL AS NUMERO_PREGAO " +
                        "FROM vista.VISTA_OPORTUNIDADE O " +
                        "JOIN vista.VISTA_CLIENTE CLI ON CLI.ID = O.CLIENTE_ID " +
                        "WHERE O.PLATAFORMA_ID IN (1, 35) " +
                        "  AND O.UASG_OC IS NOT NULL " +
                        " AND O.ID IN (9377,9383,9381,9379) " +
                        "  AND O.STATUS NOT IN ('F', 'C', 'H', 'S') " +
                        "ORDER BY O.ID DESC";

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            conn = getConexaoMySQL();
            ps   = conn.prepareStatement(sql);
            rs   = ps.executeQuery();

            while (rs.next()) {

                // Extrai campos do banco
                Long      id   = rs.getLong("ID");
                Long      clienteId   = rs.getLong("CLIENTE_ID");
                String    cnpj        = rs.getString("CNPJ");
                String    razaoSocial = rs.getString("RAZAO_SOCIAL");
                String    fantasia    = rs.getString("FANTASIA");
                Long      modalidade  = rs.getLong("MODALIDADE_ID");
                String    numeroEdital= rs.getString("NUMERO_EDITAL");
                String    uasgOc      = rs.getString("UASG_OC");
                String    status      = rs.getString("STATUS");

                // DATA_CERTAME pode ser null
                LocalDate dataCertame = null;
                java.sql.Date sqlDate = rs.getDate("DATA_CERTAME");
                if (sqlDate != null) dataCertame = sqlDate.toLocalDate();

                // Cliente: usa fantasia ou razao social
                String cliente = (fantasia != null && !fantasia.trim().isEmpty())
                        ? fantasia.trim() : razaoSocial;

                // UASG e numero pregao vem do mesmo campo UASG_OC e NUMERO_EDITAL
                String uasg        = uasgOc;

                // Converte numero do banco (ex: "900332024") para formato do card (ex: "90033/2024")
                // Regra: ultimos 4 digitos = ano, restante = numero do pregao
                String numeroPregao = formatarNumeroPregao(numeroEdital);

                lista.add(new Oportunidade(
                        id, clienteId, cnpj, razaoSocial, fantasia,
                        modalidade, numeroEdital, uasgOc, dataCertame,
                        status, cliente, uasg, numeroPregao
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

    // ================================================================
    // LOGIN (sem alteracao)
    // ================================================================
    private static void realizarLogin(BrowserContext context, Page page) throws Exception {

        page.navigate(
                "https://www.comprasnet.gov.br/seguro/loginFornecedorSelecEmpresa.asp");
        page.waitForTimeout(5000);

        if (page.locator("text=Entrar com Gov.br").count() > 0) {

            System.out.println("Fazendo login");
            page.locator("text=Entrar com Gov.br").click();
            page.waitForLoadState();

            page.fill("input[name='accountId'], #accountId", CPF);
            page.locator("#enter-account-id").click();
            page.waitForTimeout(3000);

            page.fill("input[name='password'], #password", SENHA);
            page.locator("#submit-button").click();
            page.waitForTimeout(10000);

            context.storageState(new BrowserContext.StorageStateOptions()
                    .setPath(Paths.get(SESSION_FILE)));
            System.out.println("Sessao salva");
        }

        if (page.locator("text=MEDIPHACOS INDUSTRIAS MEDICAS S/A").count() > 0) {
            page.locator("text=MEDIPHACOS INDUSTRIAS MEDICAS S/A").first().click();
            page.locator("input[type='submit'], button:has-text('Confirmar')").click();
            page.waitForTimeout(8000);
        }
    }

    // ================================================================
    // PAGINACAO CHAT (sem alteracao na logica, ajustado o processamento)
    // ================================================================
   /*
    private static void capturarMensagensPaginadasComInteracao(
            Oportunidade oportunidade,
            Page page,
            ObjectMapper mapper) {

        try {
            // Pagina 1
            if (ULTIMO_JSON_CHAT != null && !ULTIMO_JSON_CHAT.isEmpty()) {
                processarMensagens(oportunidade, ULTIMO_JSON_CHAT, mapper);
            }

            int totalPages = 15;

            for (int i = 2; i <= totalPages; i++) {

                try {
                    Locator pageButton = page.locator(
                            "button[data-pc-section='page'][aria-label='Página " + i + "']");

                    if (pageButton.count() == 0) {
                        System.out.println("PAGINA NAO ENCONTRADA " + i);
                        continue;
                    }

                    System.out.println("INDO PAGINA " + i);
                    ULTIMO_JSON_CHAT = "";
                    pageButton.first().click();
                    page.waitForTimeout(8000);

                    boolean captcha = page.locator(
                            "text=Não foi possível realizar a validação do Captcha").count() > 0;

                    if (captcha) {
                        System.out.println("CAPTCHA DETECTADO");
                        break;
                    }

                    if (ULTIMO_JSON_CHAT != null && !ULTIMO_JSON_CHAT.isEmpty()) {
                        processarMensagens(oportunidade, ULTIMO_JSON_CHAT, mapper);
                    } else {
                        System.out.println("JSON NAO RECEBIDO PAGINA " + i);
                    }

                } catch (Exception e) {
                    System.out.println("ERRO PAGINA " + i);
                    e.printStackTrace();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    */


    // ================================================================
    // PAGINACAO CHAT — CORRIGIDO
    // Usa JavaScript para clicar nos botoes de pagina.
    // Evita o TimeoutError causado pelo elemento nao estavel/visivel
    // que ocorria a partir da pagina 3 com pageButton.first().click()
    // ================================================================
    private static void capturarMensagensPaginadasComInteracao(
            Oportunidade oportunidade,
            Page page,
            ObjectMapper mapper) {

        try {
            // Pagina 1 — JSON ja capturado pelo listener
            if (ULTIMO_JSON_CHAT != null && !ULTIMO_JSON_CHAT.isEmpty()) {
                processarMensagens(oportunidade, ULTIMO_JSON_CHAT, mapper);
            }

            // Descobre quantas paginas existem dinamicamente
            int totalPaginas = descobrirTotalPaginas(page);
            System.out.println("Total de paginas do chat: " + totalPaginas);

            for (int i = 2; i <= totalPaginas; i++) {
                try {
                    System.out.println("INDO PAGINA " + i);
                    ULTIMO_JSON_CHAT = "";

                    // CORRECAO: clique via JavaScript — ignora visibilidade/estabilidade
                    boolean clicou = clicarPaginaViaJS(page, i);

                    if (!clicou) {
                        System.out.println("PAGINA " + i + " NAO ENCONTRADA NO PAGINADOR");
                        break;
                    }

                    page.waitForTimeout(6000);

                    boolean captcha = page.locator(
                            "text=Não foi possível realizar a validação do Captcha").count() > 0;

                    if (captcha) {
                        System.out.println("CAPTCHA DETECTADO NA PAGINA " + i);
                        break;
                    }

                    if (ULTIMO_JSON_CHAT != null && !ULTIMO_JSON_CHAT.isEmpty()) {
                        processarMensagens(oportunidade, ULTIMO_JSON_CHAT, mapper);
                    } else {
                        System.out.println("JSON NAO RECEBIDO PAGINA " + i);
                    }

                } catch (Exception e) {
                    System.out.println("ERRO PAGINA " + i + ": " + e.getMessage());
                    // Continua para a proxima — nao aborta o processo todo
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ================================================================
    // CLIQUE VIA JAVASCRIPT NO BOTAO DE PAGINACAO
    // Resolve o TimeoutError: o JS clica diretamente no DOM,
    // sem aguardar o elemento ficar visivel/estavel como o Playwright faz
    // Retorna true se encontrou e clicou, false se nao encontrou
    // ================================================================
    private static boolean clicarPaginaViaJS(Page page, int numeroPagina) {
        try {
            Object resultado = page.evaluate(
                    "function(num) {" +
                            // Tenta pelos seletores mais comuns (com e sem acento em Pagina)
                            "  var seletores = [" +
                            "    'button[data-pc-section=\"page\"][aria-label=\"P\u00e1gina ' + num + '\"]'," +
                            "    'button[data-pc-section=\"page\"][aria-label=\"Pagina ' + num + '\"]'," +
                            "    'button.p-paginator-page[aria-label=\"P\u00e1gina ' + num + '\"]'," +
                            "    'button.p-paginator-page[aria-label=\"Pagina ' + num + '\"]'" +
                            "  ];" +
                            "  for (var s = 0; s < seletores.length; s++) {" +
                            "    var btns = document.querySelectorAll(seletores[s]);" +
                            "    for (var b = 0; b < btns.length; b++) {" +
                            "      if (btns[b]) { btns[b].click(); return true; }" +
                            "    }" +
                            "  }" +
                            // Fallback: busca pelo texto visivel do botao
                            "  var todos = document.querySelectorAll('button[data-pc-section=\"page\"]');" +
                            "  for (var t = 0; t < todos.length; t++) {" +
                            "    if (todos[t].textContent.trim() === String(num)) {" +
                            "      todos[t].click(); return true;" +
                            "    }" +
                            "  }" +
                            "  return false;" +
                            "}",
                    numeroPagina
            );
            return Boolean.TRUE.equals(resultado);
        } catch (Exception e) {
            System.out.println("Erro ao clicar pagina " + numeroPagina + " via JS: " + e.getMessage());
            return false;
        }
    }

    // ================================================================
    // DESCOBRE TOTAL DE PAGINAS DO PAGINADOR DINAMICAMENTE
    // Pega o maior numero encontrado nos botoes de pagina
    // ================================================================
    private static int descobrirTotalPaginas(Page page) {
        try {
            Object resultado = page.evaluate(
                    "function() {" +
                            "  var botoes = document.querySelectorAll('button[data-pc-section=\"page\"]');" +
                            "  var max = 1;" +
                            "  for (var i = 0; i < botoes.length; i++) {" +
                            "    var num = parseInt(botoes[i].textContent.trim(), 10);" +
                            "    if (!isNaN(num) && num > max) max = num;" +
                            "  }" +
                            "  return max;" +
                            "}"
            );
            if (resultado instanceof Number) {
                return ((Number) resultado).intValue();
            }
        } catch (Exception e) {
            System.out.println("Erro ao descobrir total de paginas: " + e.getMessage());
        }
        return 1; // fallback: assume pagina unica
    }

    // ================================================================
    // PROCESSAR JSON — salva no banco e dispara WhatsApp se necessario
    // ================================================================
    private static void processarMensagens(
            Oportunidade oportunidade,
            String body,
            ObjectMapper mapper) {

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
                if (id == null || id.isEmpty()) continue;

                String texto     = getText(msg, "texto", "mensagem", "conteudo");
                String remetente = getText(msg, "remetente", "tipoRemetente");
                String data      = getText(msg, "dataHora", "dataEnvio");

                Integer item = null;
                JsonNode itemNode = msg.path("numeroItem");
                if (!itemNode.isMissingNode() && !itemNode.isNull()) {
                    item = itemNode.asInt();
                }

                // PASSO 3: Insere em VISTA_PREGAO_CHAT sem duplicar
                boolean inserido = salvarPregaoChat(
                        id, texto, remetente, data, item, oportunidade);

                if (!inserido) break; // ja existia no banco, pára o processo de capturar

                // PASSO 2: Verifica se menciona cliente e dispara WhatsApp
                if (textoMencionaCliente(texto, oportunidade)) {
                    dispararWhatsApp(oportunidade);
                }
            }

        } catch (Exception e) {
            System.out.println("ERRO PROCESSANDO JSON");
            e.printStackTrace();
        }
    }

    // ================================================================
    // PASSO 3: SALVA EM VISTA_PREGAO_CHAT (sem duplicar)
    // Retorna true se inseriu, false se ja existia
    // ================================================================
    private static boolean salvarPregaoChat(
            String chaveMensagem,
            String texto,
            String remetente,
            String dataStr,
            Integer itemLote,
            Oportunidade oportunidade) {

        Connection conn = null;
        PreparedStatement psCheck = null;
        PreparedStatement psInsert = null;
        ResultSet rs = null;

        try {
            conn = getConexaoMySQL();

            // Verifica duplicidade pela mensagem + oportunidade
            // Usa MENSAGEM + OPORTUNIDADE_ID como chave de negocio
            psCheck = conn.prepareStatement(
                    "SELECT ID FROM VISTA_PREGAO_CHAT " +
                            "WHERE MENSAGEM = ? AND OPORTUNIDADE_ID = ? AND DATA_CHAT_PREGOEIRO = ?"
            );
            psCheck.setString(1, truncar(montarMensagemChat(remetente, texto), 2000));
            psCheck.setLong  (2, oportunidade.getId());
            psCheck.setString  (3, truncar(dataStr, 100));
            rs = psCheck.executeQuery();

            System.out.println("Oportunidade: " + oportunidade.getId());
            System.out.println("O texto: " + truncar(montarMensagemChat(remetente, texto), 2000));
            if (rs.next()) {
                // Ja existe
                return false;
            }

            rs.close(); psCheck.close();

            // Converte data
            LocalDateTime dataChat = parsearDataChat(dataStr);

            // Insere
            psInsert = conn.prepareStatement(
                    "INSERT INTO vista.VISTA_PREGAO_CHAT " +
                            "(DATA_CHAT_PREGOEIRO, DATA_CHAT, MENSAGEM, OPORTUNIDADE_ID, ITEM_LOTE, DATA_CADASTRO) " +
                            "VALUES (?, ?, ?, ?, ?, NOW())"
            );

            psInsert.setString(1, truncar(dataStr, 100));

            if (dataChat != null) {
                psInsert.setTimestamp(2, Timestamp.valueOf(dataChat));
            } else {
                psInsert.setNull(2, Types.TIMESTAMP);
            }

            psInsert.setString(3, truncar(montarMensagemChat(remetente, texto), 2000));
            psInsert.setLong  (4, oportunidade.getId());

            if (itemLote != null) {
                psInsert.setLong(5, itemLote.longValue());
            } else {
                psInsert.setNull(5, Types.BIGINT);
            }

            psInsert.executeUpdate();

            System.out.println("SALVO: [" + remetente + "] "
                    + (texto != null ? texto.substring(0, Math.min(60, texto.length())) : ""));
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

    // ================================================================
    // PASSO 2: VERIFICA SE MENSAGEM MENCIONA O CLIENTE
    // Checa: nome fantasia, razao social, CNPJ com e sem mascara
    // ================================================================
    private static boolean textoMencionaCliente(String texto, Oportunidade oportunidade) {

        if (texto == null || texto.trim().isEmpty()) return false;

        String textoUpper = texto.toUpperCase().trim();

        // Verifica nome fantasia
        String fantasia = oportunidade.getFantasia();
        if (fantasia != null && !fantasia.trim().isEmpty()) {
            if (textoUpper.contains(fantasia.toUpperCase().trim())) return true;
        }

        // Verifica razao social
        String razao = oportunidade.getRazaoSocial();
        if (razao != null && !razao.trim().isEmpty()) {
            // Pega a primeira palavra significativa da razao social (evita falso positivo em palavras comuns)
            String[] palavras = razao.toUpperCase().trim().split("\\s+");
            if (palavras.length > 0 && palavras[0].length() >= 4) {
                if (textoUpper.contains(palavras[0])) return true;
            }
            if (textoUpper.contains(razao.toUpperCase().trim())) return true;
        }

        // Verifica cliente (nome curto)
        String cliente = oportunidade.getCliente();
        if (cliente != null && !cliente.trim().isEmpty() && cliente.length() >= 4) {
            if (textoUpper.contains(cliente.toUpperCase().trim())) return true;
        }

        // Verifica CNPJ — sem mascara (somente numeros)
        String cnpj = oportunidade.getCnpj();
        if (cnpj != null && !cnpj.trim().isEmpty()) {
            String cnpjNumeros = cnpj.replaceAll("[^0-9]", "");
            if (!cnpjNumeros.isEmpty() && textoUpper.contains(cnpjNumeros)) return true;

            // Com mascara: XX.XXX.XXX/XXXX-XX
            if (cnpjNumeros.length() == 14) {
                String cnpjMascarado =
                        cnpjNumeros.substring(0, 2)  + "." +
                                cnpjNumeros.substring(2, 5)  + "." +
                                cnpjNumeros.substring(5, 8)  + "/" +
                                cnpjNumeros.substring(8, 12) + "-" +
                                cnpjNumeros.substring(12);
                if (textoUpper.contains(cnpjMascarado)) return true;
            }
        }

        return false;
    }

    // ================================================================
    // PASSO 2: DISPARA WHATSAPP
    // ================================================================
    private static void dispararWhatsApp(Oportunidade oportunidade) {

        String mensagem =
                "URGENTE: Houve alteracoes no chat do edital "
                        + oportunidade.getNumeroEdital()
                        + "do cliente " + oportunidade.getCliente()
                        + " Favor verificar!";

        System.out.println("DISPARO WHATSAPP: " + mensagem);

        try {
            WhatsAppNotificationService.sendGroupMessage(
                    WHATS_NUMERO,
                    WHATS_GRUPO,
                    mensagem
            );
        } catch (Exception e) {
            System.out.println("Erro ao enviar WhatsApp: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ================================================================
    // UTILITARIOS
    // ================================================================

    /**
     * Converte numero do pregao do formato do banco para o formato exibido no card.
     * Exemplos:
     *   "900332024"  → "90033/2024"
     *   "12024"      → "1/2024"
     *   "90033/2024" → "90033/2024"  (ja formatado, mantém)
     *   null ou ""   → ""
     */
    private static String formatarNumeroPregao(String numeroEdital) {
        if (numeroEdital == null || numeroEdital.trim().isEmpty()) return "";

        String s = numeroEdital.trim();

        // Se ja tem barra, retorna como esta
        if (s.contains("/")) return s;

        // Remove qualquer caractere nao numerico
        String apenasNumeros = s.replaceAll("[^0-9]", "");

        // Precisa ter pelo menos 5 chars: 1 de numero + 4 do ano
        if (apenasNumeros.length() <= 4) return s;

        String ano    = apenasNumeros.substring(apenasNumeros.length() - 4);
        String numero = apenasNumeros.substring(0, apenasNumeros.length() - 4);

        return numero + "/" + ano;
    }

    /** Monta o texto final que vai para o campo MENSAGEM */
    private static String montarMensagemChat(String remetente, String texto) {
        String r = (remetente != null && !remetente.isEmpty()) ? remetente : "N/A";
        String t = (texto     != null && !texto.isEmpty())     ? texto     : "";
        return "[" + r + "] " + t;
    }

    /** Converte string de data do chat para LocalDateTime */
    private static LocalDateTime parsearDataChat(String dataStr) {
        if (dataStr == null || dataStr.trim().isEmpty()) return null;

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
                return LocalDateTime.parse(s,
                        DateTimeFormatter.ofPattern(fmt));
            } catch (DateTimeParseException ignored) {}
        }

        return null;
    }

    private static String truncar(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String getText(JsonNode node, String... campos) {
        for (String c : campos) {
            JsonNode n = node.get(c);
            if (n != null && !n.isNull()) return n.asText();
        }
        return "";
    }

    // ================================================================
    // CONEXAO MYSQL
    // ================================================================
    private static Connection getConexaoMySQL() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    /** Carrega as propriedades do banco de application.properties */
    private static void carregarConfigBanco() throws Exception {
        java.util.Properties props = new java.util.Properties();

        // Tenta carregar do classpath primeiro
        java.io.InputStream is = RPAChatComprasNetFull.class
                .getClassLoader()
                .getResourceAsStream("application.properties");

        if (is == null) {
            // Fallback: arquivo no diretorio de execucao
            java.io.File f = new java.io.File("application.properties");
            if (f.exists()) {
                is = new java.io.FileInputStream(f);
            }
        }

        if (is != null) {
            props.load(is);
            is.close();
        } else {
            throw new RuntimeException(
                    "application.properties nao encontrado. " +
                            "Crie o arquivo com db.url, db.user e db.password."
            );
        }

        DB_URL      = props.getProperty("spring.datasource.url");
        DB_USER     = props.getProperty("spring.datasource.username");
        DB_PASSWORD = props.getProperty("spring.datasource.password");

        if (DB_URL == null || DB_URL.trim().isEmpty()) {
            throw new RuntimeException("Propriedade db.url nao configurada.");
        }

        System.out.println("Banco configurado: " + DB_URL);
    }

    /** Fecha recursos JDBC silenciosamente */
    private static void fechar(ResultSet rs, PreparedStatement ps, Connection conn) {
        if (rs   != null) try { rs.close();   } catch (Exception ignored) {}
        if (ps   != null) try { ps.close();   } catch (Exception ignored) {}
        if (conn != null) try { conn.close();  } catch (Exception ignored) {}
    }

    // ================================================================
    // DELETA O ARQUIVO DE SESSAO AO FINALIZAR
    // Chame este metodo no final do main (sucesso ou erro)
    // ================================================================
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