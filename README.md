# Locksmith demo

[Locksmith](https://github.com/riido-git/locksmith) gives a Spring Boot application distributed locks and
semaphores, kept in Redis through Redisson. A lock lets one caller at a time work on a key, across all instances of
the app. A semaphore lets at most N callers in at a time.

This app shows every Locksmith 4.0.0 feature through REST endpoints, each feature in its own package, most with a
controller and a service. Tests check each feature. A stress test and a load test run three instances at once and
check that the guarantees hold.

## What you need

- Java 25.
- Docker, running.

The Maven wrapper (`./mvnw`) downloads the rest. You do not need a Redis of your own: the app, the tests and the
load test each start one in Docker. Port 6379 on your machine is never used.

## Run the app

```
./mvnw spring-boot:run
```

Spring Boot finds [compose.yaml](compose.yaml), starts Redis (`redis:8.10.2`) on a free port of 127.0.0.1, and
points Redisson at it. Then Locksmith logs one line:

```
Locksmith enabled: key-prefix [demo:], semaphore lease-time PT1M, Spring Boot 4.1.1, Redisson 4.8.0
```

Ctrl+C stops the app, and Spring Boot stops the Redis container. `docker compose down` removes it.

The app does not start without a Redis setting. [application.properties](src/main/resources/application.properties)
sets `spring.data.redis.host=redis-not-configured`, so a missing setting fails at startup instead of quietly using
whatever Redis listens on localhost:6379. To use your own Redis:

```
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.docker.compose.enabled=false --spring.data.redis.host=myhost --spring.data.redis.port=6380"
```

## Before the examples

- Every guarded method takes `workMs`: how long it pretends to work.
- Every Redis key Locksmith writes starts with `demo:`, set by `locksmith.key-prefix`.
- A call that does not get its lock answers `409`. A call that gets no permit answers `429`.
- To make two calls meet, start a slow one in the background with `&`, then send the second at once.
- Each guarded method also tells a small tracker, kept in Redis, when it starts and ends. `GET /tracker` shows how
  many callers were inside each method at once. The stress and load tests use it. It is not part of Locksmith.

## Locks: `@DistributedLock`

Code: [lock/LockService.java](src/main/java/in/riido/locksmith/demo/lock/LockService.java),
[lock/LockController.java](src/main/java/in/riido/locksmith/demo/lock/LockController.java),
[lock/BusyOrderHandler.java](src/main/java/in/riido/locksmith/demo/lock/BusyOrderHandler.java).

| Endpoint | Annotation | What it shows |
|---|---|---|
| `POST /locks/orders/{orderId}` | `key = "order:#{#orderId}"` | the defaults: a `REENTRANT` lock, try once, `THROW` |
| `POST /locks/orders/{orderId}/wait` | `waitTime = "5s"` | waits up to 5 s for a busy order |
| `POST /locks/orders/{orderId}/skip` | `onFailure = SKIP` | a busy order skips the method; it returns `false` |
| `POST /locks/orders/{orderId}/handler` | `onFailure = HANDLER` | a busy order returns what `BusyOrderHandler` returns |
| `GET /locks/documents/{id}` | `type = READ, waitTime = "5s"` | many readers at once |
| `PUT /locks/documents/{id}` | `type = WRITE, waitTime = "5s"` | one writer, and no readers meanwhile |
| `POST /locks/lease/fixed` | `leaseTime = "1s"` | Redis drops the lock after 1 s, while the method still works for 3 s |
| `POST /locks/lease/renewed` | no `leaseTime` | Redisson renews the lock for as long as the method runs |

A free order runs:

```
$ curl -s -X POST 'localhost:8080/locks/orders/42?workMs=100'
{"orderId":"42","status":"done"}
```

While one call holds order 42 for 3 s, the other variants answer like this:

```
$ curl -s -X POST 'localhost:8080/locks/orders/42?workMs=3000' > /dev/null & sleep 0.5
$ curl -s -X POST 'localhost:8080/locks/orders/42'
{"detail":"Lock [demo:lock:order:42] not acquired within PT0S","instance":"/locks/orders/42","status":409,"title":"Lock not acquired","key":"demo:lock:order:42","waitTime":"PT0S"}
$ curl -s -X POST 'localhost:8080/locks/orders/42/skip'
{"ran":false}
$ curl -s -X POST 'localhost:8080/locks/orders/42/handler'
{"orderId":"42","status":"busy"}
$ time curl -s -X POST 'localhost:8080/locks/orders/42/wait?workMs=10'
{"orderId":"42","status":"done"}
real	0m2.455s
```

The last call waited until the first one released the order.

A reader waits while a writer works:

```
$ curl -s -X PUT 'localhost:8080/locks/documents/d1?workMs=3000' > /dev/null & sleep 0.5
$ time curl -s 'localhost:8080/locks/documents/d1?workMs=10'
{"id":"d1","access":"read"}
real	0m2.564s
```

A fixed lease runs out; a lock without `leaseTime` does not. The method checks at its end whether its lock is still
held. Redisson's watchdog timeout is lowered to 5 s here (`demo.lock-watchdog-timeout`, set through
[WatchdogConfig.java](src/main/java/in/riido/locksmith/demo/lock/WatchdogConfig.java)), and the renewed method works 7 s:

