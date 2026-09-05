package com.acme.trading.order.client;

import com.acme.trading.pricing.PriceQuote;
import com.acme.trading.pricing.PricingEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PricingClientTest {

    private final PricingClient client = new PricingClient(PricingEngine.defaultEngine());

    @Test
    void aKnownSymbolIsPricedFromItsSampleQuote() {
        // AAPL sample quote has last = 189.52, so mid = 189.52 and
        // notional = 189.52 * 100 = 18952.00.
        PriceQuote quote = client.priceOrder("AAPL", new BigDecimal("100"), "RETAIL");

        assertEquals("AAPL", quote.symbol());
        assertEquals(new BigDecimal("189.52"), quote.midPrice());
        // Retail on 18952.00: 10000*0.0010 + 8952*0.0005 = 10 + 4.476 = 14.476 -> 14.48
        assertEquals(new BigDecimal("14.48"), quote.estimatedFee());
    }

    @ParameterizedTest(name = "symbol [{0}] resolves to the AAPL sample quote")
    @ValueSource(strings = {"AAPL", "aapl", "AaPl"})
    void symbolLookupIsCaseInsensitive(String symbol) {
        assertEquals(new BigDecimal("189.52"), client.priceOrder(symbol, new BigDecimal("1"), "RETAIL").midPrice());
    }

    @Test
    void anUnknownSymbolFallsBackToASyntheticHundredDollarQuote() {
        // Documents current behaviour: an unrecognised symbol is priced rather than
        // rejected, using a fabricated 100.00/100.10 quote with last = 100.05.
        PriceQuote quote = client.priceOrder("NOSUCH", new BigDecimal("10"), "RETAIL");

        assertEquals("NOSUCH", quote.symbol());
        assertEquals(new BigDecimal("100.05"), quote.midPrice());
        // notional = 100.05 * 10 = 1000.50, retail = 1000.50 * 0.0010 = 1.0005 -> 1.00
        assertEquals(new BigDecimal("1.00"), quote.estimatedFee());
    }

    @Test
    void theUnknownSymbolFallbackPreservesTheRequestedCase() {
        assertEquals("nosuch", client.priceOrder("nosuch", new BigDecimal("1"), "RETAIL").symbol());
    }

    @Test
    void everythingIsPricedInUsd() {
        assertEquals("USD", client.priceOrder("MSFT", new BigDecimal("1"), "RETAIL").currency());
    }

    @Test
    void accountTierChangesTheFee() {
        BigDecimal retail = client.priceOrder("AAPL", new BigDecimal("100"), "RETAIL").estimatedFee();
        BigDecimal institutional = client.priceOrder("AAPL", new BigDecimal("100"), "INSTITUTIONAL").estimatedFee();

        assertEquals(new BigDecimal("14.48"), retail);
        // 18952.00 * 0.0001 = 1.8952 -> 1.90, then halved by the institutional discount -> 0.95
        assertEquals(new BigDecimal("0.95"), institutional);
    }

    @Test
    void estimateFeeDelegatesToTheEngine() {
        assertEquals(PricingEngine.defaultEngine().estimateFee(new BigDecimal("20000"), "PRO"),
                client.estimateFee(new BigDecimal("20000"), "PRO"));
    }
}
