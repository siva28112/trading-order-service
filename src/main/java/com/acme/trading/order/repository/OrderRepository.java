package com.acme.trading.order.repository;

import com.acme.trading.dto.OrderResponse;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    OrderResponse save(OrderResponse order);

    Optional<OrderResponse> findById(String orderId);

    List<OrderResponse> findByAccountId(String accountId);

    List<OrderResponse> findAll();
}
