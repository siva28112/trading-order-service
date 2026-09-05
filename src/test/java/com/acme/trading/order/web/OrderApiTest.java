package com.acme.trading.order.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the HTTP contract against a running server, so the statuses asserted here
 * are the statuses a client actually receives -- MockMvc rethrows handler exceptions and
 * would hide what the container does with them.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderApiTest {

    @Autowired
    private TestRestTemplate rest;

    private static HttpEntity<String> json(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private static String orderBody(String accountId, String symbol, String side,
                                    String orderType, String quantity, String limitPrice) {
        return """
                {"accountId":"%s","symbol":"%s","side":"%s","orderType":"%s",
                 "quantity":%s%s,"clientOrderId":"it-client"}
                """.formatted(accountId, symbol, side, orderType, quantity,
                limitPrice == null ? "" : ",\"limitPrice\":" + limitPrice);
    }

    private JsonNode placeOrder(String accountId, String symbol) {
        ResponseEntity<JsonNode> response = rest.exchange("/api/v1/orders", HttpMethod.POST,
                json(orderBody(accountId, symbol, "BUY", "MARKET", "100", null)), JsonNode.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        return response.getBody();
    }

    @Test
    @DisplayName("POST /api/v1/orders returns 201 with a fully populated order")
    void placeOrderReturnsCreated() {
        JsonNode order = placeOrder("ACC-IT-1", "aapl");

        assertNotNull(order);
        assertFalse(order.get("orderId").asText().isBlank());
        assertEquals("ACC-IT-1", order.get("accountId").asText());
        assertEquals("AAPL", order.get("symbol").asText(), "the API upper-cases the symbol");
        assertEquals("OPEN", order.get("status").asText());
        assertEquals(100, order.get("quantity").asInt());
        assertEquals(0, order.get("filledQuantity").asInt());
        assertTrue(order.get("limitPrice").isNull());
        assertTrue(order.get("estimatedFee").decimalValue().signum() > 0);
        assertEquals(order.get("createdAt").asText(), order.get("updatedAt").asText());
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id} returns the order that was placed")
    void getOrderReturnsThePlacedOrder() {
        String orderId = placeOrder("ACC-IT-2", "MSFT").get("orderId").asText();

        ResponseEntity<JsonNode> response =
                rest.getForEntity("/api/v1/orders/" + orderId, JsonNode.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(orderId, response.getBody().get("orderId").asText());
        assertEquals("MSFT", response.getBody().get("symbol").asText());
    }

    @Test
    @DisplayName("DELETE /api/v1/orders/{id} cancels and returns the cancelled order")
    void cancelOrderReturnsCancelled() {
        String orderId = placeOrder("ACC-IT-3", "GOOG").get("orderId").asText();

        ResponseEntity<JsonNode> response = rest.exchange(
                "/api/v1/orders/" + orderId, HttpMethod.DELETE, null, JsonNode.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("CANCELLED", response.getBody().get("status").asText());
        assertEquals(orderId, response.getBody().get("orderId").asText());
    }

    @Test
    @DisplayName("POST /api/v1/orders/{id}/amend replaces the order in place")
    void amendOrderReplacesInPlace() {
        String orderId = placeOrder("ACC-IT-4", "AAPL").get("orderId").asText();

        ResponseEntity<JsonNode> response = rest.exchange(
                "/api/v1/orders/" + orderId + "/amend", HttpMethod.POST,
                json(orderBody("ACC-IT-4", "MSFT", "SELL", "LIMIT", "25", "400.00")), JsonNode.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(orderId, response.getBody().get("orderId").asText());
        assertEquals("MSFT", response.getBody().get("symbol").asText());
        assertEquals(25, response.getBody().get("quantity").asInt());
        assertEquals("OPEN", response.getBody().get("status").asText());
    }

    @Test
    @DisplayName("GET /api/v1/orders filters by accountId when the parameter is present")
    void listOrdersFiltersByAccount() {
        placeOrder("ACC-IT-FILTER", "AAPL");
        placeOrder("ACC-IT-FILTER", "MSFT");
        placeOrder("ACC-IT-OTHER", "GOOG");

        ResponseEntity<JsonNode> filtered =
                rest.getForEntity("/api/v1/orders?accountId=ACC-IT-FILTER", JsonNode.class);
        ResponseEntity<JsonNode> all = rest.getForEntity("/api/v1/orders", JsonNode.class);

        assertEquals(HttpStatus.OK, filtered.getStatusCode());
        assertEquals(2, filtered.getBody().size());
        assertTrue(all.getBody().size() >= 3, "the unfiltered list must include the other account");
    }

    // -------------------------------------------------------------------------------------
    // Error contract. These record what the service does TODAY, which is not what it
    // should do -- see the class comment on the assertions below.
    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("an unknown order id returns 500, not 404 (GlobalExceptionHandler never fires)")
    void unknownOrderReturnsServerErrorNotNotFound() {
        ResponseEntity<JsonNode> response =
                rest.getForEntity("/api/v1/orders/does-not-exist", JsonNode.class);

        // GlobalExceptionHandler in OrderController.java is annotated @RestController
        // rather than @ControllerAdvice, so its @ExceptionHandler methods are scoped to
        // that class -- which declares no request mappings. TradingException therefore
        // escapes to the container and Boot's default handler returns 500.
        // The intended contract is 404 with {"error": "Order not found: ..."}.
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(500, response.getBody().get("status").asInt());
    }

    @Test
    @DisplayName("a validation failure returns 500, not 400 (same root cause)")
    void invalidOrderReturnsServerErrorNotBadRequest() {
        ResponseEntity<JsonNode> response = rest.exchange("/api/v1/orders", HttpMethod.POST,
                json(orderBody("ACC-IT-5", "AAPL", "BUY", "MARKET", "0", null)), JsonNode.class);

        // Intended contract is 400 with {"error": "Quantity must be positive"}.
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    @DisplayName("a missing required field is rejected at deserialisation with 400")
    void missingRequiredFieldIsRejectedWithBadRequest() {
        // This one does return 400, but by a different route: OrderRequest's compact
        // constructor throws NullPointerException while Jackson is building the record,
        // which Spring maps to HttpMessageNotReadableException -> 400. It is not the
        // exception handler working.
        ResponseEntity<JsonNode> response = rest.exchange("/api/v1/orders", HttpMethod.POST,
                json("""
                        {"symbol":"AAPL","side":"BUY","orderType":"MARKET","quantity":10}
                        """), JsonNode.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("cancelling twice returns 500, not 400")
    void cancellingTwiceReturnsServerError() {
        String orderId = placeOrder("ACC-IT-6", "AAPL").get("orderId").asText();
        rest.exchange("/api/v1/orders/" + orderId, HttpMethod.DELETE, null, JsonNode.class);

        ResponseEntity<JsonNode> second = rest.exchange(
                "/api/v1/orders/" + orderId, HttpMethod.DELETE, null, JsonNode.class);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, second.getStatusCode());
    }
}