```
$ curl -s -X POST 'localhost:8080/locks/lease/fixed'
{"lease":"1s","workMs":3000,"stillLockedAtEnd":false}
$ curl -s -X POST 'localhost:8080/locks/lease/renewed'
{"lease":"renewed","workMs":7000,"stillLockedAtEnd":true}
```

The fixed lease also logs Locksmith's WARN at release:
`Lock [demo:lock:lease-demo:fixed] was no longer held at release after 3014ms (fixed lease 1000ms); another instance may have run concurrently ...`

## Key templates

Code: [keys/KeyService.java](src/main/java/in/riido/locksmith/demo/keys/KeyService.java). Each method reports the Redis key
it expects, and checks while it runs that this key is the one locked.

| Endpoint | Template |
|---|---|
| `POST /keys/literal` | `report:daily` (literal text) |
| `POST /keys/users/{userId}` | `user:#{#userId}` (a parameter by name) |
| `POST /keys/orders` with `{"id":..,"customerId":..}` | `customer:#{#order.customerId}` (a property of a parameter) |
| `POST /keys/tenants/{tenant}/regions/{region}` | `tenant:#{#p0}:#{#a1}` (parameters by position) |

```
$ curl -s -X POST 'localhost:8080/keys/users/u7'
{"template":"user:#{#userId}","redisKey":"demo:lock:user:u7","lockedWhileRunning":true}
$ curl -s -X POST -H 'Content-Type: application/json' -d '{"id":"o-1","customerId":"c-7"}' 'localhost:8080/keys/orders'
{"template":"customer:#{#order.customerId}","redisKey":"demo:lock:customer:c-7","lockedWhileRunning":true}
$ curl -s -X POST 'localhost:8080/keys/tenants/t1/regions/eu'
{"template":"tenant:#{#p0}:#{#a1}","redisKey":"demo:lock:tenant:t1:eu","lockedWhileRunning":true}
```

## Semaphores: `@DistributedSemaphore`

Code: [semaphore/SemaphoreService.java](src/main/java/in/riido/locksmith/demo/semaphore/SemaphoreService.java),
[semaphore/BusyExportHandler.java](src/main/java/in/riido/locksmith/demo/semaphore/BusyExportHandler.java).

| Endpoint | Annotation | What it shows |
|---|---|---|
| `POST /semaphores/exports` | `key = "exports", permits = "3"` | at most 3 at once; try once, `THROW` |
| `POST /semaphores/exports/wait` | `waitTime = "10s"` | waits for a free permit |
| `POST /semaphores/exports/skip` | `onFailure = SKIP` | no permit: returns `Optional.empty()` |
| `POST /semaphores/exports/handler` | `onFailure = HANDLER` | no permit: returns what `BusyExportHandler` returns |
| `POST /semaphores/reports` | `permits = "${demo.reports.max-concurrent}"` | the count comes from a property (2) |
| `POST /semaphores/lease` | `permits = "1", leaseTime = "1s"` | the permit expires while the method still works for 2 s |
| `POST /semaphores/imports/{tenant}` | a semaphore of 2, then a lock per tenant | both on one method; the permit is taken first |

While three slow exports hold all 3 permits:

```
$ for i in 1 2 3; do curl -s -X POST 'localhost:8080/semaphores/exports?workMs=3000' > /dev/null & done; sleep 0.5
$ curl -s -X POST 'localhost:8080/semaphores/exports'
{"detail":"Semaphore [demo:semaphore:exports] permit not acquired within PT0S (permits 3)","instance":"/semaphores/exports","status":429,"title":"Permit not acquired","key":"demo:semaphore:exports","permits":3,"waitTime":"PT0S"}
$ curl -s -X POST 'localhost:8080/semaphores/exports/skip'
{"ran":false}
$ curl -s -X POST 'localhost:8080/semaphores/exports/handler'
{"status":"busy"}
$ time curl -s -X POST 'localhost:8080/semaphores/exports/wait?workMs=10'
{"status":"done"}
real	0m2.511s
```

The count from the property, and a permit lease that runs out (the release then logs a WARN):

```
$ curl -s -X POST 'localhost:8080/semaphores/reports?workMs=10'; curl -s 'localhost:8080/programmatic/semaphores?key=reports'
{"status":"done"}{"key":"reports","availablePermits":2}
$ curl -s -X POST 'localhost:8080/semaphores/lease'
{"lease":"1s","workMs":2000,"availablePermitsAtEnd":1}
```

A semaphore and a lock on one method. A second import for the same tenant gets a permit, fails on the tenant's lock,
and gives the permit back. Another tenant gets in:

