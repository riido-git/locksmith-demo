package in.riido.locksmith.demo.semaphore;

import in.riido.locksmith.DistributedLock;
import in.riido.locksmith.DistributedSemaphore;
import in.riido.locksmith.OnFailure;
import in.riido.locksmith.demo.tracker.ConcurrencyTracker;
import in.riido.locksmith.demo.tracker.Work;
import in.riido.locksmith.semaphore.SemaphoreOperations;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SemaphoreService {

  private final ConcurrencyTracker tracker;
  private final SemaphoreOperations semaphores;
  private final int reportsMaxConcurrent;

  public SemaphoreService(
      ConcurrencyTracker tracker,
      SemaphoreOperations semaphores,
      @Value("${demo.reports.max-concurrent}") int reportsMaxConcurrent) {
    this.tracker = tracker;
    this.semaphores = semaphores;
    this.reportsMaxConcurrent = reportsMaxConcurrent;
  }

  /**
   * At most 3 exports at once across all instances. Try once: no free permit throws
   * SemaphoreNotAcquiredException.
   */
  @DistributedSemaphore(key = "exports", permits = "3")
  public ExportResult export(long workMs) {
    tracker.work("semaphore:exports", 3, workMs);
    return new ExportResult("done");
  }

  /** Waits up to 10 seconds for a free permit. */
  @DistributedSemaphore(key = "exports", permits = "3", waitTime = "10s")
  public ExportResult exportWaiting(long workMs) {
    tracker.work("semaphore:exports", 3, workMs);
    return new ExportResult("done");
  }

  /** SKIP: no free permit skips the method, which then returns Optional.empty(). */
  @DistributedSemaphore(key = "exports", permits = "3", onFailure = OnFailure.SKIP)
  public Optional<ExportResult> exportOrSkip(long workMs) {
    tracker.work("semaphore:exports", 3, workMs);
    return Optional.of(new ExportResult("done"));
  }

  /** HANDLER: no free permit skips the method and returns what BusyExportHandler returns. */
  @DistributedSemaphore(
      key = "exports",
      permits = "3",
      onFailure = OnFailure.HANDLER,
      handler = BusyExportHandler.class)
  public ExportResult exportOrHandle(long workMs) {
    tracker.work("semaphore:exports", 3, workMs);
    return new ExportResult("done");
  }

  /** The permit count comes from the property demo.reports.max-concurrent. */
  @DistributedSemaphore(
      key = "reports",
      permits = "${demo.reports.max-concurrent}",
      waitTime = "10s")
  public ExportResult report(long workMs) {
    tracker.work("semaphore:reports", reportsMaxConcurrent, workMs);
    return new ExportResult("done");
  }

  /**
   * A permit with a fixed 1-second lease: it expires after 1 second even if the method still runs.
   */
  @DistributedSemaphore(key = "lease-demo:permit", permits = "1", leaseTime = "1s")
  public PermitLeaseResult leaseDemo(long workMs) {
    tracker.work("semaphore:lease-demo:permit", 1, workMs);
    return new PermitLeaseResult("1s", workMs, semaphores.availablePermits("lease-demo:permit"));
  }

  /**
   * A permit, then a lock: at most 2 imports at once, and one per tenant. Locksmith takes the
   * permit first and releases it last, so a caller never holds the tenant's lock while it queues
   * for a permit.
   */
  @DistributedSemaphore(key = "imports", permits = "2", waitTime = "10s")
  @DistributedLock(key = "import:#{#tenant}")
  public ImportResult runImport(String tenant, long workMs) {
    try (var permit = tracker.enter("semaphore:imports", 2);
        var lock = tracker.enter("lock:import:" + tenant, 1)) {
      Work.sleep(workMs);
    }
    return new ImportResult(tenant, "imported");
  }

  public record ExportResult(String status) {}

  public record PermitLeaseResult(String lease, long workMs, int availablePermitsAtEnd) {}

  public record ImportResult(String tenant, String status) {}
}
