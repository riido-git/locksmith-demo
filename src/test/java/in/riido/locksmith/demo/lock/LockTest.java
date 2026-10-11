package in.riido.locksmith.demo.lock;

import static org.assertj.core.api.Assertions.assertThat;

import in.riido.locksmith.LockType;
import in.riido.locksmith.demo.DemoHttp.Response;
import in.riido.locksmith.demo.FeatureTest;
import in.riido.locksmith.demo.SharedRedis;
import in.riido.locksmith.lock.LockHandle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * A lock held here, on the test thread, is held by another caller as far as the request threads are
 * concerned.
 */
class LockTest extends FeatureTest {

  @Test
  void tryOnceFailsWhileAnotherCallerHoldsTheOrder() {
    try (LockHandle held = locks.key("order:t1").acquire()) {
      assertThat(held.acquired()).isTrue();
      assertThat(http.post("/locks/orders/t1?workMs=1").status()).isEqualTo(409);
    }
    assertThat(http.post("/locks/orders/t1?workMs=1").json().get("status").asString())
        .isEqualTo("done");
  }

  @Test
  void waitTimeWaitsForTheOrderToBeReleased() throws Exception {
    LockHandle held = locks.key("order:t2").acquire();
    CompletableFuture<Response> waiting =
        CompletableFuture.supplyAsync(() -> http.post("/locks/orders/t2/wait?workMs=1"));

    Thread.sleep(300);
    assertThat(waiting).isNotDone();
    held.close();

    assertThat(waiting.get(5, TimeUnit.SECONDS).status()).isEqualTo(200);
  }

  @Test
  void readersShareTheDocumentAndAWriterWaitsForThem() throws Exception {
    LockHandle reading = locks.key("document:t3").type(LockType.READ).acquire();
    assertThat(http.get("/locks/documents/t3?workMs=1").status()).isEqualTo(200);

    CompletableFuture<Response> writing =
        CompletableFuture.supplyAsync(() -> http.put("/locks/documents/t3?workMs=1"));
    Thread.sleep(300);
    assertThat(writing).isNotDone();
    reading.close();

    assertThat(writing.get(5, TimeUnit.SECONDS).json().get("access").asString())
        .isEqualTo("written");
  }

  @Test
  void aWriterKeepsReadersOut() throws Exception {
    LockHandle writing = locks.key("document:t4").type(LockType.WRITE).acquire();
    CompletableFuture<Response> reading =
        CompletableFuture.supplyAsync(() -> http.get("/locks/documents/t4?workMs=1"));

    Thread.sleep(300);
    assertThat(reading).isNotDone();
    writing.close();

    assertThat(reading.get(5, TimeUnit.SECONDS).status()).isEqualTo(200);
  }

  @Test
  void aFixedLeaseRunsOutWhileTheMethodStillRuns() {
    assertThat(
            http.post("/locks/lease/fixed?workMs=2000").json().get("stillLockedAtEnd").asBoolean())
        .isFalse();
  }

  @Test
  void aLockWithoutLeaseTimeIsRenewedPastTheWatchdogTimeout() {
    // The watchdog timeout is 5s (demo.lock-watchdog-timeout); the method runs for 6s.
    assertThat(
            http.post("/locks/lease/renewed?workMs=6000")
                .json()
                .get("stillLockedAtEnd")
                .asBoolean())
        .isTrue();
  }

  @Test
  void theRedisKeyCarriesTheConfiguredPrefix() {
    try (LockHandle held = locks.key("order:t5").acquire()) {
      assertThat(held.key()).isEqualTo("demo:lock:order:t5");
      assertThat(SharedRedis.redisCli("EXISTS", "demo:lock:order:t5")).isEqualTo("1");
    }
    assertThat(SharedRedis.redisCli("EXISTS", "demo:lock:order:t5")).isEqualTo("0");
  }
}
