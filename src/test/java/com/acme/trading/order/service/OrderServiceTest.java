package com.acme.trading.order.service;

import com.acme.trading.domain.OrderSide;
import com.acme.trading.domain.OrderStatus;
import com.acme.trading.domain.OrderType;
import com.acme.trading.dto.OrderRequest;
import com.acme.trading.dto.OrderResponse;
import com.acme.trading.exception.ValidationException;
import com.acme.trading.order.client.PricingClient;
import com.acme.trading.order.repository.InMemoryOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderServiceTest {

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                new InMemoryOrderRepository(),
                new PricingClient(com.acme.trading.pricing.PricingEngine.defaultEngine()),
                "RETAIL"
        );
    }

    @Test
    void placeOrder_createsOpenOrderWithFee() {
        OrderRequest request = new OrderRequest(
                "ACC-001",
                "AAPL",
                OrderSide.BUY,
                OrderType.MARKET,
                new BigDecimal("100"),
                null,
                "client-1"
        );

        OrderResponse response = orderService.placeOrder(request);

        assertNotNull(response.orderId());
        assertEquals(OrderStatus.OPEN, response.status());
        assertEquals("AAPL", response.symbol());
        assertNotNull(response.estimatedFee());
    }

    @Test
    void cancelOrder_transitionsToCancelled() {
        OrderRequest request = new OrderRequest(
                "ACC-001", "MSFT", OrderSide.SELL, OrderType.LIMIT,
                new BigDecimal("50"), new BigDecimal("420.00"), "client-2"
        );
        OrderResponse placed = orderService.placeOrder(request);

        OrderResponse cancelled = orderService.cancelOrder(placed.orderId());

        assertEquals(OrderStatus.CANCELLED, cancelled.status());
    }

    @Test
    void placeOrder_rejectsInvalidQuantity() {
        OrderRequest request = new OrderRequest(
                "ACC-001", "GOOG", OrderSide.BUY, OrderType.MARKET,
                BigDecimal.ZERO, null, "client-3"
        );

        assertThrows(ValidationException.class, () -> orderService.placeOrder(request));
    }
}
