package in.riido.locksmith.demo.programmatic;

import in.riido.locksmith.LockType;
import in.riido.locksmith.demo.tracker.ConcurrencyTracker;
import in.riido.locksmith.lock.LockHandle;
import in.riido.locksmith.lock.LockOperations;
import in.riido.locksmith.semaphore.PermitHandle;
import in.riido.locksmith.semaphore.SemaphoreOperations;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The same primitives as the annotations, taken in code. acquire() never throws for "not acquired".
 */
@Service
@RequiredArgsConstructor
public class ProgrammaticService {

  private final LockOperations locks;
  private final SemaphoreOperations semaphores;
  private final ConcurrencyTracker tracker;

  /** A WRITE lock per report, waiting up to 5 seconds. */
  public HandleResult rebuild(String reportId, long workMs) {
    try (LockHandle lock =
        locks
            .key("rebuild:" + reportId)
            .type(LockType.WRITE)
            .waitTime(Duration.ofSeconds(5))
            .acquire()) {
      if (lock.acquired()) {
        tracker.work("lock:rebuild:" + reportId, 1, workMs);
      }
      return new HandleResult(lock.acquired(), lock.key(), null);
    }
  }

  /** One of 3 permits, waiting up to 2 seconds. */
  public HandleResult export(long workMs) {
    try (PermitHandle permit =
        semaphores.key("api-exports").permits(3).waitTime(Duration.ofSeconds(2)).acquire()) {
      if (permit.acquired()) {
        tracker.work("semaphore:api-exports", 3, workMs);
      }
      return new HandleResult(permit.acquired(), permit.key(), permit.permitId());
    }
  }

  public boolean isLocked(String key, LockType type) {
    return locks.isLocked(key, type);
  }

  public int availablePermits(String key) {
    return semaphores.availablePermits(key);
  }

  public record HandleResult(boolean acquired, String key, String permitId) {}
}
