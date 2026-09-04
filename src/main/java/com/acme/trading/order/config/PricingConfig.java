package com.acme.trading.order.config;

import com.acme.trading.pricing.PricingEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PricingConfig {

    @Bean
    public PricingEngine pricingEngine() {
        return PricingEngine.defaultEngine();
    }
}
