package in.riido.locksmith.demo.lock;

import in.riido.locksmith.DistributedLock;
import in.riido.locksmith.LockType;
import in.riido.locksmith.OnFailure;
import in.riido.locksmith.demo.tracker.ConcurrencyTracker;
import in.riido.locksmith.demo.tracker.Work;
import in.riido.locksmith.lock.LockOperations;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LockService {

  private final ConcurrencyTracker tracker;
  private final LockOperations locks;

  /** REENTRANT (the default) and try once: a busy order throws LockNotAcquiredException. */
  @DistributedLock(key = "order:#{#orderId}")
  public OrderResult process(String orderId, long workMs) {
    tracker.work("lock:order:" + orderId, 1, workMs);
    return new OrderResult(orderId, "done");
  }

  /** Waits up to 5 seconds for a busy order instead of failing at once. */
  @DistributedLock(key = "order:#{#orderId}", waitTime = "5s")
  public OrderResult processWaiting(String orderId, long workMs) {
    tracker.work("lock:order:" + orderId, 1, workMs);
    return new OrderResult(orderId, "done");
  }

  /** SKIP: a busy order skips the method, which then returns false, the default of boolean. */
  @DistributedLock(key = "order:#{#orderId}", onFailure = OnFailure.SKIP)
  public boolean processOrSkip(String orderId, long workMs) {
    tracker.work("lock:order:" + orderId, 1, workMs);
    return true;
  }

  /** HANDLER: a busy order skips the method and returns what BusyOrderHandler returns. */
  @DistributedLock(
      key = "order:#{#orderId}",
      onFailure = OnFailure.HANDLER,
      handler = BusyOrderHandler.class)
  public OrderResult processOrHandle(String orderId, long workMs) {
    tracker.work("lock:order:" + orderId, 1, workMs);
    return new OrderResult(orderId, "done");
  }

  /** READ: any number of readers at once, none while a writer holds the document. */
  @DistributedLock(key = "document:#{#id}", type = LockType.READ, waitTime = "5s")
  public DocumentResult read(String id, long workMs) {
    try (var inside = tracker.enterReader("rw:document:" + id)) {
      Work.sleep(workMs);
    }
    return new DocumentResult(id, "read");
  }

  /** WRITE: one writer at a time, and no readers meanwhile. */
  @DistributedLock(key = "document:#{#id}", type = LockType.WRITE, waitTime = "5s")
  public DocumentResult write(String id, long workMs) {
    try (var inside = tracker.enterWriter("rw:document:" + id)) {
      Work.sleep(workMs);
    }
    return new DocumentResult(id, "written");
  }

  /**
   * A fixed lease: Redis drops the lock 1 second after it was taken, even if the method still runs.
   */
  @DistributedLock(key = "lease-demo:fixed", leaseTime = "1s")
  public LeaseResult fixedLease(long workMs) {
    tracker.work("lock:lease-demo:fixed", 1, workMs);
    return new LeaseResult("1s", workMs, locks.isLocked("lease-demo:fixed", LockType.REENTRANT));
  }

  /** No leaseTime: Redisson renews the lock for as long as the method runs. */
  @DistributedLock(key = "lease-demo:renewed")
  public LeaseResult renewedLease(long workMs) {
    tracker.work("lock:lease-demo:renewed", 1, workMs);
    return new LeaseResult(
        "renewed", workMs, locks.isLocked("lease-demo:renewed", LockType.REENTRANT));
  }

  public record OrderResult(String orderId, String status) {}

  public record DocumentResult(String id, String access) {}

  public record LeaseResult(String lease, long workMs, boolean stillLockedAtEnd) {}
}
