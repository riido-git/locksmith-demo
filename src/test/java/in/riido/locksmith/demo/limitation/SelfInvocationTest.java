package in.riido.locksmith.demo.limitation;

import static org.assertj.core.api.Assertions.assertThat;

import in.riido.locksmith.demo.FeatureTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** LIMITATION: a call on this is not coordinated. */
class SelfInvocationTest extends FeatureTest {

  @Test
  void aCallOnThisRunsWithoutTheLock() {
    JsonNode lockHeld = http.get("/limitations/self-invocation").json();
    assertThat(lockHeld.get("throughProxy").asBoolean()).isTrue();
    assertThat(lockHeld.get("calledOnThis").asBoolean()).isFalse();
  }
}
