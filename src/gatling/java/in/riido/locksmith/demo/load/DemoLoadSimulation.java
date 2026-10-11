package in.riido.locksmith.demo.load;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.bodyString;
import static io.gatling.javaapi.core.CoreDsl.doSwitch;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jmesPath;
import static io.gatling.javaapi.core.CoreDsl.onCase;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.header;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import in.riido.locksmith.demo.stress.Invariants;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.CheckBuilder;
import io.gatling.javaapi.core.Choice;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Session;
import io.gatling.javaapi.core.Simulation;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;

/**
 * Load on the three instances of compose.load.yaml. Each lane is one endpoint, called in a loop by
 * a fixed number of virtual users for the load duration; each request goes to one of the three
 * instances at random. When the load is over, one more scenario checks the invariants through the
 * app's own endpoints, with the same rules as the JUnit stress suite. A broken invariant, or a
 * response outside the expected statuses, is a failed request, and the run fails.
 */
public class DemoLoadSimulation extends Simulation {

  private static final List<String> INSTANCES =
      List.of(
          System.getProperty(
                  "instances",
                  "http://localhost:18081,http://localhost:18082,http://localhost:18083")
              .split(","));

  private static final Duration LOAD = Duration.ofSeconds(Long.getLong("loadSeconds", 60));

  /** Unique to this run, so a second run on the same stack starts from a fresh counter. */
  private static final String RUN = Long.toString(System.currentTimeMillis(), 36);

  private static final String COUNTER = "load-" + RUN;

  /** What the clients saw: per tracker section, how many times its guarded body ran. */
  private static final Map<String, LongAdder> RUNS = new ConcurrentHashMap<>();

  private static final String SKIP_DEFAULTS =
      "{\"boolean\":\"false\",\"int\":\"0\",\"Optional\":\"Optional.empty\","
          + "\"OptionalInt\":\"OptionalInt.empty\",\"String\":\"null\",\"List\":\"null\"}";

  private record Lane(String name, int users, ChainBuilder chain) {}

