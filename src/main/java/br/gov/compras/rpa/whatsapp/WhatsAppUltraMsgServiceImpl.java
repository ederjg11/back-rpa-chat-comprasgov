package br.gov.compras.rpa.whatsapp;


import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

@Service
public class WhatsAppUltraMsgServiceImpl implements WhatsAppService {

    private static final String INSTANCE = "SEU_INSTANCE_ID";
    private static final String TOKEN = "SEU_TOKEN";

    @Override
    public void enviarMensagem(String telefone, Map<String, Object> dados) {

        try {

            String mensagem = montarMensagem(dados);

            URL url = new URL("https://api.ultramsg.com/" + INSTANCE + "/messages/chat");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setDoOutput(true);

            String body = "token=" + TOKEN +
                    "&to=" + telefone +
                    "&body=" + mensagem;

            OutputStream os = conn.getOutputStream();
            os.write(body.getBytes("UTF-8"));
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();
            System.out.println("📲 Whats enviado! Status: " + responseCode);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String montarMensagem(Map<String, Object> d) {

        return "🚨 *ALERTA DE CHAT - AÇÃO NECESSÁRIA*\n\n" +

                "📢 *Nova mensagem recebida no ComprasNet*\n\n" +

                "🏛️ *Órgão:* " + safe(d.get("ORGAO")) + "\n" +
                "📄 *Edital:* " + safe(d.get("NUMERO_EDITAL")) + "\n" +
                "📍 *Local:* " + safe(d.get("LOCALIDADE_ORGAO")) + "\n\n" +

                "💬 *Mensagem recebida:*\n" +
                "────────────────────\n" +
                limitarTexto(safe(d.get("TEXTO"))) + "\n" +
                "────────────────────\n\n" +

                "⚠️ *Essa mensagem pode exigir resposta imediata.*\n\n" +

                "👉 *Acesse agora o ComprasNet:*\n" +
                safe(d.get("LINK_EDITAL")) + "\n\n" +

                "⚡ _Monitoramento automático SejaVista_";
    }

    private String limitarTexto(String texto) {
        if (texto == null) return "";
        return texto.length() > 300 ? texto.substring(0, 300) + "..." : texto;
    }

    private String safe(Object o) {
        return o == null ? "" : o.toString();
    }
}
