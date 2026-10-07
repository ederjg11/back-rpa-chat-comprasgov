package br.gov.compras.rpa.model;

import java.time.LocalDate;

public class Oportunidades {


    private final String cliente;
    private final String uasg;
    private final String numeroPregao;

    public Oportunidades(

            String cliente,
            String uasg,
            String numeroPregao
    ) {

        this.cliente = cliente;
        this.uasg = uasg;
        this.numeroPregao = numeroPregao;
    }

    public String getCliente() {
        return cliente;
    }

    public String getUasg() {
        return uasg;
    }

    public String getNumeroPregao() {
        return numeroPregao;
    }
}
