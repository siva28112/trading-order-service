# Postman collections

`trading-order-service.postman_collection.json` covers all five endpoints plus the
error contract.

## Run it

Start the service first — the collection expects it on `http://localhost:8081`:

```bash
mvn -q package -DskipTests
java -jar target/trading-order-service-1.0.0.jar
```

Then either import the collection into Postman, or run it headless:

```bash
npx newman run postman/trading-order-service.postman_collection.json
```

Requests are order-dependent: **Place order** stores `{{orderId}}` as a collection
variable and everything after it uses that. Run the folder top to bottom.

Override `baseUrl` and `accountId` with `--env-var` if you are pointing at another
instance:

```bash
npx newman run postman/trading-order-service.postman_collection.json \
  --env-var baseUrl=http://localhost:9090 --env-var accountId=ACC-42
```

## The "Error contract" folder

Those four requests assert what the service returns **today**, which is not what it
should return. `GlobalExceptionHandler` in `OrderController.java` is annotated
`@RestController` rather than `@ControllerAdvice`, so its `@ExceptionHandler` methods
are scoped to a class that declares no request mappings and never fire. An unknown
order and a validation failure both escape to Boot's default handler and come back as
500 instead of 404 and 400.

The assertions are written so that fixing the annotation makes them **fail loudly**
rather than drift silently. When that happens, update the expected statuses to 404 and
400 and drop the "INTENDED behaviour" guards.

## Last verified

```
requests    10
assertions  27
failed       0
```
