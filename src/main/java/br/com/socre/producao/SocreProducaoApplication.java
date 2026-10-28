package br.com.socre.producao;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SocreProducaoApplication {
    public static void main(String[] args) { SpringApplication.run(SocreProducaoApplication.class, args); }
    @Bean Clock clock() { return Clock.systemUTC(); }
}
