package org.profit.candle.wishlist.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WishlistConfiguration {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
