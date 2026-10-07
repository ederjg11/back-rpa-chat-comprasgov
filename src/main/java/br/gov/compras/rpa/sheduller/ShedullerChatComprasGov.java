package br.gov.compras.rpa.sheduller;

import br.gov.compras.rpa.service.RPAChatComprasNetFullTesteV2;
import br.gov.compras.rpa.service.RPAChatComprasNetFullTesteV3;
import br.gov.compras.rpa.service.RPAChatComprasNetFullTesteV4;
import br.gov.compras.rpa.service.RPAChatComprasNetFullTesteV5;
import jakarta.mail.MessagingException;
import org.hibernate.service.spi.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class ShedullerChatComprasGov {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShedullerChatComprasGov.class);

    @Scheduled(fixedDelay = 300000) // Executa 5 minutos depois que a execução anterior terminar
    public void createOpportunityByProspects() {
        System.out.println("");
        System.out.println("====================================================");
        System.out.println("⏰ INICIANDO EXECUÇÃO AGENDADA: " + java.time.LocalDateTime.now());
        System.out.println("====================================================");

        try {
            RPAChatComprasNetFullTesteV5 comprasGov = new RPAChatComprasNetFullTesteV5();
             comprasGov.run();
        } catch (Exception e) {
            System.out.println("❌ ERRO NA EXECUÇÃO AGENDADA: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("====================================================");
        System.out.println("✅ EXECUÇÃO AGENDADA FINALIZADA: " + java.time.LocalDateTime.now());
        System.out.println("Próxima execução em 5 minutos após este horário.");
        System.out.println("====================================================");
        System.out.println("");
    }
}

