package br.gov.compras.rpa.service;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class ChatHtmlReaderService {

    private static final int MIN_MESSAGE_LENGTH = 5;

    private static final List<String> NOISE_EXACT_MATCHES = List.of(
            "chat",
            "compras",
            "comprasgov",
            "menu",
            "sair"
    );

    public List<String> extractMessages(String html) {
        String safeHtml = html == null ? "" : html;
        String extractedText = Jsoup.parse(safeHtml)
                .body()
                .wholeText();

        return Arrays.stream(extractedText.split("\\R+"))
                .map(line -> line.replaceAll("\\s+", " ").trim())
                .filter(line -> !line.isBlank())
                .filter(line -> line.length() > MIN_MESSAGE_LENGTH)
                .filter(line -> !NOISE_EXACT_MATCHES.contains(line.toLowerCase(Locale.ROOT)))
                .distinct()
                .toList();
    }
}