  {
    List<Lane> lanes =
        List.of(
            new Lane(
                "lock, try once",
                6,
                pick(3)
                    .exec(
                        http("lock, try once")
                            .post("#{base}/locks/orders/g#{n}?workMs=15")
                            .check(status().in(200, 409), ranWhen200()))
                    .exec(session -> record(session, "lock:order:g" + session.getInt("n")))),
            new Lane(
                "lock, waitTime 5s",
                4,
                pick(2)
                    .exec(
                        http("lock, waitTime 5s")
                            .post("#{base}/locks/orders/gw#{n}/wait?workMs=10")
                            .check(status().is(200), ranWhen200()))
                    .exec(session -> record(session, "lock:order:gw" + session.getInt("n")))),
            new Lane(
                "lock, SKIP",
                3,
                pick(3)
                    .exec(
                        http("lock, SKIP")
                            .post("#{base}/locks/orders/gk#{n}/skip?workMs=15")
                            .check(status().is(200), jmesPath("ran").ofBoolean().saveAs("ran")))
                    .exec(session -> record(session, "lock:order:gk" + session.getInt("n")))),
            new Lane(
                "lock, HANDLER",
                3,
                pick(3)
                    .exec(
                        http("lock, HANDLER")
                            .post("#{base}/locks/orders/gh#{n}/handler?workMs=15")
                            .check(status().is(200), ranWhenDone()))
                    .exec(session -> record(session, "lock:order:gh" + session.getInt("n")))),
            new Lane(
                "lock, READ",
                6,
                pick(1)
                    .exec(
                        http("lock, READ")
                            .get("#{base}/locks/documents/gd0?workMs=5")
                            .check(status().in(200, 409), ranWhen200()))
                    .exec(session -> record(session, "rw:document:gd0"))),
            new Lane(
                "lock, WRITE",
                3,
                pick(1)
                    .exec(
                        http("lock, WRITE")
                            .put("#{base}/locks/documents/gd0?workMs=5")
                            .check(status().in(200, 409), ranWhen200()))
                    .exec(session -> record(session, "rw:document:gd0"))),
            new Lane(
                "lock, leaseTime 1s",
                2,
                pick(1)
                    .exec(
                        http("lock, leaseTime 1s")
                            .post("#{base}/locks/lease/fixed?workMs=20")
                            .check(status().in(200, 409), ranWhen200())
                            .checkIf((response, session) -> response.status().code() == 200)
                            .then(jmesPath("stillLockedAtEnd").ofBoolean().is(true)))
                    .exec(session -> record(session, "lock:lease-demo:fixed"))),
            new Lane(
                "lock, renewed past the watchdog",
                2,
                pick(1)
                    .exec(
                        http("lock, renewed past the watchdog")
                            .post("#{base}/locks/lease/renewed?workMs=5500")
                            .check(status().in(200, 409), ranWhen200())
                            .checkIf((response, session) -> response.status().code() == 200)
                            .then(jmesPath("stillLockedAtEnd").ofBoolean().is(true)))
                    .exec(session -> record(session, "lock:lease-demo:renewed"))
                    .pause(Duration.ofMillis(100))),
            new Lane(
                "key templates",
                2,
                pick(4)
                    .exec(
                        doSwitch("#{n}")
                            .on(
                                keyTemplate(
                                    0, "#{base}/keys/literal?workMs=5", null, "lock:report:daily"),
                                keyTemplate(
                                    1, "#{base}/keys/users/gu1?workMs=5", null, "lock:user:gu1"),
                                keyTemplate(
                                    2,
                                    "#{base}/keys/orders?workMs=5",
                                    "{\"id\":\"o1\",\"customerId\":\"gc1\"}",
                                    "lock:customer:gc1"),
                                keyTemplate(
                                    3,
                                    "#{base}/keys/tenants/gt/regions/eu?workMs=5",
                                    null,
                                    "lock:tenant:gt:eu")))),
            new Lane(
                "semaphore, try once",
                4,
                pick(1)
                    .exec(
                        http("semaphore, try once")
                            .post("#{base}/semaphores/exports?workMs=30")
                            .check(status().in(200, 429), ranWhen200()))
                    .exec(session -> record(session, "semaphore:exports"))),
            new Lane(
                "semaphore, waitTime 10s",
                4,
                pick(1)
                    .exec(
                        http("semaphore, waitTime 10s")
                            .post("#{base}/semaphores/exports/wait?workMs=15")
                            .check(status().is(200), ranWhen200()))
                    .exec(session -> record(session, "semaphore:exports"))),
            new Lane(
                "semaphore, SKIP",
                2,
                pick(1)
                    .exec(
                        http("semaphore, SKIP")
                            .post("#{base}/semaphores/exports/skip?workMs=30")
                            .check(status().is(200), jmesPath("ran").ofBoolean().saveAs("ran")))
                    .exec(session -> record(session, "semaphore:exports"))),
            new Lane(
                "semaphore, HANDLER",
                2,
                pick(1)
                    .exec(
                        http("semaphore, HANDLER")
                            .post("#{base}/semaphores/exports/handler?workMs=30")
                            .check(status().is(200), ranWhenDone()))
                    .exec(session -> record(session, "semaphore:exports"))),
            new Lane(
                "semaphore, permits from a property",
                4,
                pick(1)
                    .exec(
                        http("semaphore, permits from a property")
                            .post("#{base}/semaphores/reports?workMs=15")
                            .check(status().is(200), ranWhen200()))
                    .exec(session -> record(session, "semaphore:reports"))),
            new Lane(
                "semaphore, leaseTime 1s",
                2,
                pick(1)
                    .exec(
                        http("semaphore, leaseTime 1s")
                            .post("#{base}/semaphores/lease?workMs=20")
                            .check(status().in(200, 429), ranWhen200()))
                    .exec(session -> record(session, "semaphore:lease-demo:permit"))),
            new Lane(
                "semaphore, then a lock",
                4,
                pick(4)
                    .exec(
                        http("semaphore, then a lock")
                            .post("#{base}/semaphores/imports/gt#{n}?workMs=20")
                            .check(status().in(200, 409), ranWhen200()))
                    .exec(
                        session ->
                            record(
                                session,
                                "semaphore:imports",
                                "lock:import:gt" + session.getInt("n")))),
            new Lane(
                "LockOperations",
                3,
                pick(2)
                    .exec(
                        http("LockOperations")
                            .post("#{base}/programmatic/reports/gp#{n}/rebuild?workMs=10")
                            .check(
                                status().is(200), jmesPath("acquired").ofBoolean().saveAs("ran")))
                    .exec(session -> record(session, "lock:rebuild:gp" + session.getInt("n")))),
            new Lane(
                "SemaphoreOperations",
                4,
                pick(1)
                    .exec(
                        http("SemaphoreOperations")
                            .post("#{base}/programmatic/exports?workMs=20")
                            .check(
                                status().is(200), jmesPath("acquired").ofBoolean().saveAs("ran")))
                    .exec(session -> record(session, "semaphore:api-exports"))),
            new Lane(
                "isLocked, availablePermits",
                1,
                pick(1)
                    .exec(
                        http("isLocked")
                            .get("#{base}/programmatic/locks?key=order:g0")
                            .check(status().is(200)))
                    .exec(
                        http("availablePermits")
                            .get("#{base}/programmatic/semaphores?key=exports")
                            .check(status().is(200), jmesPath("availablePermits").ofInt().lte(3)))
                    .pause(Duration.ofMillis(50))),
            new Lane(
                "SKIP defaults",
                1,
                pick(1)
                    .exec(
                        http("SKIP defaults")
                            .get("#{base}/failures/skip-defaults")
                            .check(status().in(200, 429))
                            .checkIf((response, session) -> response.status().code() == 200)
                            .then(bodyString().is(SKIP_DEFAULTS)))),
            new Lane(
                "@Transactional",
                4,
                pick(1)
                    .exec(
                        http("@Transactional")
                            .post("#{base}/counters/" + COUNTER + "/increment?workMs=2")
                            .check(
                                status().is(200),
                                jmesPath("lockHeldAtCommit").ofBoolean().is(true),
                                ranWhen200()))
                    .exec(session -> record(session, "lock:counter:" + COUNTER))),
            new Lane(
                "@Cacheable",
                3,
                pick(2)
                    .exec(
                        http("@Cacheable")
                            .get("#{base}/prices/gp#{n}?workMs=30")
                            .check(status().in(200, 409)))),
            new Lane(
                "@Async",
                3,
                pick(2)
                    .exec(
                        http("@Async")
                            .post("#{base}/async/jobs/gj#{n}?workMs=10")
                            .check(
                                status().is(200),
                                jmesPath("thread")
                                    .transform(thread -> thread.startsWith("task-"))
                                    .is(true),
                                jmesPath("lockedAtStart").ofBoolean().is(true),
                                jmesPath("lockedAtEnd").ofBoolean().is(true),
                                ranWhen200()))
                    .exec(session -> record(session, "lock:job:gj" + session.getInt("n")))
                    .exec(
                        http("@Async Future")
                            .post("#{base}/async/jobs/gj#{n}/future?workMs=10")
                            .check(
                                status().is(200),
                                jmesPath("lockedAtEnd").ofBoolean().is(true),
                                ranWhen200()))
                    .exec(session -> record(session, "lock:job:gj" + session.getInt("n")))),
            new Lane(
                "@Retryable",
                2,
                pick(1)
                    .exec(
                        http("@Retryable")
                            .post("#{base}/retries/gr#{userId}-" + RUN + "?failures=2")
                            .check(
                                status().is(200),
                                jmesPath("attempts").ofInt().is(3).saveAs("runs")))
                    .exec(
                        session ->
                            record(session, "lock:retry:gr" + session.userId() + "-" + RUN))),
            new Lane(
                "HTTP interface client",
                4,
                pick(5)
                    .exec(
                        http("HTTP interface client")
                            .get("#{base}/quotes/gq#{n}?workMs=15")
                            .check(status().is(200), ranWhen200()))
                    .exec(session -> record(session, "semaphore:upstream"))),
            new Lane(
                "repository, READ",
                2,
                pick(3)
                    .exec(session -> session.set("sku", "p-" + (session.getInt("n") + 1)))
                    .exec(
                        http("repository, READ")
                            .get("#{base}/products/#{sku}")
                            .check(status().in(200, 409)))),
            new Lane(
                "repository, WRITE",
                1,
                pick(900)
                    .exec(
                        http("repository, WRITE")
                            .put("#{base}/products/p-1?price=#{n}")
                            .check(status().in(200, 409)))
                    .pause(Duration.ofMillis(20))),
            new Lane(
                "LIMITATION: fragment",
                1,
                pick(1)
                    .exec(
                        http("LIMITATION: fragment")
                            .get("#{base}/products/fragment-permits")
                            .check(status().in(200, 429))
                            .checkIf((response, session) -> response.status().code() == 200)
                            .then(jmesPath("onFragmentInterface").ofInt().is(0)))),
            new Lane(
                "LIMITATION: self-invocation",
                1,
                pick(1)
                    .exec(
                        http("LIMITATION: self-invocation")
                            .get("#{base}/limitations/self-invocation")
                            .check(
                                status().is(200), jmesPath("throughProxy").ofBoolean().is(true)))),
            new Lane(
                "controller, GET",
                4,
                pick(1)
                    .exec(
                        http("controller, GET")
                            .get("#{base}/api/carts/gc0?workMs=5")
                            .check(status().in(200, 409), ranWhen200()))
                    .exec(session -> record(session, "rw:cart:gc0"))),
            new Lane(
                "controller, PUT",
                2,
                pick(1)
                    .exec(
                        http("controller, PUT")
                            .put("#{base}/api/carts/gc0?workMs=5")
                            .check(status().in(200, 409), ranWhen200()))
                    .exec(session -> record(session, "rw:cart:gc0"))),
            new Lane(
                "controller, PATCH",
                2,
                pick(1)
                    .exec(
                        http("controller, PATCH")
                            .patch("#{base}/api/carts/gc0?workMs=5")
                            .check(status().in(200, 409), ranWhen200()))
                    .exec(session -> record(session, "rw:cart:gc0"))),
            new Lane(
                "controller, DELETE",
                2,
                pick(1)
                    .exec(
                        http("controller, DELETE")
                            .delete("#{base}/api/carts/gc0?workMs=5")
                            .check(status().in(200, 409), ranWhen200())
                            .checkIf((response, session) -> response.status().code() == 409)
                            .then(header("Retry-After").is("1"), jmesPath("status").is("busy")))
                    .exec(session -> record(session, "rw:cart:gc0"))),
            new Lane(
                "controller, checkout",
                3,
                pick(3)
                    .exec(
                        http("controller, checkout")
                            .post("#{base}/api/carts/checkout?workMs=15")
                            .body(StringBody("{\"cartId\":\"gc#{n}\"}"))
                            .asJson()
                            .check(status().in(200, 409), ranWhen200()))
                    .exec(session -> record(session, "lock:checkout:gc" + session.getInt("n")))),
            new Lane(
                "controller, invoice",
                4,
                pick(1)
                    .exec(
                        http("controller, invoice")
                            .get("#{base}/api/carts/gc0/invoice?workMs=30")
                            .check(status().in(200, 429), ranWhen200()))
                    .exec(session -> record(session, "semaphore:invoices"))));

