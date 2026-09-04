package com.acme.trading.order.web;

import com.acme.trading.dto.OrderRequest;
import com.acme.trading.dto.OrderResponse;
import com.acme.trading.exception.TradingException;
import com.acme.trading.exception.ValidationException;
import com.acme.trading.order.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@RequestBody OrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.placeOrder(request));
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable("id") String orderId) {
        return orderService.getOrder(orderId);
    }

    @DeleteMapping("/{id}")
    public OrderResponse cancelOrder(@PathVariable("id") String orderId) {
        return orderService.cancelOrder(orderId);
    }

    @GetMapping
    public List<OrderResponse> listOrders(@RequestParam(required = false) String accountId) {
        return orderService.listOrders(accountId);
    }

    @PostMapping("/{id}/amend")
    public OrderResponse amendOrder(@PathVariable("id") String orderId,
                                    @RequestBody OrderRequest amendRequest) {
        return orderService.amendOrder(orderId, amendRequest);
    }
}

@RestController
@RequestMapping("/api/v1")
class GlobalExceptionHandler {

    @org.springframework.web.bind.annotation.ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, String>> handleValidation(ValidationException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(TradingException.class)
    public ResponseEntity<Map<String, String>> handleTrading(TradingException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }
}
