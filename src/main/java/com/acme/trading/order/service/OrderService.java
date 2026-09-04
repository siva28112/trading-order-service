package com.acme.trading.order.service;

import com.acme.trading.domain.OrderStatus;
import com.acme.trading.dto.OrderRequest;
import com.acme.trading.dto.OrderResponse;
import com.acme.trading.event.OrderCancelledEvent;
import com.acme.trading.event.OrderPlacedEvent;
import com.acme.trading.exception.TradingException;
import com.acme.trading.exception.ValidationException;
import com.acme.trading.order.client.PricingClient;
import com.acme.trading.order.repository.OrderRepository;
import com.acme.trading.pricing.PriceQuote;
import com.acme.trading.validation.OrderValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final PricingClient pricingClient;
    private final String defaultAccountTier;

    public OrderService(OrderRepository orderRepository,
                        PricingClient pricingClient,
                        @Value("${acme.trading.default-account-tier:RETAIL}") String defaultAccountTier) {
        this.orderRepository = orderRepository;
        this.pricingClient = pricingClient;
        this.defaultAccountTier = defaultAccountTier;
    }

    public OrderResponse placeOrder(OrderRequest request) {
        OrderValidator.validate(request);

        PriceQuote priceQuote = pricingClient.priceOrder(
                request.symbol(), request.quantity(), defaultAccountTier);

        Instant now = Instant.now();
        OrderResponse order = new OrderResponse(
                UUID.randomUUID().toString(),
                request.accountId(),
                request.symbol().toUpperCase(),
                request.side(),
                request.orderType(),
                OrderStatus.OPEN,
                request.quantity(),
                BigDecimal.ZERO,
                request.limitPrice(),
                null,
                priceQuote.estimatedFee(),
                now,
                now
        );

        orderRepository.save(order);
        publishPlacedEvent(order);
        return order;
    }

    public OrderResponse getOrder(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new TradingException("Order not found: " + orderId));
    }

    public List<OrderResponse> listOrders(String accountId) {
        if (accountId != null && !accountId.isBlank()) {
            return orderRepository.findByAccountId(accountId);
        }
        return orderRepository.findAll();
    }

    public OrderResponse cancelOrder(String orderId) {
        OrderResponse existing = getOrder(orderId);
        if (existing.status() == OrderStatus.CANCELLED || existing.status() == OrderStatus.FILLED) {
            throw new ValidationException("Order cannot be cancelled in status: " + existing.status());
        }

        OrderResponse cancelled = new OrderResponse(
                existing.orderId(),
                existing.accountId(),
                existing.symbol(),
                existing.side(),
                existing.orderType(),
                OrderStatus.CANCELLED,
                existing.quantity(),
                existing.filledQuantity(),
                existing.limitPrice(),
                existing.averageFillPrice(),
                existing.estimatedFee(),
                existing.createdAt(),
                Instant.now()
        );

        orderRepository.save(cancelled);
        publishCancelledEvent(cancelled, "Client requested cancellation");
        return cancelled;
    }

    public OrderResponse amendOrder(String orderId, OrderRequest amendRequest) {
        OrderValidator.validate(amendRequest);
        OrderResponse existing = getOrder(orderId);

        if (existing.status() != OrderStatus.OPEN && existing.status() != OrderStatus.PENDING) {
            throw new ValidationException("Only open orders can be amended");
        }

        PriceQuote priceQuote = pricingClient.priceOrder(
                amendRequest.symbol(), amendRequest.quantity(), defaultAccountTier);

        OrderResponse amended = new OrderResponse(
                existing.orderId(),
                existing.accountId(),
                amendRequest.symbol().toUpperCase(),
                amendRequest.side(),
                amendRequest.orderType(),
                OrderStatus.OPEN,
                amendRequest.quantity(),
                existing.filledQuantity(),
                amendRequest.limitPrice(),
                existing.averageFillPrice(),
                priceQuote.estimatedFee(),
                existing.createdAt(),
                Instant.now()
        );

        orderRepository.save(amended);
        return amended;
    }

    private void publishPlacedEvent(OrderResponse order) {
        new OrderPlacedEvent(
                order.orderId(),
                order.accountId(),
                order.symbol(),
                order.side(),
                order.orderType(),
                order.quantity()
        );
    }

    private void publishCancelledEvent(OrderResponse order, String reason) {
        new OrderCancelledEvent(order.orderId(), order.accountId(), reason);
    }
}