    List<String> slots = new ArrayList<>();
    lanes.forEach(
        lane -> {
          for (int i = 0; i < lane.users(); i++) {
            slots.add(lane.name());
          }
        });
    ScenarioBuilder traffic =
        scenario("traffic")
            .exec(
                session ->
                    session
                        .set("lane", slots.get((int) ((session.userId() - 1) % slots.size())))
                        .set("userId", session.userId()))
            // exitASAP false: an iteration running when the time is up finishes, so its response is
            // counted too.
            .during(LOAD, false)
            .on(
                doSwitch("#{lane}")
                    .on(
                        lanes.stream()
                            .map(lane -> onCase(lane.name()).then(lane.chain()))
                            .toList()));

    setUp(
            traffic
                .injectOpen(atOnceUsers(slots.size()))
                .andThen(invariants().injectOpen(atOnceUsers(1))))
        .assertions(global().failedRequests().count().is(0L));
  }

  /** Starts from an empty tracker, so the rules judge this run only. */
  @Override
  public void before() {
    try {
      HttpRequest reset =
          HttpRequest.newBuilder(URI.create(INSTANCES.getFirst() + "/tracker")).DELETE().build();
      HttpResponse<Void> response =
          HttpClient.newHttpClient().send(reset, HttpResponse.BodyHandlers.discarding());
      if (response.statusCode() != 200) {
        throw new IllegalStateException("Tracker reset failed: HTTP " + response.statusCode());
      }
    } catch (Exception ex) {
      throw new IllegalStateException(
          "Is the stack of compose.load.yaml up? " + ex.getMessage(), ex);
    }
  }

