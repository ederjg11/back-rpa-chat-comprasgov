package br.gov.compras.rpa;

import okhttp3.*;

public class TesteRealComprasGov {

    public static void main(String[] args) throws Exception {

        OkHttpClient client = new OkHttpClient();

        String url = "https://cnetmobile.estaleiro.serpro.gov.br/comprasnet-mensagem/v2/chat/16019905900332024?size=10&page=0&legadoAsp=false&captcha=P1_eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.3gAHp3Bhc3NrZXnFBOSG7Dl7chjgidfl-hVumYFlvTG-dzFbTepo18JID-AEt9QJG9K6isbhHDunpM7xAyGPSZoY1kaBHu15Bia9U2RnfCGH0Ndn8nMmgj3EYlUUHr7P3QfvtJwRyHAbaRh8353lIzF6vR7qRxnwtTbgrx02EMAClYcJwzMjSp6sPdO9Bkh7q3Km7ysN6Ca6rGurYrtVuGAcfs5EVrr5l9gmHrnzeymrHxmaP7LxQsLdPLfLbgldrKD98hWEDE_hpENhdrYa-G9YhSvFmOfQCnMK9eaZR97HYYpDClB8wb9p-ERMDe7-kvQ9bb2K2KgBm1Ht96iLI61qco8iOgBqxf0eJnaHXLt6Dp_HQmKt-W60HDF1R2DErRQIGIR2dYUo20ajnJp-SxzZzUPRwPAFSgn2hQRgLoUmM3EbuNjhy2ipIUAj_IqM4WzumndYpXR89P-wmrvkhZyU7_4uPnSdITAAf_pZcnSpLVcrt1XSRKjkBvy9a-oJfun4HKdA8ePsHqaVK-a9MJtFOj1YOJIEFd-62mHla_3p--4pogDIqqQtNEM_lcgcpb-2hCV2Mt0PmcqmK7m9twqbid8LPlHQz9eHELdJj6lpsWS25AQPxTRBcQpz1lYv0TivuAnbAiGf5txcZ9I4jk6Cma86AR-zcvlQJPT20DqAFvKcCcifUL6dsaXJUWnz3eYIVd2xcVzb3C_x93BbcYDtF4jkRB1X855bjfYhLJ7QeQCVixts_ZeotScPaEe_bXQ0Eb0iaVzNGJ58RvYJmsqE1dqnmchY1uvfeLGjsSDoMRqVbwrVmEd5lRcKSMoToBybsevYF5xoG9DFganDil_006W7Ia41ULUQqn9XigmruD9P2MqeGyk_gO6CcSFGKvseTVKcpR2Lk3FKutGQNRDTH0kuxFV1z7SFaiWs6tJGn6s0mIaJYwt5S3HEYBIpF2GjtN8GDSLHFpBy3Y_qcCRI_i89DHrL-C2-lgit0uKMHkETlY7EjxwP2mHrSmFV8y3oucXScueVo1zuMSZM2TwLGQroTi2ZeSgarNIPuWsCSgn1jVJhdny0VFTo1yb8A_jUKQjvxrSP_cMRTTmEXVPG0lip7LgR0AmQ3qkvihgJWhVJjYsyYwuPweqJkE9bSHXIQ7DP3Ow35Oz8wxE_pBnFgvwjWBANCd2wl45w3gpLk1krhR_QZCZR32RljDSDDB0sv-IBIOGalIVvpu9Sn4rOktoqlDYnU93q1ciLiXRIJUU-1zeXJaCKj64Cmlyb2d2lDQ2IEBC2ZKpp4dQ6-XIgVRRopytzXqw7nd_nbg7HhOvfDYKWEjb1YpQQeUIRqTZbGMpJ_7EAQfGmKqp606zrXuqrIB7RLhnhIFcXmGF7CriploU_pFek8JcM41-o4FZy7cOEwpt8qyrzb61oMjtwDtnM9gPNZjg1YUM4zgAC06puUx6r1b3Vwgu0kpXGj6nV13fcxMF-HhKw1LbAPG7FNAgRPpYBYRHwvLJEmOEYGUW-CI41y21XWTC8Vkp8KB-sPTrW6k-lac7WFhBCoWa5TPjmrdvpgHVOfu_zynCciTPBW-W2QagGXqCG2oG7Cd9FwDIhjkZ0FqzCFGKQaFToD57ulnxhmGKS9k3zpR2fZKc2PVzN2dwA0yIWlhLqW_mRYIPkce3Yn6Ww8vYpYlQ5p3NpdGVrZXnZJGI4YmJkZWQxLTlkMDQtNGFjZS05OTUyLWI2N2NkZTA4MWE3YqNleHDOafJ_9qJwZAClY2RhdGHUAACmY2RhdGEy1AAAomtypzRhYWI2Yzc.oIbQmfZ515-NjyTMJBNrbkjwajDzCU7H2y_GdKOuOpw";

        Request request = new Request.Builder()
                .url(url)

                // 🔐 AUTH
                .addHeader("Authorization", "Bearer eyJhbGciOiJSUzUxMiJ9.eyJzdWIiOiIwNjMwMzM0NDEyNyIsInRpcG8iOiJGIiwiaWRlbnRpZmljYWNhb19mb3JuZWNlZG9yIjoiMjE5OTg4ODUwMDAxMzAiLCJpZF9zZXNzYW8iOjEwODY0NjY0Niwib3JpZ2VtX3Nlc3NhbyI6IlciLCJhdXRlbnRpY2FjYW8iOiJMIiwibml2ZWlzX2NvbmZpYWJpbGlkYWRlIjpbMSwzXSwidGlwb19mb3JuZWNlZG9yIjoiSiIsInBvcnRlX2Zvcm5lY2Vkb3IiOiIwIiwiaWF0IjoxNzc3NDk5NzMxLCJleHAiOjE3Nzc1MDAzMzF9.fCyrSxRbzLU5TikFJY0JK35O7N3HmiALHPlDzsAJE9tIyxv1Rn7zLbHQ71JkhpvfIr137eOv7jm6fa2rHYt57whGwcyFt2t3L_M_zTTg9sUSy-NW3Hi2gTN00sJmcbSxP4erCiaDl4b0DsX55wyFC0u21Gic4BnYZoNolCoq3P3C8vSirYSCcltv8AJEq7_Yxf0ZlcXXZOsRFnCD1DIbmrJp8j8QZTG8Q_bM5zU5Hc1zlRAOdRB2DiAM9n9cdnheB_DMhZ6D4_tbJ8ZvQVm9VggyMAGud21Vx9Atmz_h0EooMO8pNLe0WMubFwWJfvAfgbxokwfpzmj_BoUmnMHBHlprk3c3rUy4SPzzXemoAaSWYktbaco_5KsrCf3Fe1a_RvhERCo4ygBpkSGd8oKeljamylFf--4UH2Jv5qDWKr23lLuHHHL7vwcNogllTHqi2_R3p40PZ6nXQBh0tsxExIm4yGbuk9jmCJjfW8q3ndFjlcRqgrUH7FRI4w00w4yLKXzkEDSn-XSlMRx6c-B_dwDoJeK1zIK4iX7avo3UMdBY79hNgJBnMn2M3q20ds43gWUbcKOjxJa0-SlhODyrou5vGFun-UtBuJA83K1KyqfdUaG4xeT7ZBVoUqgBez4yMHryxj5iPs9vRzHUUOAH95rl1Tgxa3fW2v4RJ1Tnb5I")

                // 🔥 HEADERS IMPORTANTES
                .addHeader("Accept", "application/json, text/plain, */*")
                .addHeader("x-device-platform", "web")
                .addHeader("x-version-number", "6.0.1")
                .addHeader("Referer", "https://cnetmobile.estaleiro.serpro.gov.br/comprasnet-web/")
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 6.0; Nexus 5) AppleWebKit/537.36 Chrome/138.0.0.0 Mobile Safari/537.36")

                .build();

        Response response = client.newCall(request).execute();

        System.out.println("Status: " + response.code());

        if (response.body() != null) {
            System.out.println(response.body().string());
        }
    }
}
