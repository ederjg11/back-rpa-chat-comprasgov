package br.gov.compras.rpa.repository;

import br.gov.compras.rpa.model.MonitoredClient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MonitoredClientRepository extends JpaRepository<MonitoredClient, Long> {

    List<MonitoredClient> findByActiveTrue();
}
