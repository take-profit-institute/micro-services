package org.profit.candle.market;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MarketServiceApplication {
    public static void main(String[] args) {

        System.out.println(System.getenv("KIWOOM_APP_KEY"));

        SpringApplication.run(MarketServiceApplication.class, args);
    }


}
