package br.gov.compras.rpa.service;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ChatComprasGovService {

    private static final String URL =
            "https://cnetmobile.estaleiro.serpro.gov.br/comprasnet-mensagem/v2/chat/16019905000352023?size=10&page=0";

    // 🔥 COLE SEU TOKEN AQUI (SEM refreshToken, só o principal)
    private static final String TOKEN =
        "eyJhbGciOiJSUzUxMiJ9.eyJzdWIiOiIwNjMwMzM0NDEyNyIsInRpcG8iOiJGIiwiaWRlbnRpZmljYWNhb19mb3JuZWNlZG9yIjoiMjE5OTg4ODUwMDAxMzAiLCJpZF9zZXNzYW8iOjEwODU4MjM1NSwib3JpZ2VtX3Nlc3NhbyI6IlciLCJhdXRlbnRpY2FjYW8iOiJMIiwibml2ZWlzX2NvbmZpYWJpbGlkYWRlIjpbMSwzXSwidGlwb19mb3JuZWNlZG9yIjoiSiIsInBvcnRlX2Zvcm5lY2Vkb3IiOiIwIiwiaWF0IjoxNzc3NDcxNzQ0LCJleHAiOjE3Nzc0NzIzNDR9.RSInSCDVRTL7rY8Hli7Oj2dresVnzyRekLv7FVKGYbx4qxNWYGWR6F7BMVA4Bh762K9bxTfIhRA5rV9NTx-D0shQSAPBrLHIfw_rEQG-crFvutJawnP-o_nBjZHlfmjLhW-58JdyATvBq6jwH5wsbeXbHwnBW9HBumS_0j7GNvIlBXxhIgxiFvURiTQNbrYNqJztR2Oz9Yj9QMmx9L4MRaHvSgZ-qHuZ9l4k4sBcQKV0ioQhjpjfvYZGftDjJouAYpKP-1OR-rbvSuUwMhkaPAM3mskoE8lFSloFMRMYaZHkYGP4ZKPyKNU7IUiLg7w5BYmYdBZFeObxkrBNsxA664u_zN2srKH0MBXmtQetWcdZyRyqwaEtSiW_zui5CX1KUOqQ_xQlRfDQ6w1i-7-HFnv4QIZDnrkUUXe6Ufbm_ncyZoXTjdF2Jx7sRiE8nM9NZNV46-Kyf5pi5IhYnvyLUz7qrK0MfX3CnkdpjecXvkXI8wIMJJMMeFe6TaMR_7YuokRZZ2SYtcIQS0c4UNKo8lZX6ax28muntuItnDCi8PNwEgXL3DD5lDzZ8tc_2Er1WvzneJIn_z7OqFuH2cGj25Q9-yr-ItodRKgvCQvKoUuh7zvZbCuRDxOHsxB9OBz-8dP9JSRu51CrSD2HpzdvGr3d7w03ZkXu8A0G2GGuwog";
    public void buscarMensagens() {

        try {
            OkHttpClient client = new OkHttpClient();

            Request request = new Request.Builder()
                    .url(URL)
                    .addHeader("Authorization", "Bearer " + TOKEN)
                    .addHeader("Accept", "application/json")
                    .build();

            Response response = client.newCall(request).execute();

            System.out.println("Status: " + response.code());

            if (response.isSuccessful()) {
                String body = response.body().string();
                System.out.println("🔥 JSON:");
                System.out.println(body);
            } else {
                System.out.println("Erro ao chamar API");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}