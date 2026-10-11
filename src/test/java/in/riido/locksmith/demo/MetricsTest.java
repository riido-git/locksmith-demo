package in.riido.locksmith.demo;

import static org.assertj.core.api.Assertions.assertThat;

import in.riido.locksmith.lock.LockHandle;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Locksmith's two timers, read through Spring Boot Actuator's metrics endpoint. */
class MetricsTest extends FeatureTest {

  @Test
  void acquireAndHeldTimers() {
    double acquiredBefore = count("locksmith.acquire?tag=primitive:lock&tag=outcome:acquired");
    double heldBefore = count("locksmith.held?tag=primitive:lock");

    assertThat(http.post("/locks/orders/m1?workMs=1").status()).isEqualTo(200);

    assertThat(count("locksmith.acquire?tag=primitive:lock&tag=outcome:acquired"))
        .isEqualTo(acquiredBefore + 1);
    assertThat(count("locksmith.held?tag=primitive:lock")).isEqualTo(heldBefore + 1);
  }

  @Test
  void aRefusedAcquireIsTaggedSkipped() {
    double skippedBefore = count("locksmith.acquire?tag=primitive:lock&tag=outcome:skipped");
    try (LockHandle held = locks.key("order:m2").acquire()) {
      assertThat(http.post("/locks/orders/m2?workMs=1").status()).isEqualTo(409);
    }
    assertThat(count("locksmith.acquire?tag=primitive:lock&tag=outcome:skipped"))
        .isEqualTo(skippedBefore + 1);
  }

  @Test
  void semaphoreTimers() {
    assertThat(http.post("/semaphores/exports?workMs=1").status()).isEqualTo(200);
    assertThat(count("locksmith.acquire?tag=primitive:semaphore&tag=outcome:acquired"))
        .isPositive();
    assertThat(count("locksmith.held?tag=primitive:semaphore")).isPositive();
  }

  private double count(String meterAndTags) {
    var response = http.get("/actuator/metrics/" + meterAndTags);
    if (response.status() == 404) {
      return 0; // no measurement yet for these tags
    }
    for (JsonNode measurement : response.json().get("measurements")) {
      if (measurement.get("statistic").asString().equals("COUNT")) {
        return measurement.get("value").asDouble();
      }
    }
    throw new IllegalStateException("No COUNT in " + response.body());
  }
}
