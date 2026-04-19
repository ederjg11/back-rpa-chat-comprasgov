package br.gov.compras.rpa.service;

import br.gov.compras.rpa.model.MonitoredClient;
import br.gov.compras.rpa.repository.MonitoredClientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientMatcherServiceTest {

    @Mock
    private MonitoredClientRepository monitoredClientRepository;

    @InjectMocks
    private ClientMatcherService clientMatcherService;

    @Test
    void shouldMatchByNormalizedCnpj() {
        MonitoredClient client = new MonitoredClient("ACME", "12.345.678/0001-90");
        when(monitoredClientRepository.findByActiveTrue()).thenReturn(List.of(client));

        var matched = clientMatcherService.match("Mensagem para 12345678000190 no chat");

        assertThat(matched).isPresent();
        assertThat(matched.get().getName()).isEqualTo("ACME");
    }

    @Test
    void shouldMatchByClientNameIgnoringCase() {
        MonitoredClient client = new MonitoredClient("Cliente Norte", "98.765.432/0001-10");
        when(monitoredClientRepository.findByActiveTrue()).thenReturn(List.of(client));

        var matched = clientMatcherService.match("mensagem citando cliente norte em edital");

        assertThat(matched).isPresent();
        assertThat(matched.get().getCnpj()).isEqualTo("98.765.432/0001-10");
    }
}
