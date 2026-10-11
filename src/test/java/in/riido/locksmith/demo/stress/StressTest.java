package in.riido.locksmith.demo.stress;

import static org.assertj.core.api.Assertions.assertThat;

import in.riido.locksmith.demo.DemoApplication;
import in.riido.locksmith.demo.DemoHttp;
import in.riido.locksmith.demo.DemoHttp.Response;
import in.riido.locksmith.demo.SharedRedis;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Three instances of the demo in this JVM: three Spring contexts, each with its own port, Tomcat
 * and Redisson client. They share one Redis from Testcontainers and one H2 database. Many threads
 * call every endpoint over HTTP, spread over the three instances. After each scenario the
 * invariants are checked on what the app itself reports; at the end the run prints throughput and
 * latency per scenario. The checks are invariants, not timings.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StressTest {

  private static final GenericContainer<?> REDIS =
      new GenericContainer<>(SharedRedis.IMAGE).withExposedPorts(6379);

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private static final List<ConfigurableApplicationContext> INSTANCES = new ArrayList<>();

  private static final List<DemoHttp> HTTP = new ArrayList<>();

  private static final List<String> TABLE = new ArrayList<>();

  private static final List<String> NOTES = new ArrayList<>();

  @BeforeAll
  static void startThreeInstances() {
    REDIS.start();
    for (int i = 1; i <= 3; i++) {
      int port = DemoHttp.freePort();
      INSTANCES.add(
          new SpringApplicationBuilder(DemoApplication.class)
              .run(
                  "--server.port=" + port,
                  "--spring.data.redis.host=" + REDIS.getHost(),
                  "--spring.data.redis.port=" + REDIS.getMappedPort(6379),
                  // One named in-memory database, shared by the three instances because they run in
                  // one JVM.
                  "--spring.datasource.url=jdbc:h2:mem:stress;DB_CLOSE_DELAY=-1",
                  "--spring.docker.compose.enabled=false",
                  "--spring.main.banner-mode=off",
                  "--logging.level.root=WARN"));
      HTTP.add(new DemoHttp(port));
    }
  }

  @AfterAll
  static void stopInstances() {
    System.out.println();
    System.out.println(
        "Stress results: 3 instances, one Redis ("
            + SharedRedis.IMAGE
            + "), one H2 database."
            + " A scenario gets a row only when every check of it held.");
    System.out.printf(
        "%-46s %8s %6s %8s %6s %8s %7s %7s %7s%n",
        "Scenario", "Requests", "2xx", "409/429", "Ran", "req/s", "p50 ms", "p95 ms", "p99 ms");
    TABLE.forEach(System.out::println);
    NOTES.forEach(System.out::println);
    INSTANCES.forEach(ConfigurableApplicationContext::close);
    REDIS.stop();
  }

  @Test
  @Order(1)
  void lockTryOnce() {
    Run run =
        run(
            "lock, try once: 409 when busy",
            3000,
            48,
            i -> Call.post("/locks/orders/s" + i % 3 + "?workMs=15", "lock:order:s" + i % 3));
    run.assertStatuses(200, 409);
    int ran = check(run, r -> r.status() == 200 ? 1 : 0);
    assertThat(run.count(r -> r.status() == 409)).as("callers collided").isPositive();
    record(run, String.valueOf(ran));
  }

  @Test
  @Order(2)
  void lockWaitTime() {
    Run run =
        run(
            "lock, waitTime 5s",
            300,
            32,
            i -> Call.post("/locks/orders/w" + i % 2 + "/wait?workMs=10", "lock:order:w" + i % 2));
    run.assertStatuses(200);
    int ran = check(run, r -> 1);
    record(run, String.valueOf(ran));
  }

  @Test
  @Order(3)
  void lockSkip() {
    Run run =
        run(
            "lock, SKIP: false when busy",
            3000,
            32,
            i -> Call.post("/locks/orders/k" + i % 3 + "/skip?workMs=15", "lock:order:k" + i % 3));
    run.assertStatuses(200);
    int ran = check(run, r -> r.json().get("ran").asBoolean() ? 1 : 0);
    assertThat(run.count(r -> !r.json().get("ran").asBoolean())).as("calls skipped").isPositive();
    record(run, String.valueOf(ran));
  }

  @Test
  @Order(4)
  void lockHandler() {
    Run run =
        run(
            "lock, HANDLER: busy when busy",
            3000,
            32,
            i ->
                Call.post(
                    "/locks/orders/h" + i % 3 + "/handler?workMs=15", "lock:order:h" + i % 3));
    run.assertStatuses(200);
    int ran = check(run, r -> status(r).equals("done") ? 1 : 0);
    assertThat(run.count(r -> status(r).equals("busy"))).as("handler calls").isPositive();
    record(run, String.valueOf(ran));
  }

  @Test
  @Order(5)
  void readWriteLock() {
    Run run =
        run(
            "lock, READ and WRITE on one key",
            400,
            16,
            i ->
                i % 2 == 0
                    ? Call.put("/locks/documents/d0?workMs=5", "rw:document:d0")
                    : Call.get("/locks/documents/d0?workMs=5", "rw:document:d0"));
    // Readers can keep a waiting writer out (a documented limitation), so a writer may get 409.
    run.assertStatuses(200, 409);
    int ran = check(run, r -> r.status() == 200 ? 1 : 0);
    record(run, String.valueOf(ran));
  }

  @Test
  @Order(6)
  void semaphoreTryOnce() {
    Run run =
        run(
            "semaphore of 3, try once: 429 when full",
            3000,
            24,
            i -> Call.post("/semaphores/exports?workMs=30", "semaphore:exports"));
    run.assertStatuses(200, 429);
    int ran = check(run, r -> r.status() == 200 ? 1 : 0);
    assertThat(run.count(r -> r.status() == 429)).as("calls refused").isPositive();
    record(run, String.valueOf(ran));
  }

  @Test
  @Order(7)
  void semaphoreWaitTime() {
    Run run =
        run(
            "semaphore of 3, waitTime 10s",
            300,
            24,
            i -> Call.post("/semaphores/exports/wait?workMs=15", "semaphore:exports"));
    run.assertStatuses(200);
    int ran = check(run, r -> 1);
    record(run, String.valueOf(ran));
  }

  @Test
  @Order(8)
  void semaphoreSkip() {
    Run run =
        run(
            "semaphore of 3, SKIP: Optional.empty",
            3000,
            24,
            i -> Call.post("/semaphores/exports/skip?workMs=30", "semaphore:exports"));
    run.assertStatuses(200);
    int ran = check(run, r -> r.json().get("ran").asBoolean() ? 1 : 0);
    assertThat(run.count(r -> !r.json().get("ran").asBoolean())).as("calls skipped").isPositive();
    record(run, String.valueOf(ran));
  }

  @Test
  @Order(9)
  void semaphoreHandler() {
    Run run =
        run(
            "semaphore of 3, HANDLER: busy when full",
            3000,
            24,
            i -> Call.post("/semaphores/exports/handler?workMs=30", "semaphore:exports"));
    run.assertStatuses(200);
    int ran = check(run, r -> status(r).equals("done") ? 1 : 0);
    assertThat(run.count(r -> status(r).equals("busy"))).as("handler calls").isPositive();
    record(run, String.valueOf(ran));
  }

  @Test
  @Order(10)
  void semaphoreCountFromProperty() {
    Run run =
        run(
            "semaphore, permits = ${...} (2)",
            200,
            16,
            i -> Call.post("/semaphores/reports?workMs=15", "semaphore:reports"));
    run.assertStatuses(200);
    int ran = check(run, r -> 1);
    record(run, String.valueOf(ran));
  }

  @Test
  @Order(11)
  void semaphoreLease() {
    Run run =
        run(
            "semaphore of 1, leaseTime 1s",
            1000,
            8,
            i -> Call.post("/semaphores/lease?workMs=20", "semaphore:lease-demo:permit"));
    run.assertStatuses(200, 429);
    int ran = check(run, r -> r.status() == 200 ? 1 : 0);
    record(run, String.valueOf(ran));
  }

  @Test
  @Order(12)
  void semaphoreAndLockOnOneMethod() {
    Run run =
        run(
            "semaphore of 2, then a lock per tenant",
            300,
            24,
            i ->
                Call.post(
                    "/semaphores/imports/t" + i % 4 + "?workMs=20",
                    "semaphore:imports",
                    "lock:import:t" + i % 4));
    run.assertStatuses(200, 409);
    int ran = check(run, r -> r.status() == 200 ? 1 : 0);
    record(run, String.valueOf(ran));
  }

  @Test
  @Order(13)
  void keyTemplates() {
    Run run =
        run(
            "key templates",
            200,
            16,
            i ->
                switch (i % 4) {
                  case 0 -> Call.post("/keys/literal?workMs=5", "lock:report:daily");
                  case 1 -> Call.post("/keys/users/u" + i % 2 + "?workMs=5", "lock:user:u" + i % 2);
                  case 2 ->
                      new Call(
                          "POST",
                          "/keys/orders?workMs=5",
                          "{\"id\":\"o1\",\"customerId\":\"c" + i % 3 + "\"}",
                          List.of("lock:customer:c" + i % 3));
                  default -> Call.post("/keys/tenants/t1/regions/eu?workMs=5", "lock:tenant:t1:eu");
                });
    run.assertStatuses(200);
    assertThat(run.count(r -> !r.json().get("lockedWhileRunning").asBoolean())).isZero();
    int ran = check(run, r -> 1);
    record(run, String.valueOf(ran));
  }

  @Test
  @Order(14)
  void fixedLease() {
    Run run =
        run(
            "lock, leaseTime 1s, work 20ms",
            1000,
            8,
            i -> Call.post("/locks/lease/fixed?workMs=20", "lock:lease-demo:fixed"));
    run.assertStatuses(200, 409);
    assertThat(run.count(r -> r.status() == 200 && !r.json().get("stillLockedAtEnd").asBoolean()))
        .isZero();
    int ran = check(run, r -> r.status() == 200 ? 1 : 0);
    record(run, String.valueOf(ran));
  }

  @Test
  @Order(15)
  void renewedLease() throws InterruptedException {
    // Instance 1 holds the lock for 6.5s, past the 5s watchdog timeout. The other two instances try
    // to take it
    // every 100ms for as long as it runs, so attempts keep coming while Redisson renews the lock.
    HTTP.getFirst().delete("/tracker");
    Call hold = Call.post("/locks/lease/renewed?workMs=6500", "lock:lease-demo:renewed");
    Call attempt = Call.post("/locks/lease/renewed?workMs=1", "lock:lease-demo:renewed");
    long start = System.nanoTime();
    CompletableFuture<Result> holder =
        CompletableFuture.supplyAsync(
            () -> new Result(hold, HTTP.get(0).send(hold.method(), hold.path(), null)));
    Thread.sleep(200);
    List<Result> results = new ArrayList<>();
    while (!holder.isDone()) {
      results.add(
          new Result(attempt, HTTP.get(1 + results.size() % 2).send("POST", attempt.path(), null)));
      Thread.sleep(100);
    }
    results.add(holder.join());
    Run run = new Run("lock renewed past the watchdog timeout", results, System.nanoTime() - start);

    run.assertStatuses(200, 409);
    assertThat(holder.join().json().get("stillLockedAtEnd").asBoolean()).isTrue();
    assertThat(run.count(r -> r.status() == 409))
        .as("attempts refused while renewed")
        .isGreaterThan(30);
    int ran = check(run, r -> r.status() == 200 ? 1 : 0);
    record(run, String.valueOf(ran));
  }

  @Test
  @Order(16)
  void programmaticLock() {
    Run run =
        run(
            "LockOperations, WRITE, waitTime 5s",
            200,
            16,
            i ->
                Call.post(
                    "/programmatic/reports/p" + i % 2 + "/rebuild?workMs=10",
                    "lock:rebuild:p" + i % 2));
    run.assertStatuses(200);
    int ran = check(run, r -> r.json().get("acquired").asBoolean() ? 1 : 0);
    record(run, String.valueOf(ran));
  }

  @Test
  @Order(17)
  void programmaticSemaphore() {
    Run run =
        run(
            "SemaphoreOperations, 3 permits, waitTime 2s",
            200,
            16,
            i -> Call.post("/programmatic/exports?workMs=20", "semaphore:api-exports"));
    run.assertStatuses(200);
    int ran = check(run, r -> r.json().get("acquired").asBoolean() ? 1 : 0);
    record(run, String.valueOf(ran));
  }

  @Test
  @Order(18)
  void programmaticQueries() {
    Run run =
        run(
            "isLocked and availablePermits",
            200,
            16,
            i ->
                i % 2 == 0
                    ? Call.get("/programmatic/locks?key=order:s0")
                    : Call.get("/programmatic/semaphores?key=api-exports"));
    run.assertStatuses(200);
    assertThat(
            run.count(
                r ->
                    r.json().has("availablePermits")
                        && (r.json().get("availablePermits").asInt() < 0
                            || r.json().get("availablePermits").asInt() > 3)))
        .isZero();
    record(run, "-");
  }

  @Test
  @Order(19)
  void skipDefaults() {
    Run run =
        run("SKIP defaults per return type", 1000, 8, i -> Call.get("/failures/skip-defaults"));
    run.assertStatuses(200, 429);
    assertThat(
            run.count(
                r ->
                    r.status() == 200
                        && !r.body()
                            .equals(
                                "{\"boolean\":\"false\",\"int\":\"0\",\"Optional\":\"Optional.empty\","
                                    + "\"OptionalInt\":\"OptionalInt.empty\",\"String\":\"null\",\"List\":\"null\"}")))
        .isZero();
    record(run, "-");
  }

  @Test
  @Order(20)
  void transactionLosesNoUpdate() {
    Run run =
        run(
            "@Transactional read-modify-write under lock",
            300,
            24,
            i -> Call.post("/counters/stress/increment?workMs=2", "lock:counter:stress"));
    run.assertStatuses(200);
    int ran = check(run, r -> 1);
    assertThat(HTTP.getFirst().get("/counters/stress").json().get("value").asLong())
        .as("no lost update")
        .isEqualTo(300);
    assertThat(run.count(r -> !r.json().get("lockHeldAtCommit").asBoolean()))
        .as("commits after the unlock")
        .isZero();
    record(run, String.valueOf(ran));
  }

  @Test
  @Order(21)
  void sameUpdateWithoutTheLock() {
    Run run =
        run(
            "control: same update without the lock",
            100,
            24,
            i -> Call.post("/counters/control/increment-unlocked?workMs=10"));
    run.assertStatuses(200);
    long value = HTTP.getFirst().get("/counters/control").json().get("value").asLong();
    // Printed, not asserted: how many updates get lost depends on timing.
    record(run, "lost " + (100 - value));
  }

  @Test
  @Order(22)
  void cacheHitStillTakesTheLock() {
    // Fill each instance's own cache first, so every call below is a cache hit.
    for (DemoHttp instance : HTTP) {
      assertThat(instance.get("/prices/p0?workMs=1").status()).isEqualTo(200);
      assertThat(instance.get("/prices/p1?workMs=1").status()).isEqualTo(200);
    }
    Run run =
        run(
            "@Cacheable hits behind the lock, try once",
            3000,
            24,
            i -> Call.get("/prices/p" + i % 2));
    run.assertStatuses(200, 409);
    assertThat(tracker()).as("no price was computed again").isEmpty();
    assertThat(run.count(r -> r.status() == 409))
        .as("cache hits collided on the lock")
        .isPositive();
    record(run, "0");
  }

  @Test
  @Order(23)
  void asyncHoldsTheLockOnTheWorker() {
    Run run =
        run(
            "@Async CompletableFuture and Future",
            200,
            16,
            i -> {
              String job = "j" + i % 2;
              return Call.post(
                  "/async/jobs/" + job + (i % 4 < 2 ? "" : "/future") + "?workMs=10",
                  "lock:job:" + job);
            });
    run.assertStatuses(200);
    assertThat(
            run.count(
                r ->
                    !r.json().get("thread").asString().startsWith("task-")
                        || !r.json().get("lockedAtStart").asBoolean()
                        || !r.json().get("lockedAtEnd").asBoolean()))
        .isZero();
    int ran = check(run, r -> 1);
    record(run, String.valueOf(ran));
  }

  @Test
  @Order(24)
  void retryTakesTheLockPerAttempt() {
    Run run =
        run(
            "@Retryable, 2 failures then success",
            60,
            12,
            i -> Call.post("/retries/r" + i + "?failures=2", "lock:retry:r" + i));
    run.assertStatuses(200);
    assertThat(run.count(r -> r.json().get("attempts").asInt() != 3)).isZero();
    int ran = check(run, r -> r.json().get("attempts").asInt());
    record(run, String.valueOf(ran));
  }

  @Test
  @Order(25)
  void httpInterfaceClient() {
    Run run =
        run(
            "HTTP interface client, semaphore of 2",
            300,
            24,
            i -> Call.get("/quotes/q" + i % 5 + "?workMs=15", "semaphore:upstream"));
    run.assertStatuses(200);
    int ran = check(run, r -> 1);
    record(run, String.valueOf(ran));
  }

  @Test
  @Order(26)
  void repositoryReadersNeverBlockEachOther() {
    Run run =
        run(
            "repository, READ query method only",
            300,
            24,
            i -> Call.get("/products/p-" + (1 + i % 3)));
    run.assertStatuses(200);
    record(run, "-");
  }

  @Test
  @Order(27)
  void repositoryReadersAndWriters() {
    Run run =
        run(
            "repository, READ query and WRITE save",
            300,
            24,
            i ->
                i % 3 == 0
                    ? Call.put("/products/p-1?price=" + (100 + i))
                    : Call.get("/products/p-1"));
    run.assertStatuses(200, 409);
    record(run, "-");
  }

  @Test
  @Order(28)
  void fragmentOnInterfaceTakesTwoPermits() {
    Run run =
        run(
            "LIMITATION: fragment annotations",
            1000,
            4,
            i -> Call.get("/products/fragment-permits"));
    run.assertStatuses(200, 429);
    assertThat(run.count(r -> r.status() == 200)).isPositive();
    // One call holds both permits of the fragment-interface semaphore, every time.
    assertThat(
            run.count(r -> r.status() == 200 && r.json().get("onFragmentInterface").asInt() != 0))
        .isZero();
    record(run, "-");
  }

  @Test
  @Order(29)
  void selfInvocation() {
    Run run =
        run("LIMITATION: self-invocation", 100, 8, i -> Call.get("/limitations/self-invocation"));
    run.assertStatuses(200);
    assertThat(run.count(r -> !r.json().get("throughProxy").asBoolean())).isZero();
    record(run, "-");
  }

  @Test
  @Order(30)
  void controllerReadAndWrite() {
    Run run =
        run(
            "controller, READ and WRITE on one cart",
            600,
            16,
            i ->
                switch (i % 4) {
                  case 0, 2 -> Call.get("/api/carts/sc0?workMs=5", "rw:cart:sc0");
                  case 1 -> Call.put("/api/carts/sc0?workMs=5", "rw:cart:sc0");
                  default ->
                      new Call("PATCH", "/api/carts/sc0?workMs=5", null, List.of("rw:cart:sc0"));
                });
    // PATCH tries once. GET and PUT wait up to 5s, and readers can keep a waiting writer out.
    run.assertStatuses(200, 409);
    int ran = check(run, r -> r.status() == 200 ? 1 : 0);
    assertThat(run.count(r -> r.call().method().equals("PATCH") && r.status() == 409))
        .as("PATCH on a busy cart")
        .isPositive();
    record(run, String.valueOf(ran));
  }

  @Test
  @Order(31)
  void controllerHandlerAnswersBusy() {
    // The first 200 calls are GETs, so the read/write rules have readers that shared each cart.
    // Then come the
    // DELETEs: one at a time gets a cart, and the others meanwhile get BusyCartHandler's answer.
    Run run =
        run(
            "controller, HANDLER: 409 and Retry-After",
            3000,
            16,
            i -> {
              String cart = "sd" + i % 2;
              return i < 200
                  ? Call.get("/api/carts/" + cart + "?workMs=5", "rw:cart:" + cart)
                  : new Call(
                      "DELETE",
                      "/api/carts/" + cart + "?workMs=15",
                      null,
                      List.of("rw:cart:" + cart));
            });
    run.assertStatuses(200, 409);
    int ran = check(run, r -> r.status() == 200 ? 1 : 0);
    long busy = run.count(r -> r.call().method().equals("DELETE") && r.status() == 409);
    assertThat(busy).as("busy DELETEs").isPositive();
    // Each one is BusyCartHandler's own answer: 409, Retry-After: 1, and its body.
    assertThat(
            run.count(
                r ->
                    r.call().method().equals("DELETE")
                        && r.status() == 409
                        && r.response().headers().firstValue("Retry-After").orElse("").equals("1")
                        && status(r).equals("busy")))
        .as("busy DELETEs answered by BusyCartHandler")
        .isEqualTo(busy);
    record(run, String.valueOf(ran));
  }

  @Test
  @Order(32)
  void controllerKeyFromTheBody() {
    Run run =
        run(
            "controller, key from the body: 409 when busy",
            3000,
            48,
            i ->
                new Call(
                    "POST",
                    "/api/carts/checkout?workMs=15",
                    "{\"cartId\":\"so" + i % 2 + "\"}",
                    List.of("lock:checkout:so" + i % 2)));
    run.assertStatuses(200, 409);
    int ran = check(run, r -> r.status() == 200 ? 1 : 0);
    assertThat(run.count(r -> r.status() == 409)).as("double clicks refused").isPositive();
    record(run, String.valueOf(ran));
  }

  @Test
  @Order(33)
  void controllerSemaphore() {
    Run run =
        run(
            "controller, semaphore of 2: 429 when full",
            3000,
            24,
            i -> Call.get("/api/carts/si" + i % 5 + "/invoice?workMs=30", "semaphore:invoices"));
    run.assertStatuses(200, 429);
    int ran = check(run, r -> r.status() == 200 ? 1 : 0);
    assertThat(run.count(r -> r.status() == 429)).as("calls refused").isPositive();
    record(run, String.valueOf(ran));
  }

  @Test
  @Order(34)
  void everyAcquireWasReleasedOnce() {
    List<String> broken = new ArrayList<>();
    for (int i = 0; i < HTTP.size(); i++) {
      for (String primitive : List.of("lock", "semaphore")) {
        double acquired =
            count(
                HTTP.get(i),
                "locksmith.acquire?tag=primitive:" + primitive + "&tag=outcome:acquired");
        double held = count(HTTP.get(i), "locksmith.held?tag=primitive:" + primitive);
        assertThat(acquired).as("instance %d %s acquires", i + 1, primitive).isPositive();
        broken.addAll(Invariants.releases("instance " + (i + 1), primitive, acquired, held));
        NOTES.add(
            String.format(
                "instance %d: %s acquired %.0f times, released %.0f times",
                i + 1, primitive, acquired, held));
      }
    }
    assertThat(broken).isEmpty();
  }

  @Test
  @Order(35)
  void noLockKeyLeftInRedis() {
    List<String> throughApp =
        Arrays.asList(JSON.readValue(HTTP.getFirst().get("/tracker/keys").body(), String[].class));
    List<String> inContainer =
        Arrays.stream(redisCli("--scan").split("\n"))
            .filter(key -> !key.startsWith("tracker:"))
            .sorted()
            .toList();

    assertThat(throughApp).containsExactlyElementsOf(inContainer);
    assertThat(Invariants.leftoverKeys(inContainer, "demo:")).isEmpty();
    assertThat(inContainer).contains("demo:semaphore:exports", "demo:semaphore:upstream");
    NOTES.add(
        "Keys in the test container's Redis after the run (semaphore counts stay by design): "
            + inContainer);
  }

  private static Run run(String scenario, int requests, int threads, IntFunction<Call> calls) {
    HTTP.getFirst().delete("/tracker");
    List<Call> planned = IntStream.range(0, requests).mapToObj(calls).toList();
    List<Future<Result>> futures = new ArrayList<>();
    long start = System.nanoTime();
    try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
      for (int i = 0; i < requests; i++) {
        Call call = planned.get(i);
        DemoHttp instance = HTTP.get(i % HTTP.size());
        futures.add(
            pool.submit(
                () -> new Result(call, instance.send(call.method(), call.path(), call.body()))));
      }
    }
    long elapsed = System.nanoTime() - start;
    return new Run(scenario, futures.stream().map(Future::resultNow).toList(), elapsed);
  }

  /**
   * Checks the shared invariants after a scenario; returns how many times clients saw the body run.
   */
  private static int check(Run run, ToIntFunction<Result> runsOfTheBody) {
    Map<String, Long> seen = new TreeMap<>();
    for (Result result : run.results()) {
      int runs = runsOfTheBody.applyAsInt(result);
      result.call().sections().forEach(section -> seen.merge(section, (long) runs, Long::sum));
    }
    Map<String, Map<String, Object>> tracker = tracker();
    List<String> broken = new ArrayList<>(Invariants.tracker(tracker));
    broken.addAll(Invariants.runs(tracker, seen));
    assertThat(broken).as(run.scenario()).isEmpty();
    return run.results().stream().mapToInt(runsOfTheBody).sum();
  }

  private static void record(Run run, String ran) {
    long[] millis =
        run.results().stream().mapToLong(r -> r.response().nanos() / 1_000_000).sorted().toArray();
    TABLE.add(
        String.format(
            "%-46s %8d %6d %8d %6s %8.0f %7d %7d %7d",
            run.scenario(),
            run.results().size(),
            run.count(r -> r.status() / 100 == 2),
            run.count(r -> r.status() == 409 || r.status() == 429),
            ran,
            run.results().size() / (run.nanos() / 1e9),
            percentile(millis, 50),
            percentile(millis, 95),
            percentile(millis, 99)));
  }

  private static long percentile(long[] sorted, int percent) {
    return sorted[Math.max(0, (int) Math.ceil(percent / 100.0 * sorted.length) - 1)];
  }

  private static Map<String, Map<String, Object>> tracker() {
    return JSON.readValue(
        HTTP.getFirst().get("/tracker").body(),
        new TypeReference<Map<String, Map<String, Object>>>() {});
  }

  private static double count(DemoHttp instance, String meterAndTags) {
    Response response = instance.get("/actuator/metrics/" + meterAndTags);
    if (response.status() == 404) {
      return 0;
    }
    for (JsonNode measurement : response.json().get("measurements")) {
      if (measurement.get("statistic").asString().equals("COUNT")) {
        return measurement.get("value").asDouble();
      }
    }
    throw new IllegalStateException("No COUNT in " + response.body());
  }

  private static String status(Result result) {
    return result.json().get("status").asString();
  }

  private static String redisCli(String... args) {
    String[] command = new String[args.length + 1];
    command[0] = "redis-cli";
    System.arraycopy(args, 0, command, 1, args.length);
    try {
      return REDIS.execInContainer(command).getStdout().trim();
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  /** One HTTP call, and the tracker sections its body enters when it runs. */
  record Call(String method, String path, String body, List<String> sections) {

    static Call get(String path, String... sections) {
      return new Call("GET", path, null, List.of(sections));
    }

    static Call post(String path, String... sections) {
      return new Call("POST", path, null, List.of(sections));
    }

    static Call put(String path, String... sections) {
      return new Call("PUT", path, null, List.of(sections));
    }
  }

  record Result(Call call, Response response) {

    int status() {
      return response.status();
    }

    String body() {
      return response.body();
    }

    JsonNode json() {
      return response.json();
    }
  }

  record Run(String scenario, List<Result> results, long nanos) {

    long count(Predicate<Result> predicate) {
      return results.stream().filter(predicate).count();
    }

    void assertStatuses(Integer... allowed) {
      assertThat(results)
          .as(scenario)
          .allSatisfy(
              result -> assertThat(result.status()).as(result.body()).isIn((Object[]) allowed));
    }
  }
}