```
$ curl -s -X POST 'localhost:8080/semaphores/imports/acme?workMs=3000' > /dev/null & sleep 0.5
$ curl -s -X POST 'localhost:8080/semaphores/imports/acme'
{"detail":"Lock [demo:lock:import:acme] not acquired within PT0S","instance":"/semaphores/imports/acme","status":409,"title":"Lock not acquired","key":"demo:lock:import:acme","waitTime":"PT0S"}
$ curl -s -X POST 'localhost:8080/semaphores/imports/globex?workMs=10'
{"tenant":"globex","status":"imported"}
```

## On controller methods

Code: [api/CartController.java](src/main/java/in/riido/locksmith/demo/api/CartController.java),
[api/BusyCartHandler.java](src/main/java/in/riido/locksmith/demo/api/BusyCartHandler.java).

The annotations work on a `@RestController` handler method as on any other bean: Spring MVC calls the handler
through the controller's proxy, so the lock is held while the handler runs. A busy lock answers `409` and a full
semaphore `429` through
[NotAcquiredExceptionHandler](src/main/java/in/riido/locksmith/demo/failure/NotAcquiredExceptionHandler.java), while
`DELETE` shows a `HANDLER` that answers its own response.

| Endpoint | Annotation | What it shows |
|---|---|---|
| `GET /api/carts/{cartId}` | `@DistributedLock(key = "cart:#{#cartId}", type = READ, waitTime = "5s")` | many readers at once, none while a writer holds the cart |
| `PUT /api/carts/{cartId}` | `@DistributedLock(key = "cart:#{#cartId}", type = WRITE, waitTime = "5s")` | a writer waits for its turn |
| `PATCH /api/carts/{cartId}` | `@DistributedLock(key = "cart:#{#cartId}", type = WRITE)` | try once: `409` when busy |
| `DELETE /api/carts/{cartId}` | `@DistributedLock(key = "cart:#{#cartId}", type = WRITE, onFailure = HANDLER, handler = BusyCartHandler.class)` | a busy cart gets the `ResponseEntity` that `BusyCartHandler` returns: `409`, `Retry-After: 1` and a small body |
| `POST /api/carts/checkout` with `{"cartId":..}` | `@DistributedLock(key = "checkout:#{#request.cartId}")` | the key comes from the JSON body; try once, so a double click gets `409` |
| `GET /api/carts/{cartId}/invoice` | `@DistributedSemaphore(key = "invoices", permits = "2")` | at most 2 at once across all instances; `429` when full |

A free cart:

```
$ curl -s 'localhost:8080/api/carts/c1?workMs=100'
{"cartId":"c1","status":"read"}
```

While a writer holds cart c1 for 3 s:

```
$ curl -s -X PUT 'localhost:8080/api/carts/c1?workMs=3000' > /dev/null & sleep 0.5
$ curl -s -X PATCH 'localhost:8080/api/carts/c1'
{"detail":"Lock [demo:rwlock:cart:c1] not acquired within PT0S","instance":"/api/carts/c1","status":409,"title":"Lock not acquired","key":"demo:rwlock:cart:c1","waitTime":"PT0S"}
$ curl -s -i -X DELETE 'localhost:8080/api/carts/c1'
HTTP/1.1 409 
Retry-After: 1
Content-Type: application/json
Transfer-Encoding: chunked
Date: Sun, 11 Oct 2026 17:22:26 GMT

{"cartId":"c1","status":"busy"}
$ time curl -s 'localhost:8080/api/carts/c1?workMs=10'
{"cartId":"c1","status":"read"}
real	0m2.488s
```

The last call waited until the writer released the cart.

Readers share a cart, and a writer waits for them:

```
$ curl -s 'localhost:8080/api/carts/c2?workMs=3000' > /dev/null & sleep 0.5
$ time curl -s 'localhost:8080/api/carts/c2?workMs=10'
{"cartId":"c2","status":"read"}
real	0m0.053s
$ time curl -s -X PUT 'localhost:8080/api/carts/c2?workMs=10'
{"cartId":"c2","status":"replaced"}
real	0m2.477s
```

A double click on checkout. The key comes from the JSON body:

```
$ curl -s -X POST -H 'Content-Type: application/json' -d '{"cartId":"c3"}' 'localhost:8080/api/carts/checkout?workMs=3000' > /dev/null & sleep 0.5
$ curl -s -X POST -H 'Content-Type: application/json' -d '{"cartId":"c3"}' 'localhost:8080/api/carts/checkout'
{"detail":"Lock [demo:lock:checkout:c3] not acquired within PT0S","instance":"/api/carts/checkout","status":409,"title":"Lock not acquired","key":"demo:lock:checkout:c3","waitTime":"PT0S"}
```

Two slow invoices hold both permits:

```
$ for i in 1 2; do curl -s 'localhost:8080/api/carts/c4/invoice?workMs=3000' > /dev/null & done; sleep 0.5
$ curl -s 'localhost:8080/api/carts/c4/invoice'
{"detail":"Semaphore [demo:semaphore:invoices] permit not acquired within PT0S (permits 2)","instance":"/api/carts/c4/invoice","status":429,"title":"Permit not acquired","key":"demo:semaphore:invoices","permits":2,"waitTime":"PT0S"}
```

