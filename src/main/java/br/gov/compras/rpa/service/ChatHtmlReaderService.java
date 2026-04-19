package br.gov.compras.rpa.service;

import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class ChatHtmlReaderService {

    private static final List<String> NOISE_EXACT_MATCHES = List.of(
            "chat",
            "compras",
            "comprasgov",
            "menu",
            "sair"
    );

    public List<String> extractMessages(String html) {
        String cleaned = removeScriptsAndStyles(html == null ? "" : html);
        String normalizedBreaks = cleaned
                .replaceAll("(?i)</(div|p|li|tr|td|section|article|h\\d)>", "\n")
                .replaceAll("(?i)<br\\s*/?>", "\n");

        return Arrays.stream(HtmlUtils.htmlUnescape(normalizedBreaks)
                        .replaceAll("<[^>]+>", " ")
                        .split("\\R+"))
                .map(line -> line.replaceAll("\\s+", " ").trim())
                .filter(line -> !line.isBlank())
                .filter(line -> line.length() > 5)
                .filter(line -> !NOISE_EXACT_MATCHES.contains(line.toLowerCase(Locale.ROOT)))
                .distinct()
                .toList();
    }

    private String removeScriptsAndStyles(String html) {
        return html
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ");
    }
}
