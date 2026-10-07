package br.gov.compras.rpa.service;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;

import java.util.List;
import java.util.stream.Collectors;

public class LoginGovComprasNetService {

    private Browser browser;
    private BrowserContext context;
    private Page page;

    private String cookieHeader;

    public String login() {

        Playwright playwright = Playwright.create();

        // 🔥 CAMINHO DO CHROME (MAC)
        String userDataDir = System.getProperty("user.home") +
                "/Library/Application Support/Google/Chrome";

        BrowserContext context = playwright.chromium().launchPersistentContext(
                java.nio.file.Paths.get(userDataDir),

                new BrowserType.LaunchPersistentContextOptions()
                        .setChannel("chrome") // 🔥 USA CHROME REAL
                        .setHeadless(false)
                        .setArgs(java.util.List.of(
                                "--start-maximized"
                        ))
        );

        Page page = context.pages().isEmpty()
                ? context.newPage()
                : context.pages().get(0);

        System.out.println("🔥 Abrindo ComprasNet já com perfil real...");
        page.navigate("https://www.comprasnet.gov.br/seguro/landing_sso.asp");

        page.waitForTimeout(5000);

        System.out.println("✅ Página carregada!");

        // 🔥 CAPTURA COOKIES
        List<Cookie> cookies = context.cookies();

        cookieHeader = cookies.stream()
                .map(c -> c.name + "=" + c.value)
                .collect(Collectors.joining("; "));

        System.out.println("🍪 Cookies capturados:");
        System.out.println(cookieHeader);

        return cookieHeader;
    }

    public Page getPage() {
        return page;
    }

    public BrowserContext getContext() {
        return context;
    }

    public void close() {
        if (browser != null) {
            browser.close();
        }
    }
}