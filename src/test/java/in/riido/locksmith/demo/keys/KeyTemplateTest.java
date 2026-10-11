package in.riido.locksmith.demo.keys;

import static org.assertj.core.api.Assertions.assertThat;

import in.riido.locksmith.demo.FeatureTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class KeyTemplateTest extends FeatureTest {

  @Test
  void literalText() {
    assertLocked(http.post("/keys/literal").json(), "demo:lock:report:daily");
  }

  @Test
  void islandOverAParameter() {
    assertLocked(http.post("/keys/users/u7").json(), "demo:lock:user:u7");
  }

  @Test
  void islandOverAPropertyOfAParameter() {
    JsonNode result =
        http.send("POST", "/keys/orders", "{\"id\":\"o-1\",\"customerId\":\"c-7\"}").json();
    assertLocked(result, "demo:lock:customer:c-7");
  }

  @Test
  void parametersByPosition() {
    assertLocked(http.post("/keys/tenants/t1/regions/eu").json(), "demo:lock:tenant:t1:eu");
  }

  private static void assertLocked(JsonNode result, String redisKey) {
    assertThat(result.get("redisKey").asString()).isEqualTo(redisKey);
    assertThat(result.get("lockedWhileRunning").asBoolean()).isTrue();
  }
}