Do not use `onFailure = SKIP` on a handler method. When the lock is busy, the method returns the default of its return
type (`null` for a `ResponseEntity`), and Spring MVC answers `200` with an empty body (measured on 2026-10-11:
`HTTP/1.1 200`, `Content-Length: 0`). Use `THROW` with an exception handler, as this app does, or a `HANDLER` that
returns the response you want.

## Programmatic API: `LockOperations` and `SemaphoreOperations`

Code: [programmatic/ProgrammaticService.java](src/main/java/in/riido/locksmith/demo/programmatic/ProgrammaticService.java).
The handles are closed by try-with-resources. `acquire()` never throws for "not acquired"; the code checks
`acquired()`.

| Endpoint | What it does |
|---|---|
| `POST /programmatic/reports/{reportId}/rebuild` | a `WRITE` lock, waits up to 5 s |
| `POST /programmatic/exports` | one of 3 permits, waits up to 2 s |
| `GET /programmatic/locks?key=...&type=REENTRANT` | `isLocked` |
| `GET /programmatic/semaphores?key=...` | `availablePermits` |

```
$ curl -s -X POST 'localhost:8080/programmatic/reports/r1/rebuild?workMs=10'
{"acquired":true,"key":"demo:rwlock:rebuild:r1","permitId":null}
$ curl -s -X POST 'localhost:8080/programmatic/exports?workMs=10'
{"acquired":true,"key":"demo:semaphore:api-exports","permitId":"a0b8e41e5c6d58e2c3d4616dfaf83b39"}
$ curl -s -X POST 'localhost:8080/locks/orders/42?workMs=2000' > /dev/null & sleep 0.5
$ curl -s 'localhost:8080/programmatic/locks?key=order:42'
{"locked":true,"key":"order:42","type":"REENTRANT"}
```

## Failure handling

[failure/NotAcquiredExceptionHandler.java](src/main/java/in/riido/locksmith/demo/failure/NotAcquiredExceptionHandler.java)
turns `LockNotAcquiredException` into `409 Conflict` and `SemaphoreNotAcquiredException` into
`429 Too Many Requests`, as RFC 9457 problem details (examples above).

With `SKIP`, the method does not run and returns the default of its return type. `GET /failures/skip-defaults`
holds the only permit of a semaphore and calls one `SKIP` method per return type
([failure/SkipDefaultsService.java](src/main/java/in/riido/locksmith/demo/failure/SkipDefaultsService.java)):

```
$ curl -s 'localhost:8080/failures/skip-defaults'
{"boolean":"false","int":"0","Optional":"Optional.empty","OptionalInt":"OptionalInt.empty","String":"null","List":"null"}
```

## Metrics

Spring Boot Actuator shows Locksmith's two timers. `outcome:skipped` counts acquires whose wait ran out.

```
$ curl -s 'localhost:8080/actuator/metrics/locksmith.acquire'
{"availableTags":[{"tag":"primitive","values":["lock","semaphore"]},{"tag":"outcome","values":["acquired","skipped"]}],"baseUnit":"seconds","measurements":[{"statistic":"COUNT","value":39.0},...],"name":"locksmith.acquire"}
$ curl -s 'localhost:8080/actuator/metrics/locksmith.acquire?tag=primitive:lock&tag=outcome:skipped'
{"availableTags":[],"baseUnit":"seconds","measurements":[{"statistic":"COUNT","value":4.0},...],"name":"locksmith.acquire"}
$ curl -s 'localhost:8080/actuator/metrics/locksmith.held'
{"availableTags":[{"tag":"primitive","values":["lock","semaphore"]}],"baseUnit":"seconds","measurements":[{"statistic":"COUNT","value":24.0},...],"name":"locksmith.held"}
```

## Properties

| Property | Value here | Shown by |
|---|---|---|
| `locksmith.key-prefix` | `demo:` | every Redis key above; [PropertiesTest](src/test/java/in/riido/locksmith/demo/PropertiesTest.java) also shows the default `locksmith:` |
| `locksmith.semaphore.lease-time` | `1m` | the startup line; `PropertiesTest` sets `1s` and sees a permit without `leaseTime` expire |
| `locksmith.enabled` | not set (`true`) | `PropertiesTest`: with `false`, two calls run an annotated method at the same time; [StartupValidationTest](src/test/java/in/riido/locksmith/demo/StartupValidationTest.java): with `treu`, startup fails |

This app cannot run with `locksmith.enabled=false`: its services inject `LockOperations` and
`SemaphoreOperations`, which Locksmith then does not create. That is Locksmith's documented behaviour, and
`PropertiesTest` checks it.

## With other Spring annotations

**`@Transactional` with JDBC on H2**
([transaction/CounterService.java](src/main/java/in/riido/locksmith/demo/transaction/CounterService.java)).
`increment` reads a counter, works, and writes it back, under a lock and in a transaction. Locksmith's advice runs
outside the transaction, so the transaction commits before the lock is released. The method checks this at commit
time (`lockHeldAtCommit`). `increment-unlocked` does the same without the lock:

