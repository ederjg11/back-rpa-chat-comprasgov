package br.gov.compras.rpa.service;

import br.gov.compras.rpa.model.MonitoredClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

@Service
public class WhatsAppNotificationService implements NotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WhatsAppNotificationService.class);

    private final RestClient restClient;
    private final String webhookUrl;

    public WhatsAppNotificationService(RestClient.Builder restClientBuilder,
                                       @Value("${app.notifications.whatsapp.webhook-url:}") String webhookUrl) {
        this.restClient = restClientBuilder.build();
        this.webhookUrl = webhookUrl;
    }

    @Override
    public void notifyClientMention(MonitoredClient client, String message) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            LOGGER.info("WhatsApp webhook not configured. Notification skipped for client {}", client.getName());
            return;
        }

        try {
            restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "clientName", client.getName(),
                            "cnpj", client.getCnpj(),
                            "message", message
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException ex) {
            LOGGER.error("Failed to send WhatsApp notification for client {}", client.getName(), ex);
        }
    }
    public void envioMessagem(String groupAdmin, String groupName, String message)  {
                try {
                    sendGroupMessage(groupAdmin, groupName,message);
                } catch (Exception e) {
                    e.printStackTrace();
                }

        }

    public  static void sendGroupMessage(String groupAdmin, String groupName, String message) throws Exception {
        // TODO: Should have used a 3rd party library to make a JSON string from an object
        System.out.println("*****************DENTRO DO METODO WHATS*******************************");
        System.out.println("*****************DENTRO DO METODO WHATS MENSAGEM*******************************");
        System.out.println(message);


        String WA_GATEWAY_URL = "http://api.whatsmate.net/v3/whatsapp/group/text/message/" + 42;
        String jsonPayload = new StringBuilder()
                .append("{")
                .append("\"group_admin\":\"")
                .append(groupAdmin)
                .append("\",")
                .append("\"group_name\":\"")
                .append(groupName)
                .append("\",")
                .append("\"message\":\"")
                .append(message)
                .append("\"")
                .append("}")
                .toString();


        URL url = new URL(WA_GATEWAY_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("X-WM-CLIENT-ID", "ederjg11@gmail.com");
        conn.setRequestProperty("X-WM-CLIENT-SECRET","064289ece30f471a8b3e48f462c49ca5");
        conn.setRequestProperty("Content-Type", "application/json");

        OutputStream os = conn.getOutputStream();
        os.write(jsonPayload.getBytes());
        os.flush();
        os.close();

        int statusCode = conn.getResponseCode();
        System.out.println("*****************ENVIO WHATS METODO*******************************");

        System.out.println("Response from WA Gateway: \n");
        System.out.println("Status Code: " + statusCode);
        BufferedReader br = new BufferedReader(new InputStreamReader(
                (statusCode == 200) ? conn.getInputStream() : conn.getErrorStream()
        ));
        String output;
        while ((output = br.readLine()) != null) {
            System.out.println(output);
        }
        conn.disconnect();
    }

}
