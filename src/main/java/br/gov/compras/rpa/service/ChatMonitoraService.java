package br.gov.compras.rpa.service;
import br.gov.compras.rpa.model.ChatMensagem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;

import okhttp3.*;
import okhttp3.Request;
import okhttp3.Response;

import java.util.*;
import java.util.stream.Collectors;
public class ChatMonitoraService {
    private static final String URL_LOGIN = "https://www.comprasnet.gov.br/";
    private static final String URL_CHAT = "https://cnetmobile.estaleiro.serpro.gov.br/comprasnet-mensagem/v2/chat/16019905000352023?size=10&page=0";

    private final ObjectMapper mapper = new ObjectMapper();
    private String cookieHeader;

    // =========================
    // LOGIN AUTOMÁTICO
    // =========================
    public void login(String cpf, String senha) {

        Playwright playwright = Playwright.create();
        Browser browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(false)
        );

        BrowserContext context = browser.newContext();
        Page page = context.newPage();

        System.out.println("🔥 Abrindo página login...");
        page.navigate(URL_LOGIN);

        // CPF
        page.fill("#accountId", cpf);
        page.click("#enter-account-id");

        page.waitForTimeout(2000);

        // SENHA
        page.fill("#password", senha);
        page.click("#submit-button");

        page.waitForTimeout(5000);

        System.out.println("✅ Login realizado!");

        // CAPTURA COOKIES
        List<Cookie> cookies = context.cookies();

        String cookieHeader = cookies.stream()
                .map(c -> c.name + "=" + c.value)
                .collect(Collectors.joining("; "));

        System.out.println("🍪 Cookies capturados:");
        System.out.println(cookieHeader);
    }

    // =========================
    // BUSCAR CHAT
    // =========================
    public List<ChatMensagem> buscarMensagens() throws Exception {

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(URL_CHAT)
                .addHeader("Cookie", cookieHeader)
                .addHeader("Accept", "application/json")
                .build();

        Response response = client.newCall(request).execute();

        String json = response.body().string();

        System.out.println("📩 JSON recebido:");
        System.out.println(json);

        return mapper.readValue(json, new TypeReference<List<ChatMensagem>>() {});
    }

    // =========================
    // MONITORAMENTO
    // =========================
    public void monitorar() throws Exception {

        Set<String> mensagensJaProcessadas = new HashSet<>();

        while (true) {

            List<ChatMensagem> mensagens = buscarMensagens();

            for (ChatMensagem msg : mensagens) {

                if (!mensagensJaProcessadas.contains(msg.getTexto())) {

                    mensagensJaProcessadas.add(msg.getTexto());

                    System.out.println("🚨 NOVA MENSAGEM:");
                    System.out.println(msg.getTexto());

                    // REGRA
                    if (msg.getTexto().toLowerCase().contains("retorno")) {
                        System.out.println("🔥 OPORTUNIDADE DETECTADA!");

                        // FUTURO: enviar Whats aqui
                    }
                }
            }

            Thread.sleep(5000);
        }
    }

}