```
$ curl -s -X POST 'localhost:8080/counters/visits/increment?workMs=10'
{"name":"visits","value":1,"lockHeldAtCommit":true}
$ for i in $(seq 20); do curl -s -o /dev/null -X POST 'localhost:8080/counters/safe/increment?workMs=50' & done; wait; curl -s 'localhost:8080/counters/safe'
{"name":"safe","value":20}
$ for i in $(seq 20); do curl -s -o /dev/null -X POST 'localhost:8080/counters/unsafe/increment-unlocked?workMs=50' & done; wait; curl -s 'localhost:8080/counters/unsafe'
{"name":"unsafe","value":2}
```

**`@Cacheable`** ([cache/PriceService.java](src/main/java/in/riido/locksmith/demo/cache/PriceService.java)). Locksmith runs
before the cache, so a cache hit still takes the lock. With the default try once, cache hits that meet collide:

```
$ curl -s 'localhost:8080/prices/sku-1?workMs=100'; curl -s 'localhost:8080/prices/sku-1'
{"sku":"sku-1","cents":433,"computedAt":"2026-10-10T18:44:41.963515Z"}
{"sku":"sku-1","cents":433,"computedAt":"2026-10-10T18:44:41.963515Z"}
$ for i in $(seq 20); do curl -s -o /dev/null -w '%{http_code} ' 'localhost:8080/prices/sku-1' & done; wait
409 200 409 409 409 200 409 409 200 409 409 409 200 409 409 200 409 409 200 200
```

To serve cache hits without the lock, put `@Cacheable` on another bean that calls the locked method.

**`@Async`** ([async/AsyncService.java](src/main/java/in/riido/locksmith/demo/async/AsyncService.java)). Spring's `@Async`
runs first, so the lock is taken on the worker thread and held for the whole body. One method returns
`CompletableFuture`, the other `Future`:

```
$ curl -s -X POST 'localhost:8080/async/jobs/j1?workMs=100'
{"jobId":"j1","thread":"task-1","lockedAtStart":true,"lockedAtEnd":true}
$ curl -s -X POST 'localhost:8080/async/jobs/j1/future?workMs=100'
{"jobId":"j1","thread":"task-2","lockedAtStart":true,"lockedAtEnd":true}
```

**`@Retryable`** from Spring Framework 7 ([retry/RetryService.java](src/main/java/in/riido/locksmith/demo/retry/RetryService.java),
switched on by `@EnableResilientMethods` in [DemoApplication.java](src/main/java/in/riido/locksmith/demo/DemoApplication.java)).
Retries run outside Locksmith, so each attempt takes the lock again. Here the first two attempts fail:

```
$ curl -s -X POST 'localhost:8080/retries/call-1?failures=2'
{"callId":"call-1","attempts":3,"lockHeldPerAttempt":[true,true,true]}
```

[RetryTest](src/test/java/in/riido/locksmith/demo/retry/RetryTest.java) shows the `locksmith.acquire` count rising by 3 for
this one call.

## Interface clients

A Spring bean can itself be a JDK proxy of an interface. The annotation then sits on the interface method.

**HTTP interface client** ([httpclient/QuoteClient.java](src/main/java/in/riido/locksmith/demo/httpclient/QuoteClient.java)).
An `@HttpExchange` client with `@DistributedSemaphore(key = "upstream", permits = "2", waitTime = "10s")`. It calls a
slow "upstream", which is a controller in this same app
([UpstreamController.java](src/main/java/in/riido/locksmith/demo/httpclient/UpstreamController.java)) that records how many
calls it serves at once. Six parallel calls; the upstream never served more than two at a time:

```
$ curl -s 'localhost:8080/quotes/q1?workMs=100'
{"sku":"q1","cents":552}
$ curl -s -X DELETE 'localhost:8080/tracker'; for i in $(seq 6); do curl -s -o /dev/null "localhost:8080/quotes/q$i?workMs=500" & done; wait; curl -s 'localhost:8080/tracker'
{"semaphore:upstream":{"holder":0,"holderEntries":6,"holderMax":2,"limit":2}}
```

**Spring Data repository** ([repository/ProductRepository.java](src/main/java/in/riido/locksmith/demo/repository/ProductRepository.java)).
The query method `findBySku` takes a `READ` lock on `product:#{#sku}`. The CRUD method `save` is redeclared in the
interface to take a `WRITE` lock on `product:#{#p0.sku}`. Both try once.

```
$ curl -s 'localhost:8080/products/p-1'
{"id":1,"sku":"p-1","name":"Pen","price":120}
$ curl -s -X PUT 'localhost:8080/products/p-1?price=130'
{"id":1,"sku":"p-1","name":"Pen","price":130}
```

## Limitations you will meet

These are documented on Locksmith's [Limitations](https://github.com/riido-git/locksmith/wiki/Limitations) page.
The demo shows two of them.