  /** One request per rule, after the load; its check passes only when the rule held. */
  private static ScenarioBuilder invariants() {
    String first = INSTANCES.getFirst();
    ChainBuilder checks =
        exec(
            http("invariant: one holder per lock, permits respected and used, readers and writers apart")
                .get(first + "/tracker")
                .check(
                    jmesPath("@")
                        .ofMap()
                        .transform(sections -> verdict(Invariants.tracker(sections)))
                        .is("ok")),
            http("invariant: every body run the clients saw, and no other")
                .get(first + "/tracker")
                .check(
                    jmesPath("@")
                        .ofMap()
                        .transform(sections -> verdict(Invariants.runs(sections, runsSeen())))
                        .is("ok")),
            http("invariant: no counter update lost")
                .get(first + "/counters/" + COUNTER)
                .check(
                    jmesPath("value")
                        .ofLong()
                        .is(session -> runsSeen().getOrDefault("lock:counter:" + COUNTER, 0L))),
            http("invariant: no lock key left in Redis")
                .get(first + "/tracker/keys")
                .check(
                    jmesPath("@")
                        .ofList()
                        .transform(
                            keys ->
                                verdict(
                                    Invariants.leftoverKeys(
                                        keys.stream().map(String::valueOf).toList(), "demo:")))
                        .is("ok")));
    for (int i = 0; i < INSTANCES.size(); i++) {
      String instance = "instance " + (i + 1);
      for (String primitive : List.of("lock", "semaphore")) {
        String metrics = INSTANCES.get(i) + "/actuator/metrics/";
        checks =
            checks
                .exec(
                    http(instance + " " + primitive + " acquires")
                        .get(
                            metrics
                                + "locksmith.acquire?tag=primitive:"
                                + primitive
                                + "&tag=outcome:acquired")
                        .check(countOf().saveAs("acquired")))
                .exec(
                    http("invariant: " + instance + " released every " + primitive + " it acquired")
                        .get(metrics + "locksmith.held?tag=primitive:" + primitive)
                        .check(
                            countOf()
                                .transformWithSession(
                                    (held, session) ->
                                        verdict(
                                            Invariants.releases(
                                                instance,
                                                primitive,
                                                session.<Double>get("acquired"),
                                                held)))
                                .is("ok")));
      }
    }
    return scenario("invariants").exec(checks);
  }

