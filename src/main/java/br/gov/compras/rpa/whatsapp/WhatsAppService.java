package br.gov.compras.rpa.whatsapp;

import java.util.Map;

public interface WhatsAppService {

    void enviarMensagem(String telefone, Map<String, Object> dados);

}