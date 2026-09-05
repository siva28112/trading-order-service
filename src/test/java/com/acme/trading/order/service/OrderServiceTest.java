package com.acme.trading.order.service;

import com.acme.trading.domain.OrderSide;
import com.acme.trading.domain.OrderStatus;
import com.acme.trading.domain.OrderType;
import com.acme.trading.domain.OrderType;
import com.acme.trading.dto.OrderRequest;
import com.acme.trading.dto.OrderResponse;
import com.acme.trading.exception.TradingException;
import com.acme.trading.exception.ValidationException;
import com.acme.trading.order.client.PricingClient;
import com.acme.trading.order.repository.InMemoryOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    // ---------------------------------------------------------------------------------
    // Added coverage. The three tests above are unchanged.
    // ---------------------------------------------------------------------------------

    private static OrderRequest request(String symbol, OrderType type, String quantity, String limitPrice) {
        return new OrderRequest("ACC-001", symbol, OrderSide.BUY, type,
                new BigDecimal(quantity), limitPrice == null ? null : new BigDecimal(limitPrice), "client-x");
    }

    @Test
    void placeOrder_upperCasesTheSymbol() {
        OrderResponse response = orderService.placeOrder(request("aapl", OrderType.MARKET, "10", null));
        assertEquals("AAPL", response.symbol());
    }

    @Test
    void placeOrder_startsWithNoFillAndMatchingTimestamps() {
        OrderResponse response = orderService.placeOrder(request("AAPL", OrderType.MARKET, "10", null));

        assertEquals(0, BigDecimal.ZERO.compareTo(response.filledQuantity()));
        assertNull(response.averageFillPrice());
        assertEquals(response.createdAt(), response.updatedAt(),
                "a newly placed order has not been updated since creation");
    }

    @Test
    void placeOrder_carriesTheRequestedLimitPriceThrough() {
        OrderResponse response = orderService.placeOrder(request("AAPL", OrderType.LIMIT, "10", "150.00"));
        assertEquals(new BigDecimal("150.00"), response.limitPrice());
    }

    @Test
    void placeOrder_assignsADistinctIdPerOrder() {
        OrderResponse first = orderService.placeOrder(request("AAPL", OrderType.MARKET, "10", null));
        OrderResponse second = orderService.placeOrder(request("AAPL", OrderType.MARKET, "10", null));

        assertNotEquals(first.orderId(), second.orderId());
    }

    @Test
    void getOrder_rejectsAnUnknownId() {
        TradingException thrown =
                assertThrows(TradingException.class, () -> orderService.getOrder("missing"));
        assertEquals("Order not found: missing", thrown.getMessage());
    }

    @Test
    void listOrders_filtersByAccountWhenGiven() {
        orderService.placeOrder(new OrderRequest("ACC-A", "AAPL", OrderSide.BUY, OrderType.MARKET,
                new BigDecimal("10"), null, "c1"));
        orderService.placeOrder(new OrderRequest("ACC-B", "MSFT", OrderSide.BUY, OrderType.MARKET,
                new BigDecimal("10"), null, "c2"));

        assertEquals(1, orderService.listOrders("ACC-A").size());
        assertEquals(2, orderService.listOrders(null).size());
    }

    @Test
    void listOrders_treatsBlankAccountIdAsNoFilter() {
        orderService.placeOrder(request("AAPL", OrderType.MARKET, "10", null));

        assertEquals(1, orderService.listOrders("").size());
        assertEquals(1, orderService.listOrders("   ").size());
    }

    @Test
    void cancelOrder_rejectsAnAlreadyCancelledOrder() {
        OrderResponse placed = orderService.placeOrder(request("AAPL", OrderType.MARKET, "10", null));
        orderService.cancelOrder(placed.orderId());

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> orderService.cancelOrder(placed.orderId()));
        assertEquals("Order cannot be cancelled in status: CANCELLED", thrown.getMessage());
    }

    @Test
    void cancelOrder_rejectsAnUnknownId() {
        assertThrows(TradingException.class, () -> orderService.cancelOrder("missing"));
    }

    @Test
    void cancelOrder_preservesIdentityAndMovesOnlyTheUpdatedTimestamp() {
        OrderResponse placed = orderService.placeOrder(request("AAPL", OrderType.MARKET, "10", null));
        OrderResponse cancelled = orderService.cancelOrder(placed.orderId());

        assertEquals(placed.orderId(), cancelled.orderId());
        assertEquals(placed.accountId(), cancelled.accountId());
        assertEquals(placed.createdAt(), cancelled.createdAt(), "createdAt must not move on cancel");
        assertTrue(!cancelled.updatedAt().isBefore(placed.updatedAt()));
    }

    @Test
    void amendOrder_replacesQuantitySymbolAndFee() {
        OrderResponse placed = orderService.placeOrder(request("AAPL", OrderType.MARKET, "10", null));

        OrderResponse amended = orderService.amendOrder(placed.orderId(),
                new OrderRequest("ACC-001", "msft", OrderSide.SELL, OrderType.LIMIT,
                        new BigDecimal("25"), new BigDecimal("400.00"), "client-amend"));

        assertEquals(placed.orderId(), amended.orderId());
        assertEquals("MSFT", amended.symbol());
        assertEquals(OrderSide.SELL, amended.side());
        assertEquals(new BigDecimal("25"), amended.quantity());
        assertEquals(new BigDecimal("400.00"), amended.limitPrice());
        assertEquals(OrderStatus.OPEN, amended.status());
        assertEquals(placed.createdAt(), amended.createdAt(), "createdAt must not move on amend");
    }

    @Test
    void amendOrder_keepsTheOriginalAccountEvenIfTheAmendNamesAnother() {
        // Documents current behaviour: accountId comes from the stored order, so an amend
        // cannot move an order between accounts.
        OrderResponse placed = orderService.placeOrder(request("AAPL", OrderType.MARKET, "10", null));

        OrderResponse amended = orderService.amendOrder(placed.orderId(),
                new OrderRequest("ACC-OTHER", "AAPL", OrderSide.BUY, OrderType.MARKET,
                        new BigDecimal("11"), null, "c"));

        assertEquals("ACC-001", amended.accountId());
    }

    @Test
    void amendOrder_rejectsACancelledOrder() {
        OrderResponse placed = orderService.placeOrder(request("AAPL", OrderType.MARKET, "10", null));
        orderService.cancelOrder(placed.orderId());

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> orderService.amendOrder(placed.orderId(),
                        request("AAPL", OrderType.MARKET, "11", null)));
        assertEquals("Only open orders can be amended", thrown.getMessage());
    }

    @Test
    void amendOrder_validatesTheAmendBeforeLookingUpTheOrder() {
        // The validator runs first, so an invalid amend against a missing order reports
        // the validation failure rather than "order not found".
        assertThrows(ValidationException.class,
                () -> orderService.amendOrder("missing", request("AAPL", OrderType.MARKET, "0", null)));
    }

    @Test
    void amendOrder_replacesRatherThanAddsAnOrder() {
        OrderResponse placed = orderService.placeOrder(request("AAPL", OrderType.MARKET, "10", null));
        orderService.amendOrder(placed.orderId(), request("AAPL", OrderType.MARKET, "20", null));

        assertEquals(1, orderService.listOrders(null).size());
    }

    @Test
    void placeOrder_feeReflectsTheConfiguredAccountTier() {
        OrderService institutional = new OrderService(
                new InMemoryOrderRepository(),
                new PricingClient(com.acme.trading.pricing.PricingEngine.defaultEngine()),
                "INSTITUTIONAL");

        BigDecimal retailFee = orderService.placeOrder(request("AAPL", OrderType.MARKET, "100", null))
                .estimatedFee();
        BigDecimal institutionalFee = institutional.placeOrder(request("AAPL", OrderType.MARKET, "100", null))
                .estimatedFee();

        assertEquals(new BigDecimal("14.48"), retailFee);
        assertEquals(new BigDecimal("0.95"), institutionalFee);
    }
}