**LIMITATION: self-invocation** ([limitation/SelfInvocationService.java](src/main/java/in/riido/locksmith/demo/limitation/SelfInvocationService.java)).
A call on `this` skips the Spring proxy, so the annotated method runs without its lock:

```
$ curl -s 'localhost:8080/limitations/self-invocation'
{"throughProxy":true,"calledOnThis":false}
```

**LIMITATION: Spring Data repository fragments** ([repository/ProductStats.java](src/main/java/in/riido/locksmith/demo/repository/ProductStats.java),
[ProductStatsImpl.java](src/main/java/in/riido/locksmith/demo/repository/ProductStatsImpl.java)). Each method returns the
free permits of its own semaphore of 2, seen from inside the call. Annotated on the fragment interface, the
repository and the fragment bean both take a permit, so one call holds both (0 left). Annotated on the repository
interface, or on the fragment's implementation class, one call takes one permit (1 left):

```
$ curl -s 'localhost:8080/products/fragment-permits'
{"onFragmentInterface":0,"onRepositoryInterface":1,"onFragmentImpl":1}
```

With `permits = "1"` on the fragment interface, every such call would fail.

## Tests

```
./mvnw verify
```

Docker must be running: the tests start Redis (`redis:8.10.2`) with Testcontainers. No test is set to skip when
Docker is missing, so check that the summary says `Skipped: 0`. The run takes about 70 seconds.

| Test | What it checks |
|---|---|
| [LockTest](src/test/java/in/riido/locksmith/demo/lock/LockTest.java) | try once, waitTime, READ and WRITE, fixed lease, renewal, the key prefix in Redis |
| [KeyTemplateTest](src/test/java/in/riido/locksmith/demo/keys/KeyTemplateTest.java) | the four kinds of key template |
| [SemaphoreTest](src/test/java/in/riido/locksmith/demo/semaphore/SemaphoreTest.java) | permits, waitTime, the count from a property, the permit lease, semaphore plus lock |
| [CartControllerTest](src/test/java/in/riido/locksmith/demo/api/CartControllerTest.java) | the annotations on controller methods: readers and writers, try once, the `HANDLER`'s response with `Retry-After`, a key from the JSON body, the semaphore |
| [ProgrammaticApiTest](src/test/java/in/riido/locksmith/demo/programmatic/ProgrammaticApiTest.java) | the handles, "not acquired" without an exception, `isLocked`, `availablePermits` |
| [FailureHandlingTest](src/test/java/in/riido/locksmith/demo/failure/FailureHandlingTest.java) | 409 and 429, `SKIP` defaults, `HANDLER` values |
| [MetricsTest](src/test/java/in/riido/locksmith/demo/MetricsTest.java) | `locksmith.acquire` and `locksmith.held` through Actuator |
| [TransactionTest](src/test/java/in/riido/locksmith/demo/transaction/TransactionTest.java), [CacheTest](src/test/java/in/riido/locksmith/demo/cache/CacheTest.java), [AsyncTest](src/test/java/in/riido/locksmith/demo/async/AsyncTest.java), [RetryTest](src/test/java/in/riido/locksmith/demo/retry/RetryTest.java) | the Spring integrations above |
| [HttpClientTest](src/test/java/in/riido/locksmith/demo/httpclient/HttpClientTest.java), [RepositoryTest](src/test/java/in/riido/locksmith/demo/repository/RepositoryTest.java) | the interface clients, and the fragment limitation |
| [SelfInvocationTest](src/test/java/in/riido/locksmith/demo/limitation/SelfInvocationTest.java) | the self-invocation limitation |
| [PropertiesTest](src/test/java/in/riido/locksmith/demo/PropertiesTest.java) | the three properties |
| [StartupValidationTest](src/test/java/in/riido/locksmith/demo/StartupValidationTest.java) | each misconfiguration stops startup with a `LocksmithConfigurationException`: a blank key, a key variable that is not a parameter, a final method, an `@Async` method returning `String`, one semaphore key with two counts, `REENTRANT` and `READ` on one key, `locksmith.enabled=treu`, `waitTime = "5x"` |
| [DemoApplicationTests](src/test/java/in/riido/locksmith/demo/DemoApplicationTests.java) | the app's Redisson client writes to the test container, not to localhost:6379 |
| [StressTest](src/test/java/in/riido/locksmith/demo/stress/StressTest.java) | the stress test below |

## Stress test (JUnit, part of `./mvnw verify`)

[StressTest](src/test/java/in/riido/locksmith/demo/stress/StressTest.java) starts three instances of the app in one JVM:
three Spring contexts, each with its own port, Tomcat and Redisson client. They share one Redis from Testcontainers
and one H2 database (`jdbc:h2:mem:stress`, shared because the three run in one JVM). For each scenario, many threads
call one feature over HTTP, spread over the three instances. After each scenario it checks
[the invariants](src/test/java/in/riido/locksmith/demo/stress/Invariants.java) through the app's own endpoints:

