package in.riido.locksmith.demo.async;

import in.riido.locksmith.DistributedLock;
import in.riido.locksmith.LockType;
import in.riido.locksmith.demo.tracker.ConcurrencyTracker;
import in.riido.locksmith.lock.LockOperations;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Spring's @Async advice runs before Locksmith's, so the lock is taken on the worker thread and
 * covers the whole method body there. Locksmith accepts only Future and CompletableFuture as @Async
 * return types besides void.
 */
@Service
@RequiredArgsConstructor
public class AsyncService {

  private final LockOperations locks;
  private final ConcurrencyTracker tracker;

  @Async
  @DistributedLock(key = "job:#{#jobId}", waitTime = "5s")
  public CompletableFuture<JobResult> run(String jobId, long workMs) {
    return CompletableFuture.completedFuture(job(jobId, workMs));
  }

  @Async
  @DistributedLock(key = "job:#{#jobId}", waitTime = "5s")
  public Future<JobResult> runAsFuture(String jobId, long workMs) {
    return CompletableFuture.completedFuture(job(jobId, workMs));
  }

  private JobResult job(String jobId, long workMs) {
    boolean lockedAtStart = locks.isLocked("job:" + jobId, LockType.REENTRANT);
    tracker.work("lock:job:" + jobId, 1, workMs);
    boolean lockedAtEnd = locks.isLocked("job:" + jobId, LockType.REENTRANT);
    return new JobResult(jobId, Thread.currentThread().getName(), lockedAtStart, lockedAtEnd);
  }

  public record JobResult(
      String jobId, String thread, boolean lockedAtStart, boolean lockedAtEnd) {}
}
