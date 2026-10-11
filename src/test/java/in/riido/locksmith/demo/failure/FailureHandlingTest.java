package in.riido.locksmith.demo.failure;

import static org.assertj.core.api.Assertions.assertThat;

import in.riido.locksmith.demo.DemoHttp.Response;
import in.riido.locksmith.demo.FeatureTest;
import in.riido.locksmith.lock.LockHandle;
import in.riido.locksmith.semaphore.PermitHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class FailureHandlingTest extends FeatureTest {

  @Test
  void lockNotAcquiredBecomes409() {
    try (LockHandle held = locks.key("order:f1").acquire()) {
      Response response = http.post("/locks/orders/f1?workMs=1");
      assertThat(response.status()).isEqualTo(409);
      assertThat(response.json().get("title").asString()).isEqualTo("Lock not acquired");
      assertThat(response.json().get("key").asString()).isEqualTo("demo:lock:order:f1");
      assertThat(response.json().get("detail").asString())
          .isEqualTo("Lock [demo:lock:order:f1] not acquired within PT0S");
    }
  }

  @Test
  void semaphoreNotAcquiredBecomes429() {
    List<PermitHandle> taken = takeAll("exports", 3);
    try {
      Response response = http.post("/semaphores/exports?workMs=1");
      assertThat(response.status()).isEqualTo(429);
      assertThat(response.json().get("permits").asInt()).isEqualTo(3);
      assertThat(response.json().get("detail").asString())
          .isEqualTo(
              "Semaphore [demo:semaphore:exports] permit not acquired within PT0S (permits 3)");
    } finally {
      taken.forEach(PermitHandle::close);
    }
  }

  @Test
  void skipReturnsTheDefaultOfTheReturnType() {
    try (LockHandle held = locks.key("order:f2").acquire()) {
      assertThat(http.post("/locks/orders/f2/skip?workMs=1").json().get("ran").asBoolean())
          .isFalse();
    }
    List<PermitHandle> taken = takeAll("exports", 3);
    try {
      assertThat(http.post("/semaphores/exports/skip?workMs=1").json().get("ran").asBoolean())
          .isFalse();
    } finally {
      taken.forEach(PermitHandle::close);
    }
  }

  @Test
  void skipDefaultsForEachReturnType() {
    Map<String, String> returned =
        JsonMapper.builder()
            .build()
            .readValue(
                http.get("/failures/skip-defaults").body(),
                new TypeReference<Map<String, String>>() {});
    assertThat(returned)
        .containsExactly(
            Map.entry("boolean", "false"),
            Map.entry("int", "0"),
            Map.entry("Optional", "Optional.empty"),
            Map.entry("OptionalInt", "OptionalInt.empty"),
            Map.entry("String", "null"),
            Map.entry("List", "null"));
  }

  @Test
  void handlerValueBecomesTheResult() {
    try (LockHandle held = locks.key("order:f3").acquire()) {
      assertThat(http.post("/locks/orders/f3/handler?workMs=1").json().get("status").asString())
          .isEqualTo("busy");
    }
    List<PermitHandle> taken = takeAll("exports", 3);
    try {
      assertThat(http.post("/semaphores/exports/handler?workMs=1").json().get("status").asString())
          .isEqualTo("busy");
    } finally {
      taken.forEach(PermitHandle::close);
    }
  }

  private List<PermitHandle> takeAll(String key, int permits) {
    List<PermitHandle> taken = new ArrayList<>();
    for (int i = 0; i < permits; i++) {
      taken.add(semaphores.key(key).permits(permits).acquire());
    }
    return taken;
  }
}