- a lock never had two holders at once, across instances;
- a semaphore never had more holders than permits, and under load it did reach them;
- a reader and a writer were never inside together, a writer was never with another writer, and readers did share;
- each guarded method ran exactly as often as the clients were told it ran, so `THROW`, `SKIP` and `HANDLER` counts
  are right;
- no counter update was lost;
- every lock and permit an instance acquired, it released once (`locksmith.acquire` equals `locksmith.held`);
- no lock key is left in Redis; only semaphore count keys stay, by design.

It checks invariants, not timings. Run it alone with `./mvnw test -Dtest=StressTest`. It takes about 40 seconds.

## Load test (Gatling)

The load test runs against [compose.load.yaml](compose.load.yaml): Redis, an H2 database server, and three
instances of the app built by the [Dockerfile](Dockerfile). The instances answer on 127.0.0.1:18081, 18082 and
18083. Redis and H2 publish no port on your machine.

```
./mvnw -DskipTests package
docker compose -f compose.load.yaml up -d --build --wait
./mvnw -Pload test-compile gatling:test
docker compose -f compose.load.yaml down -v
```

The simulation is [DemoLoadSimulation](src/gatling/java/in/riido/locksmith/demo/load/DemoLoadSimulation.java), in the
`load` Maven profile, so `./mvnw verify` needs no stack. For 60 seconds, 100 virtual users call every endpoint; each
request goes to one of the three instances at random. Then it checks the same invariants as the stress test,
through the app's endpoints. A broken invariant, or a response outside the expected statuses, fails the run.
`-DloadSeconds=15` makes it shorter. The report lands in `target/gatling/demoloadsimulation-<time>/index.html`.

## Measured results

Measured on 2026-10-11 on an Apple M2 Pro laptop (10 cores, 16 GB; Docker Desktop with 10 CPUs and 8 GB).

`./mvnw clean verify`: 99 tests, 0 failures, 0 errors, 0 skipped, 1 min 12 s. The stress test's table, where `Ran`
is how many times the guarded method ran:

```
Scenario                                       Requests    2xx  409/429    Ran    req/s  p50 ms  p95 ms  p99 ms
lock, try once: 409 when busy                      3000     79     2921     79     4869       8      17      33
lock, waitTime 5s                                   300    300        0    300      131     152     742    1044
lock, SKIP: false when busy                        3000   3000        0     55     7816       3       6      23
lock, HANDLER: busy when busy                      3000   3000        0     50     9237       2       5      21
lock, READ and WRITE on one key                     400    400        0    400      181      47     303     602
semaphore of 3, try once: 429 when full            3000     45     2955     45     5309       3       9      36
semaphore of 3, waitTime 10s                        300    300        0    300      139     105     528     717
semaphore of 3, SKIP: Optional.empty               3000   3000        0     40     5892       2       6      35
semaphore of 3, HANDLER: busy when full            3000   3000        0     51     4181       3      15      39
semaphore, permits = ${...} (2)                     200    200        0    200       91     100     659    1115
semaphore of 1, leaseTime 1s                       1000      9      991      9     4075       1       3      14
semaphore of 2, then a lock per tenant              300    259       41    259       78     195     988    1510
key templates                                       200    200        0    200      474      24      79     113
lock, leaseTime 1s, work 20ms                      1000      5      995      5     9083       0       0       1
lock renewed past the watchdog timeout               61      1       60      1        9       3       4    6509
LockOperations, WRITE, waitTime 5s                  200    200        0    200      124      83     327     664
SemaphoreOperations, 3 permits, waitTime 2s         200    200        0    200      107     109     374     570
isLocked and availablePermits                       200    200        0      -     4189       2       9      12
SKIP defaults per return type                      1000     33      967      -     4127       1       3       8
@Transactional read-modify-write under lock         300    300        0    300      200      54     412     718
control: same update without the lock               100    100        0 lost 95     1281      13      19      27
@Cacheable hits behind the lock, try once          3000    480     2520      0    10822       2       3       4
@Async CompletableFuture and Future                 200    200        0    200      126      88     314     456
@Retryable, 2 failures then success                  60     60        0    180       44     273     284     284
HTTP interface client, semaphore of 2               300    300        0    300       80     186     846    1290
repository, READ query method only                  300    300        0      -     2911       6      13      14
repository, READ query and WRITE save               300    200      100      -     4260       5       7       8
LIMITATION: fragment annotations                   1000     72      928      -     2945       1       3       4
LIMITATION: self-invocation                         100    100        0      -     1375       1      22      36
controller, READ and WRITE on one cart              600    454      146    454      302      18     226     447
controller, HANDLER: 409 and Retry-After           3000    229     2771    229     7524       1       8      14
controller, key from the body: 409 when busy       3000     31     2969     31     9075       4       7      17
controller, semaphore of 2: 429 when full          3000     25     2975     25     6895       2       4       7
instance 1: lock acquired 1375 times, released 1375 times
instance 1: semaphore acquired 725 times, released 725 times
instance 2: lock acquired 1368 times, released 1368 times
instance 2: semaphore acquired 731 times, released 731 times
instance 3: lock acquired 1386 times, released 1386 times
instance 3: semaphore acquired 717 times, released 717 times
Keys in the test container's Redis after the run (semaphore counts stay by design): [demo:semaphore:api-exports, demo:semaphore:exports, demo:semaphore:imports, demo:semaphore:invoices, demo:semaphore:lease-demo:permit, demo:semaphore:reports, demo:semaphore:skip-defaults, demo:semaphore:stats:on-fragment-impl, demo:semaphore:stats:on-fragment-interface, demo:semaphore:stats:on-repository, demo:semaphore:upstream]
```

