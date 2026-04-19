package br.gov.compras.rpa.service;

import br.gov.compras.rpa.model.MonitoredClient;
import br.gov.compras.rpa.repository.MonitoredClientRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ClientMatcherService {

    private final MonitoredClientRepository monitoredClientRepository;

    public ClientMatcherService(MonitoredClientRepository monitoredClientRepository) {
        this.monitoredClientRepository = monitoredClientRepository;
    }

    public Optional<MonitoredClient> match(String message) {
        String normalizedMessage = normalize(message);

        return monitoredClientRepository.findByActiveTrue().stream()
                .filter(client -> normalizedMessage.contains(normalize(client.getCnpj()))
                        || message.toLowerCase().contains(client.getName().toLowerCase()))
                .findFirst();
    }

    private String normalize(String input) {
        return input == null ? "" : input.replaceAll("\\D", "");
    }
}
