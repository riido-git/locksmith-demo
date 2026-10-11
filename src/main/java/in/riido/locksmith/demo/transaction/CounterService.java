package in.riido.locksmith.demo.transaction;

import in.riido.locksmith.DistributedLock;
import in.riido.locksmith.LockType;
import in.riido.locksmith.demo.tracker.ConcurrencyTracker;
import in.riido.locksmith.demo.tracker.Work;
import in.riido.locksmith.lock.LockOperations;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class CounterService {

  private final JdbcClient jdbc;
  private final LockOperations locks;
  private final ConcurrencyTracker tracker;

  /**
   * Read, wait, write: safe because the lock lets one caller at a time in, across all instances.
   * Locksmith's advice runs outside @Transactional, so the transaction commits before the lock is
   * released.
   */
  @DistributedLock(key = "counter:#{#name}", waitTime = "10s")
  @Transactional
  public Increment increment(String name, long workMs) {
    // Filled in when the transaction commits, after this method has returned.
    AtomicBoolean lockHeldAtCommit = new AtomicBoolean();
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            lockHeldAtCommit.set(locks.isLocked("counter:" + name, LockType.REENTRANT));
          }
        });
    long value;
    try (var inside = tracker.enter("lock:counter:" + name, 1)) {
      value = readAndWrite(name, workMs);
    }
    return new Increment(name, value, lockHeldAtCommit);
  }

  /** The same read-modify-write without the lock: concurrent calls lose updates. */
  @Transactional
  public Increment incrementUnlocked(String name, long workMs) {
    return new Increment(name, readAndWrite(name, workMs), new AtomicBoolean());
  }

  public long value(String name) {
    return jdbc.sql("select counter_value from counter where name = ?")
        .param(name)
        .query(Long.class)
        .optional()
        .orElse(0L);
  }

  private long readAndWrite(String name, long workMs) {
    long next = value(name) + 1;
    Work.sleep(workMs);
    jdbc.sql("merge into counter (name, counter_value) key (name) values (?, ?)")
        .params(name, next)
        .update();
    return next;
  }

  public record Increment(String name, long value, AtomicBoolean lockHeldAtCommit) {}
}