Gatling, 60 seconds against the three containers: 536,262 requests, 0 failed, 8,512 requests per second, p50 4 ms, p95
38 ms, p99 77 ms. All 10 invariant checks passed. Across the three instances: 33 lock keys, 69,080 runs of a locked
method, never two holders of one lock. Each semaphore was full at some moment and never over: `exports` and
`api-exports` 3 of 3, `imports`, `reports`, `upstream` and `invoices` 2 of 2. The read/write locks had up to 6 readers
at once on the document and 4 on the cart, and no reader ever met a writer. Writers waited long on the document: of
176 write requests, 168 got the lock (p95 3.7 s) and 8 gave up after their 5 s wait. Readers keep a waiting writer
out, as the Limitations page says.

## Dependencies

Added to the Spring Initializr project (which already had `spring-boot-starter-webmvc`, `spring-boot-devtools`,
Lombok and `spring-boot-starter-webmvc-test`). Versions managed by Spring Boot 4.1.1 stay at its versions; the
others are the latest stable release. Dates and licences are read from Maven Central and from each POM.

| Dependency | Why | Version used | Latest release on Maven Central | Licence |
|---|---|---|---|---|
| `in.riido:locksmith-spring-boot-starter` | the library this demo shows | 4.0.0, 2026-10-11 | 4.0.0, 2026-10-11 | Apache License 2.0 |
| `org.redisson:redisson-spring-boot-starter` | the `RedissonClient` bean Locksmith needs | 4.8.0, 2026-10-02 | 4.8.0, 2026-10-02 | Apache 2.0 (`redisson-parent` POM) |
| `spring-boot-starter-actuator` | the metrics endpoint for Locksmith's timers | 4.1.1 (Boot), 2026-08-20 | 4.1.1 is the latest GA; 4.2.0-M2 milestone, 2026-09-24 | Apache 2.0 |
| `spring-boot-starter-cache` | `@Cacheable` | 4.1.1 (Boot) | as above | Apache 2.0 |
| `spring-boot-starter-data-jdbc` | JDBC, `@Transactional`, the Spring Data repository | 4.1.1 (Boot) | as above | Apache 2.0 |
| `spring-boot-starter-restclient` | the HTTP interface client | 4.1.1 (Boot) | as above | Apache 2.0 |
| `spring-boot-docker-compose` (runtime) | starts the Redis of `compose.yaml` | 4.1.1 (Boot) | as above | Apache 2.0 |
| `com.h2database:h2` (runtime) | the database | 2.4.240 (Boot), 2025-09-24 | 2.5.252, 2026-09-26 | MPL 2.0 or EPL 1.0 |
| `spring-boot-testcontainers` (test) | Redis containers in tests, read through `@ServiceConnection` | 4.1.1 (Boot) | as above | Apache 2.0 |
| `org.testcontainers:testcontainers` (test, through the above) | runs the containers | 2.0.5 (Boot), 2026-04-20 | 2.0.5, 2026-04-20 | MIT |
| `io.gatling.highcharts:gatling-charts-highcharts` (test, `load` profile) | Gatling and its HTML report | 3.16.0, 2026-09-29 | 3.16.0, 2026-09-29 | Gatling Highcharts licence: free use of the report module as delivered; no copying or changing of its code; no warranty; French law. Not an open-source licence. Gatling's own modules (`gatling-app`, `gatling-core`, `gatling-http`, `gatling-charts`) are Apache-2.0 |
| `io.gatling:gatling-maven-plugin` (`load` profile) | runs the simulation | 4.21.12, 2026-09-07 | 4.21.12, 2026-09-07 | Apache 2 |
| `org.codehaus.mojo:build-helper-maven-plugin` (`load` profile) | compiles `src/gatling/java` in that profile only | 3.6.1 (Boot), 2025-06-04 | 3.6.2, 2026-09-11 | MIT |
| `com.spotify.fmt:fmt-maven-plugin` | formats the Java sources in Google style on every build, as the Locksmith library does | 2.29, 2025-09-17 | 2.29, 2025-09-17 | MIT |

Docker images: `redis:8.10.2` (the same in `compose.yaml`, `compose.load.yaml` and the tests) and
`eclipse-temurin:25-jre` (the app image of the load test).

Gatling 3.16.0 asks for newer Netty, Jackson 2 and Caffeine than Spring Boot 4.1.1 manages. The `load` profile keeps
Spring Boot's versions, and the load test above ran on them.
