package br.gov.compras.rpa.model;

import java.time.LocalDate;

public class Oportunidade {

    private final Long id;
    private final Long clienteId;
    private final String cnpj;
    private final String razaoSocial;
    private final String fantasia;
    private final Long modalidadeId;
    private final String numeroEdital;
    private final String uasgOc;
    private final LocalDate dataCertame;
    private final String status;

    private final String cliente;
    private final String uasg;
    private final String numeroPregao;

    public Oportunidade(
            Long id,
            Long clienteId,
            String cnpj,
            String razaoSocial,
            String fantasia,
            Long modalidadeId,
            String numeroEdital,
            String uasgOc,
            LocalDate dataCertame,
            String status,
            String cliente,
            String uasg,
            String numeroPregao
    ) {
        this.id = id;
        this.clienteId = clienteId;
        this.cnpj = cnpj;
        this.razaoSocial = razaoSocial;
        this.fantasia = fantasia;
        this.modalidadeId = modalidadeId;
        this.numeroEdital = numeroEdital;
        this.uasgOc = uasgOc;
        this.dataCertame = dataCertame;
        this.status = status;
        this.cliente = cliente;
        this.uasg = uasg;
        this.numeroPregao = numeroPregao;
    }

    public Long getId() {
        return id;
    }

    public Long getClienteId() {
        return clienteId;
    }

    public String getCnpj() {
        return cnpj;
    }

    public String getRazaoSocial() {
        return razaoSocial;
    }

    public String getFantasia() {
        return fantasia;
    }

    public Long getModalidadeId() {
        return modalidadeId;
    }

    public String getNumeroEdital() {
        return numeroEdital;
    }

    public String getUasgOc() {
        return uasgOc;
    }

    public LocalDate getDataCertame() {
        return dataCertame;
    }

    public String getStatus() {
        return status;
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