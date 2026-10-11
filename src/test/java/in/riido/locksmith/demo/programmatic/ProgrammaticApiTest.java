package in.riido.locksmith.demo.programmatic;

import static org.assertj.core.api.Assertions.assertThat;

import in.riido.locksmith.demo.FeatureTest;
import in.riido.locksmith.lock.LockHandle;
import in.riido.locksmith.semaphore.PermitHandle;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class ProgrammaticApiTest extends FeatureTest {

  @Test
  void lockHandle() {
    JsonNode result = http.post("/programmatic/reports/r1/rebuild?workMs=1").json();
    assertThat(result.get("acquired").asBoolean()).isTrue();
    assertThat(result.get("key").asString()).isEqualTo("demo:rwlock:rebuild:r1");
  }

  @Test
  void permitHandle() {
    JsonNode result = http.post("/programmatic/exports?workMs=1").json();
    assertThat(result.get("acquired").asBoolean()).isTrue();
    assertThat(result.get("key").asString()).isEqualTo("demo:semaphore:api-exports");
    assertThat(result.get("permitId").asString()).isNotBlank();
  }

  @Test
  void notAcquiredIsReportedNotThrown() {
    List<PermitHandle> taken = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      taken.add(semaphores.key("api-exports").permits(3).acquire());
    }
    try {
      // Waits its 2 seconds, then reports that it got no permit.
      JsonNode result = http.post("/programmatic/exports?workMs=1").json();
      assertThat(result.get("acquired").asBoolean()).isFalse();
      assertThat(result.get("permitId").isNull()).isTrue();
    } finally {
      taken.forEach(PermitHandle::close);
    }
  }

  @Test
  void isLocked() {
    try (LockHandle held = locks.key("order:p1").acquire()) {
      assertThat(http.get("/programmatic/locks?key=order:p1").json().get("locked").asBoolean())
          .isTrue();
    }
    assertThat(http.get("/programmatic/locks?key=order:p1").json().get("locked").asBoolean())
        .isFalse();
  }

  @Test
  void availablePermits() {
    try (PermitHandle permit = semaphores.key("api-exports").permits(3).acquire()) {
      assertThat(
              http.get("/programmatic/semaphores?key=api-exports")
                  .json()
                  .get("availablePermits")
                  .asInt())
          .isEqualTo(2);
    }
  }
}
