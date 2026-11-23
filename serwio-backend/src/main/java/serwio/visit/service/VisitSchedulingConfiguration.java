package serwio.visit.service;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class VisitSchedulingConfiguration {
    @Bean
    Clock visitClock() {
        return Clock.systemUTC();
    }
}
