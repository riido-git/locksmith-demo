package in.riido.locksmith.demo.keys;

import in.riido.locksmith.DistributedLock;
import in.riido.locksmith.LockType;
import in.riido.locksmith.autoconfigure.LocksmithProperties;
import in.riido.locksmith.demo.tracker.ConcurrencyTracker;
import in.riido.locksmith.lock.LockOperations;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * One method per kind of key template. Each one reports the key it expects and checks, while it
 * runs, that this key is the one locked in Redis.
 */
@Service
@RequiredArgsConstructor
public class KeyService {

  private final ConcurrencyTracker tracker;
  private final LockOperations locks;
  private final LocksmithProperties locksmith;

  /** Literal text only: every call shares one key. */
  @DistributedLock(key = "report:daily", waitTime = "5s")
  public KeyResult literal(long workMs) {
    return held("report:daily", "report:daily", workMs);
  }

  /** A #{...} island over a parameter, by name. */
  @DistributedLock(key = "user:#{#userId}", waitTime = "5s")
  public KeyResult parameter(String userId, long workMs) {
    return held("user:#{#userId}", "user:" + userId, workMs);
  }

  /** An island over a property of a parameter. */
  @DistributedLock(key = "customer:#{#order.customerId}", waitTime = "5s")
  public KeyResult property(Order order, long workMs) {
    return held("customer:#{#order.customerId}", "customer:" + order.customerId(), workMs);
  }

  /** Parameters by position: #p0 is the first, #a1 the second. */
  @DistributedLock(key = "tenant:#{#p0}:#{#a1}", waitTime = "5s")
  public KeyResult position(String tenant, String region, long workMs) {
    return held("tenant:#{#p0}:#{#a1}", "tenant:" + tenant + ":" + region, workMs);
  }

  private KeyResult held(String template, String key, long workMs) {
    tracker.work("lock:" + key, 1, workMs);
    return new KeyResult(
        template, locksmith.keyPrefix() + "lock:" + key, locks.isLocked(key, LockType.REENTRANT));
  }

  public record Order(String id, String customerId) {}

  public record KeyResult(String template, String redisKey, boolean lockedWhileRunning) {}
}