  /**
   * The next request goes to a random instance; #{n} is a random number below {@code keys}, for its
   * key.
   */
  private static ChainBuilder pick(int keys) {
    return exec(
        session ->
            session
                .set("base", INSTANCES.get(ThreadLocalRandom.current().nextInt(INSTANCES.size())))
                .set("n", ThreadLocalRandom.current().nextInt(keys)));
  }

  private static Choice.WithKey keyTemplate(int n, String url, String body, String section) {
    var request =
        body == null
            ? http("key templates").post(url)
            : http("key templates").post(url).body(StringBody(body)).asJson();
    return onCase(n)
        .then(
            exec(request.check(
                    status().is(200),
                    jmesPath("lockedWhileRunning").ofBoolean().is(true),
                    ranWhen200()))
                .exec(session -> record(session, section)));
  }

  private static CheckBuilder ranWhen200() {
    return status().transform(code -> code == 200).saveAs("ran");
  }

  private static CheckBuilder ranWhenDone() {
    return jmesPath("status").transform(status -> status.equals("done")).saveAs("ran");
  }

  private static CheckBuilder.Validate<Double> countOf() {
    return jmesPath("measurements[?statistic=='COUNT'].value | [0]").ofDouble().withDefault(0.0);
  }

  /** Adds the body runs this response showed to each section, then clears the saved values. */
  private static Session record(Session session, String... sections) {
    int runs =
        session.contains("runs")
            ? session.getInt("runs")
            : session.contains("ran") && session.getBoolean("ran") ? 1 : 0;
    for (String section : sections) {
      RUNS.computeIfAbsent(section, key -> new LongAdder()).add(runs);
    }
    return session.remove("ran").remove("runs");
  }

  private static Map<String, Long> runsSeen() {
    Map<String, Long> seen = new TreeMap<>();
    RUNS.forEach((section, runs) -> seen.put(section, runs.sum()));
    return seen;
  }

  private static String verdict(List<String> broken) {
    return broken.isEmpty() ? "ok" : String.join("; ", broken);
  }
}
