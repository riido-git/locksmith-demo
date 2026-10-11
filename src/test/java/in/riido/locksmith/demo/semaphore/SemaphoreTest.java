package in.riido.locksmith.demo.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import in.riido.locksmith.demo.DemoHttp.Response;
import in.riido.locksmith.demo.FeatureTest;
import in.riido.locksmith.lock.LockHandle;
import in.riido.locksmith.semaphore.PermitHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SemaphoreTest extends FeatureTest {

  @Test
  void tryOnceFailsWhenAllPermitsAreTaken() {
    List<PermitHandle> taken = takeExportPermits(3);
    try {
      assertThat(http.post("/semaphores/exports?workMs=1").status()).isEqualTo(429);
      taken.removeFirst().close();
      assertThat(http.post("/semaphores/exports?workMs=1").status()).isEqualTo(200);
    } finally {
      taken.forEach(PermitHandle::close);
    }
  }

  @Test
  void waitTimeWaitsForAPermit() throws Exception {
    List<PermitHandle> taken = takeExportPermits(3);
    try {
      CompletableFuture<Response> waiting =
          CompletableFuture.supplyAsync(() -> http.post("/semaphores/exports/wait?workMs=1"));
      Thread.sleep(300);
      assertThat(waiting).isNotDone();
      taken.removeFirst().close();
      assertThat(waiting.get(5, TimeUnit.SECONDS).status()).isEqualTo(200);
    } finally {
      taken.forEach(PermitHandle::close);
    }
  }

  @Test
  void permitCountComesFromAProperty() {
    assertThat(http.post("/semaphores/reports?workMs=1").status()).isEqualTo(200);
    // demo.reports.max-concurrent=2 was written to Redis by the first acquire.
    assertThat(semaphores.availablePermits("reports")).isEqualTo(2);
  }

  @Test
  void aPermitLeaseRunsOutWhileTheMethodStillRuns() {
    Response result = http.post("/semaphores/lease?workMs=2000");
    assertThat(result.json().get("availablePermitsAtEnd").asInt()).isEqualTo(1);
  }

  @Test
  void permitFirstThenLock() {
    try (LockHandle tenantBusy = locks.key("import:t1").acquire()) {
      assertThat(http.post("/semaphores/imports/t1?workMs=1").status()).isEqualTo(409);
      // The permit was released before the lock failure was reported.
      assertThat(semaphores.availablePermits("imports")).isEqualTo(2);
      assertThat(http.post("/semaphores/imports/t2?workMs=1").status()).isEqualTo(200);
    }
    assertThat(http.post("/semaphores/imports/t1?workMs=1").status()).isEqualTo(200);
  }

  private List<PermitHandle> takeExportPermits(int count) {
    List<PermitHandle> taken = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      PermitHandle permit = semaphores.key("exports").permits(3).acquire();
      assertThat(permit.acquired()).isTrue();
      taken.add(permit);
    }
    return taken;
  }
}
