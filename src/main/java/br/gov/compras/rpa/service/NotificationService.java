package br.gov.compras.rpa.service;

import br.gov.compras.rpa.model.MonitoredClient;

public interface NotificationService {

    void notifyClientMention(MonitoredClient client, String message);
}
