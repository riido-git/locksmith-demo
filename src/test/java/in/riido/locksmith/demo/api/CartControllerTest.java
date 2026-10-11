package in.riido.locksmith.demo.api;

import static org.assertj.core.api.Assertions.assertThat;

import in.riido.locksmith.LockType;
import in.riido.locksmith.demo.DemoHttp.Response;
import in.riido.locksmith.demo.FeatureTest;
import in.riido.locksmith.demo.SharedRedis;
import in.riido.locksmith.lock.LockHandle;
import in.riido.locksmith.semaphore.PermitHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * The guarded methods are CartController's handler methods. A lock or permit held here, on the test
 * thread, is held by another caller as far as the request threads are concerned.
 */
class CartControllerTest extends FeatureTest {

  @Test
  void readersShareTheCart() {
    try (LockHandle reading = locks.key("cart:c1").type(LockType.READ).acquire()) {
      assertThat(reading.acquired()).isTrue();
      assertThat(http.get("/api/carts/c1?workMs=1").json().get("status").asString())
          .isEqualTo("read");
    }
  }

  @Test
  void getWaitsWhileAWriterHoldsTheCart() throws Exception {
    LockHandle writing = locks.key("cart:c2").type(LockType.WRITE).acquire();
    CompletableFuture<Response> reading =
        CompletableFuture.supplyAsync(() -> http.get("/api/carts/c2?workMs=1"));

    Thread.sleep(300);
    assertThat(reading).isNotDone();
    writing.close();

    assertThat(reading.get(5, TimeUnit.SECONDS).status()).isEqualTo(200);
  }

  @Test
  void putWaitsForTheReader() throws Exception {
    LockHandle reading = locks.key("cart:c3").type(LockType.READ).acquire();
    CompletableFuture<Response> writing =
        CompletableFuture.supplyAsync(() -> http.put("/api/carts/c3?workMs=1"));

    Thread.sleep(300);
    assertThat(writing).isNotDone();
    reading.close();

    assertThat(writing.get(5, TimeUnit.SECONDS).json().get("status").asString())
        .isEqualTo("replaced");
  }

  @Test
  void patchFailsWhileTheCartIsBusy() {
    try (LockHandle writing = locks.key("cart:c4").type(LockType.WRITE).acquire()) {
      assertThat(writing.acquired()).isTrue();
      assertThat(http.send("PATCH", "/api/carts/c4?workMs=1", null).status()).isEqualTo(409);
    }
    assertThat(http.send("PATCH", "/api/carts/c4?workMs=1", null).status()).isEqualTo(200);
  }

  @Test
  void busyDeleteGetsTheHandlersResponse() {
    try (LockHandle writing = locks.key("cart:c5").type(LockType.WRITE).acquire()) {
      Response busy = http.delete("/api/carts/c5?workMs=1");
      assertThat(busy.status()).isEqualTo(409);
      assertThat(busy.headers().firstValue("Retry-After")).hasValue("1");
      assertThat(busy.body()).isEqualTo("{\"cartId\":\"c5\",\"status\":\"busy\"}");
    }
    Response deleted = http.delete("/api/carts/c5?workMs=1");
    assertThat(deleted.status()).isEqualTo(200);
    assertThat(deleted.json().get("status").asString()).isEqualTo("deleted");
  }

  @Test
  void checkoutTakesItsKeyFromTheBody() {
    String body = "{\"cartId\":\"c6\"}";
    try (LockHandle checkingOut = locks.key("checkout:c6").acquire()) {
      assertThat(SharedRedis.redisCli("EXISTS", "demo:lock:checkout:c6")).isEqualTo("1");
      Response doubleClick = http.send("POST", "/api/carts/checkout?workMs=1", body);
      assertThat(doubleClick.status()).isEqualTo(409);
      assertThat(doubleClick.json().get("key").asString()).isEqualTo("demo:lock:checkout:c6");
    }
    assertThat(
            http.send("POST", "/api/carts/checkout?workMs=1", body).json().get("status").asString())
        .isEqualTo("checked out");
  }

  @Test
  void invoiceGets429WhileBothPermitsAreTaken() {
    List<PermitHandle> taken = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      PermitHandle permit = semaphores.key("invoices").permits(2).acquire();
      assertThat(permit.acquired()).isTrue();
      taken.add(permit);
    }
    try {
      assertThat(http.get("/api/carts/c7/invoice?workMs=1").status()).isEqualTo(429);
      taken.removeFirst().close();
      assertThat(http.get("/api/carts/c7/invoice?workMs=1").status()).isEqualTo(200);
    } finally {
      taken.forEach(PermitHandle::close);
    }
  }
}
