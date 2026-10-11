package in.riido.locksmith.demo.cache;

import static org.assertj.core.api.Assertions.assertThat;

import in.riido.locksmith.demo.FeatureTest;
import in.riido.locksmith.lock.LockHandle;
import org.junit.jupiter.api.Test;

class CacheTest extends FeatureTest {

  @Test
  void aCacheHitStillTakesTheLock() {
    String computedAt = http.get("/prices/c1?workMs=1").json().get("computedAt").asString();
    // The second call is served from the cache: same computedAt.
    assertThat(http.get("/prices/c1?workMs=1").json().get("computedAt").asString())
        .isEqualTo(computedAt);

    try (LockHandle held = locks.key("price:c1").acquire()) {
      // Still a cache hit, but Locksmith runs first and the lock is busy.
      assertThat(http.get("/prices/c1?workMs=1").status()).isEqualTo(409);
    }
  }
}
