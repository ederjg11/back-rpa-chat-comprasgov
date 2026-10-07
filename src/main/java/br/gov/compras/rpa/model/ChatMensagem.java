package br.gov.compras.rpa.model;

import lombok.Data;
@Data
public class ChatMensagem {
    private String texto;
    private String dataHora;
    private String identificadorRemetente;
    private String tipoRemetente;
}
