# Acme Trading Order Service

REST API for order placement, cancellation, amendment, and listing.

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/orders` | Place a new order |
| GET | `/api/v1/orders/{id}` | Get order by ID |
| DELETE | `/api/v1/orders/{id}` | Cancel an open order |
| GET | `/api/v1/orders` | List orders (optional `accountId` filter) |
| POST | `/api/v1/orders/{id}/amend` | Amend an open order |

## Run

```bash
mvn spring-boot:run
```

Service listens on port **8081**.

## Dependencies

- `trading-common-lib` — domain models and validation
- `trading-pricing-lib` — fee estimation via `PricingEngine`
