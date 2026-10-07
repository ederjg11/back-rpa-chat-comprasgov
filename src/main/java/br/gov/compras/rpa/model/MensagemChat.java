package br.gov.compras.rpa.model;

import lombok.Data;

@Data
public class MensagemChat {

    public String id;
    public String texto;
    public String remetente;
    public String data;
    public Integer itemLote;

}