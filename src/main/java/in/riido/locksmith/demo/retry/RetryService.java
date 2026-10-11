package in.riido.locksmith.demo.retry;

import in.riido.locksmith.DistributedLock;
import in.riido.locksmith.LockType;
import in.riido.locksmith.demo.tracker.ConcurrencyTracker;
import in.riido.locksmith.lock.LockOperations;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RetryService {

  private final LockOperations locks;
  private final ConcurrencyTracker tracker;

  /**
   * Spring's @Retryable runs outside Locksmith, so each attempt takes the lock again and the lock
   * is free between attempts. The first {@code failures} attempts throw. The caller passes an empty
   * list, and each attempt adds whether it held the lock.
   */
  @Retryable(includes = IllegalStateException.class, maxRetries = 3, delay = 100)
  @DistributedLock(key = "retry:#{#callId}")
  public RetryResult call(String callId, int failures, List<Boolean> lockHeldPerAttempt) {
    tracker.work("lock:retry:" + callId, 1, 10);
    lockHeldPerAttempt.add(locks.isLocked("retry:" + callId, LockType.REENTRANT));
    if (lockHeldPerAttempt.size() <= failures) {
      throw new IllegalStateException("Attempt " + lockHeldPerAttempt.size() + " failed");
    }
    return new RetryResult(callId, lockHeldPerAttempt.size(), lockHeldPerAttempt);
  }

  public record RetryResult(String callId, int attempts, List<Boolean> lockHeldPerAttempt) {}
}
