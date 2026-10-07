package br.gov.compras.rpa;

import br.gov.compras.rpa.service.ChatMonitoraService;


public class Main {

    public static void main(String[] args) throws Exception {

        ChatMonitoraService service = new ChatMonitoraService();

        service.login("06303344127", "Rfmh05046@");

        service.monitorar();
    }
}

