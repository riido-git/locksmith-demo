package in.riido.locksmith.demo.retry;

import static org.assertj.core.api.Assertions.assertThat;

import in.riido.locksmith.demo.FeatureTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

class RetryTest extends FeatureTest {

  @Autowired MeterRegistry meters;

  @Test
  void eachAttemptTakesTheLockAgain() {
    long acquiredBefore = lockAcquisitions();

    JsonNode result = http.post("/retries/rt1?failures=2").json();

    assertThat(result.get("attempts").asInt()).isEqualTo(3);
    assertThat(result.get("lockHeldPerAttempt").values()).allMatch(JsonNode::asBoolean).hasSize(3);
    // One call, three acquisitions: the lock was released and taken again around each attempt.
    assertThat(lockAcquisitions() - acquiredBefore).isEqualTo(3);
  }

  private long lockAcquisitions() {
    var timer =
        meters.find("locksmith.acquire").tags("primitive", "lock", "outcome", "acquired").timer();
    return timer == null ? 0 : timer.count();
  }
}
