package in.riido.locksmith.demo.repository;

import static org.assertj.core.api.Assertions.assertThat;

import in.riido.locksmith.LockType;
import in.riido.locksmith.demo.FeatureTest;
import in.riido.locksmith.lock.LockHandle;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

class RepositoryTest extends FeatureTest {

  @Autowired ProductRepository repository;

  @Test
  void theRepositoryBeanIsAJdkProxy() {
    Object target = AopProxyUtils.getSingletonTarget(repository);
    assertThat(Proxy.isProxyClass(target.getClass())).isTrue();
  }

  @Test
  void theQueryMethodTakesAReadLock() {
    try (LockHandle writing = locks.key("product:p-2").type(LockType.WRITE).acquire()) {
      assertThat(http.get("/products/p-2").status()).isEqualTo(409);
    }
    assertThat(http.get("/products/p-2").status()).isEqualTo(200);
  }

  @Test
  void theRedeclaredSaveTakesAWriteLock() {
    try (LockHandle reading = locks.key("product:p-3").type(LockType.READ).acquire()) {
      // findBySku shares the read lock; save is refused while a reader holds the product.
      assertThat(http.get("/products/p-3").status()).isEqualTo(200);
      assertThat(http.put("/products/p-3?price=901").status()).isEqualTo(409);
    }
    assertThat(http.put("/products/p-3?price=901").json().get("price").asInt()).isEqualTo(901);
  }

  /** LIMITATION: annotated on a fragment interface, one call takes two permits. */
  @Test
  void fragmentPlacements() {
    JsonNode permitsLeft = http.get("/products/fragment-permits").json();
    assertThat(permitsLeft.get("onFragmentInterface").asInt()).isZero();
    assertThat(permitsLeft.get("onRepositoryInterface").asInt()).isEqualTo(1);
    assertThat(permitsLeft.get("onFragmentImpl").asInt()).isEqualTo(1);
  }
}
