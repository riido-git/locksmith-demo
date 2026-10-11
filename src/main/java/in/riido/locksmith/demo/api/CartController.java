package in.riido.locksmith.demo.api;

import in.riido.locksmith.DistributedLock;
import in.riido.locksmith.DistributedSemaphore;
import in.riido.locksmith.LockType;
import in.riido.locksmith.OnFailure;
import in.riido.locksmith.demo.tracker.ConcurrencyTracker;
import in.riido.locksmith.demo.tracker.Work;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Each handler method carries its own Locksmith annotation; there is no service in between. Spring
 * MVC calls the handler through the controller's proxy, so the lock or permit is held while the
 * handler runs.
 */
@RestController
@RequestMapping("/api/carts")
@RequiredArgsConstructor
public class CartController {

  private final ConcurrencyTracker tracker;

  /** READ: any number of readers at once, none while a writer holds the cart. */
  @GetMapping("/{cartId}")
  @DistributedLock(key = "cart:#{#cartId}", type = LockType.READ, waitTime = "5s")
  public CartResult read(
      @PathVariable String cartId, @RequestParam(defaultValue = "1000") long workMs) {
    try (var inside = tracker.enterReader("rw:cart:" + cartId)) {
      Work.sleep(workMs);
    }
    return new CartResult(cartId, "read");
  }

  /** WRITE: one writer at a time, and no readers meanwhile; waits up to 5 seconds for its turn. */
  @PutMapping("/{cartId}")
  @DistributedLock(key = "cart:#{#cartId}", type = LockType.WRITE, waitTime = "5s")
  public CartResult replace(
      @PathVariable String cartId, @RequestParam(defaultValue = "1000") long workMs) {
    try (var inside = tracker.enterWriter("rw:cart:" + cartId)) {
      Work.sleep(workMs);
    }
    return new CartResult(cartId, "replaced");
  }

  /** WRITE, try once: a busy cart throws LockNotAcquiredException, and the client gets 409. */
  @PatchMapping("/{cartId}")
  @DistributedLock(key = "cart:#{#cartId}", type = LockType.WRITE)
  public CartResult update(
      @PathVariable String cartId, @RequestParam(defaultValue = "1000") long workMs) {
    try (var inside = tracker.enterWriter("rw:cart:" + cartId)) {
      Work.sleep(workMs);
    }
    return new CartResult(cartId, "updated");
  }

  /** HANDLER: a busy cart skips the method and returns the response BusyCartHandler builds. */
  @DeleteMapping("/{cartId}")
  @DistributedLock(
      key = "cart:#{#cartId}",
      type = LockType.WRITE,
      onFailure = OnFailure.HANDLER,
      handler = BusyCartHandler.class)
  public ResponseEntity<CartResult> delete(
      @PathVariable String cartId, @RequestParam(defaultValue = "1000") long workMs) {
    try (var inside = tracker.enterWriter("rw:cart:" + cartId)) {
      Work.sleep(workMs);
    }
    return ResponseEntity.ok(new CartResult(cartId, "deleted"));
  }

  /**
   * The key comes from the JSON body. Try once: a second checkout of the cart while the first runs
   * gets 409.
   */
  @PostMapping("/checkout")
  @DistributedLock(key = "checkout:#{#request.cartId}")
  public CartResult checkout(
      @RequestBody CheckoutRequest request, @RequestParam(defaultValue = "1000") long workMs) {
    tracker.work("lock:checkout:" + request.cartId(), 1, workMs);
    return new CartResult(request.cartId(), "checked out");
  }

  /**
   * At most 2 invoices at once across all instances. Try once: when both are taken, the client gets
   * 429.
   */
  @GetMapping("/{cartId}/invoice")
  @DistributedSemaphore(key = "invoices", permits = "2")
  public CartResult invoice(
      @PathVariable String cartId, @RequestParam(defaultValue = "1000") long workMs) {
    tracker.work("semaphore:invoices", 2, workMs);
    return new CartResult(cartId, "invoiced");
  }

  public record CheckoutRequest(String cartId) {}

  public record CartResult(String cartId, String status) {}
}
