package com.acme.trading.order.repository;

import com.acme.trading.domain.OrderSide;
import com.acme.trading.domain.OrderStatus;
import com.acme.trading.domain.OrderType;
import com.acme.trading.dto.OrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryOrderRepositoryTest {

    private InMemoryOrderRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryOrderRepository();
    }

    private static OrderResponse order(String orderId, String accountId, OrderStatus status) {
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        return new OrderResponse(orderId, accountId, "AAPL", OrderSide.BUY, OrderType.MARKET,
                status, new BigDecimal("100"), BigDecimal.ZERO, null, null,
                new BigDecimal("1.00"), now, now);
    }

    @Test
    void saveReturnsTheOrderItStored() {
        OrderResponse stored = order("ORD-1", "ACC-1", OrderStatus.OPEN);

        assertSame(stored, repository.save(stored));
        assertEquals(stored, repository.findById("ORD-1").orElseThrow());
    }

    @Test
    void savingTheSameIdReplacesRatherThanDuplicates() {
        repository.save(order("ORD-1", "ACC-1", OrderStatus.OPEN));
        repository.save(order("ORD-1", "ACC-1", OrderStatus.CANCELLED));

        assertEquals(OrderStatus.CANCELLED, repository.findById("ORD-1").orElseThrow().status());
        assertEquals(1, repository.findAll().size(), "an amend must not leave a second copy behind");
    }

    @Test
    void findByIdIsEmptyForAnUnknownOrder() {
        assertTrue(repository.findById("nope").isEmpty());
    }

    @Test
    void findByAccountIdReturnsOnlyThatAccount() {
        repository.save(order("ORD-1", "ACC-1", OrderStatus.OPEN));
        repository.save(order("ORD-2", "ACC-1", OrderStatus.OPEN));
        repository.save(order("ORD-3", "ACC-2", OrderStatus.OPEN));

        assertEquals(2, repository.findByAccountId("ACC-1").size());
        assertEquals(1, repository.findByAccountId("ACC-2").size());
        assertEquals(List.of(), repository.findByAccountId("ACC-3"));
    }

    @Test
    void findByAccountIdMatchesExactlyAndIsCaseSensitive() {
        repository.save(order("ORD-1", "ACC-1", OrderStatus.OPEN));

        assertEquals(List.of(), repository.findByAccountId("acc-1"));
        assertEquals(List.of(), repository.findByAccountId("ACC-1 "));
    }

    @Test
    void findAllReturnsAnImmutableSnapshot() {
        repository.save(order("ORD-1", "ACC-1", OrderStatus.OPEN));
        List<OrderResponse> snapshot = repository.findAll();

        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.add(order("ORD-2", "ACC-1", OrderStatus.OPEN)));

        // The snapshot does not grow when the repository does.
        repository.save(order("ORD-2", "ACC-1", OrderStatus.OPEN));
        assertEquals(1, snapshot.size());
        assertEquals(2, repository.findAll().size());
    }

    @Test
    void findAllIsEmptyOnAFreshRepository() {
        assertEquals(List.of(), repository.findAll());
    }

    @Test
    void concurrentSavesOfDistinctOrdersAllSurvive() throws Exception {
        int threads = 16;
        int perThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);

        for (int t = 0; t < threads; t++) {
            int threadId = t;
            pool.submit(() -> {
                start.await();
                for (int i = 0; i < perThread; i++) {
                    repository.save(order("ORD-" + threadId + "-" + i, "ACC-" + threadId, OrderStatus.OPEN));
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "writers did not finish");

        assertEquals(threads * perThread, repository.findAll().size());
        assertEquals(perThread, repository.findByAccountId("ACC-0").size());
    }
}
