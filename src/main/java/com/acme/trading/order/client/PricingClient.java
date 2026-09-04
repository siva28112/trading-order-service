package com.acme.trading.order.client;

import com.acme.trading.dto.Quote;
import com.acme.trading.pricing.PriceQuote;
import com.acme.trading.pricing.PricingEngine;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Wrapper around {@link PricingEngine} with sample market quotes for local development.
 */
@Component
public class PricingClient {

    private static final Map<String, Quote> SAMPLE_QUOTES = Map.of(
            "AAPL", new Quote("AAPL", bd("189.50"), bd("189.55"), bd("189.52"), bd("45000000"), Instant.now()),
            "MSFT", new Quote("MSFT", bd("415.20"), bd("415.30"), bd("415.25"), bd("22000000"), Instant.now()),
            "GOOG", new Quote("GOOG", bd("175.80"), bd("175.90"), bd("175.85"), bd("18000000"), Instant.now())
    );

    private final PricingEngine pricingEngine;

    public PricingClient(PricingEngine pricingEngine) {
        this.pricingEngine = pricingEngine;
    }

    public PriceQuote priceOrder(String symbol, BigDecimal quantity, String accountTier) {
        Quote quote = SAMPLE_QUOTES.getOrDefault(symbol.toUpperCase(),
                new Quote(symbol, bd("100.00"), bd("100.10"), bd("100.05"), BigDecimal.ZERO, Instant.now()));
        return pricingEngine.priceOrder(quote, quantity, accountTier, "USD");
    }

    public BigDecimal estimateFee(BigDecimal notional, String accountTier) {
        return pricingEngine.estimateFee(notional, accountTier);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
