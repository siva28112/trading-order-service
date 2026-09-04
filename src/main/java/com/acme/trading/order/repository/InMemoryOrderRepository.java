package com.acme.trading.order.repository;

import com.acme.trading.dto.OrderResponse;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class InMemoryOrderRepository implements OrderRepository {

    private final Map<String, OrderResponse> orders = new ConcurrentHashMap<>();

    @Override
    public OrderResponse save(OrderResponse order) {
        orders.put(order.orderId(), order);
        return order;
    }

    @Override
    public Optional<OrderResponse> findById(String orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    @Override
    public List<OrderResponse> findByAccountId(String accountId) {
        return orders.values().stream()
                .filter(o -> o.accountId().equals(accountId))
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderResponse> findAll() {
        return List.copyOf(orders.values());
    }
}
