package in.riido.locksmith.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.riido.locksmith.DistributedLock;
import in.riido.locksmith.DistributedSemaphore;
import in.riido.locksmith.demo.tracker.Work;
import in.riido.locksmith.lock.LockHandle;
import in.riido.locksmith.lock.LockNotAcquiredException;
import in.riido.locksmith.lock.LockOperations;
import in.riido.locksmith.semaphore.SemaphoreOperations;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;

/** locksmith.enabled, locksmith.key-prefix and locksmith.semaphore.lease-time. */
@ExtendWith(OutputCaptureExtension.class)
class PropertiesTest {

  @Test
  void enabledTrueLetsOneCallerInAtATime() {
    LocksmithContexts.runner()
        .withBean(Meeting.class)
        .run(
            context -> {
              Meeting meeting = context.getBean(Meeting.class);
              CompletableFuture<Boolean> first = CompletableFuture.supplyAsync(meeting::meet);
              CompletableFuture<Boolean> second = CompletableFuture.supplyAsync(meeting::meet);

              // One call gets the lock and waits in vain for the other, which is refused at once.
              assertThat(List.of(outcome(first), outcome(second)))
                  .containsExactlyInAnyOrder("alone", "refused");
            });
  }

  @Test
  void enabledFalseRunsAnnotatedMethodsWithoutCoordination(CapturedOutput output) {
    LocksmithContexts.runner()
        .withPropertyValues("locksmith.enabled=false")
        .withBean(Meeting.class)
        .run(
            context -> {
              Meeting meeting = context.getBean(Meeting.class);
              CompletableFuture<Boolean> first = CompletableFuture.supplyAsync(meeting::meet);
              CompletableFuture<Boolean> second = CompletableFuture.supplyAsync(meeting::meet);

              // Both calls are inside the annotated method at the same time.
              assertThat(List.of(outcome(first), outcome(second))).containsExactly("met", "met");
              assertThat(context).doesNotHaveBean(LockOperations.class);
            });
    assertThat(output)
        .contains("Locksmith is disabled: annotated methods run without any coordination");
  }

  /**
   * This demo injects LockOperations and SemaphoreOperations, so it cannot start with Locksmith
   * switched off.
   */
  @Test
  void theDemoAppNeedsLocksmithEnabled() {
    assertThatThrownBy(
            () ->
                new SpringApplicationBuilder(DemoApplication.class)
                    .run(
                        "--locksmith.enabled=false",
                        "--server.port=0",
                        "--spring.docker.compose.enabled=false",
                        "--spring.data.redis.host=" + SharedRedis.REDIS.getHost(),
                        "--spring.data.redis.port=" + SharedRedis.REDIS.getMappedPort(6379)))
        .rootCause()
        .isInstanceOf(NoSuchBeanDefinitionException.class)
        .hasMessageContaining("in.riido.locksmith");
  }

  @Test
  void keyPrefix() {
    LocksmithContexts.runner()
        .run(context -> assertThat(lockKey(context)).isEqualTo("locksmith:lock:report"));
    LocksmithContexts.runner()
        .withPropertyValues("locksmith.key-prefix=shop:")
        .run(context -> assertThat(lockKey(context)).isEqualTo("shop:lock:report"));
  }

  @Test
  void semaphoreLeaseTimeIsTheDefaultLeaseOfAPermit() {
    LocksmithContexts.runner()
        .withPropertyValues("locksmith.semaphore.lease-time=1s")
        .withBean(SlowReport.class)
        .run(
            context -> {
              // No leaseTime on the annotation: the permit gets the 1s default and expires while
              // the method runs.
              assertThat(context.getBean(SlowReport.class).permitsFreeAfterWorking(2000))
                  .isEqualTo(1);
            });
  }

  private static String outcome(CompletableFuture<Boolean> call) {
    try {
      return call.join() ? "met" : "alone";
    } catch (CompletionException ex) {
      return ex.getCause() instanceof LockNotAcquiredException ? "refused" : ex.toString();
    }
  }

  private static String lockKey(ApplicationContext context) {
    try (LockHandle lock = context.getBean(LockOperations.class).key("report").acquire()) {
      return lock.key();
    }
  }

  static class Meeting {

    private final CountDownLatch bothInside = new CountDownLatch(2);

    /** Returns true when another caller was inside this method at the same time. */
    @DistributedLock(key = "meeting")
    public boolean meet() {
      bothInside.countDown();
      try {
        return bothInside.await(2, TimeUnit.SECONDS);
      } catch (InterruptedException ex) {
        throw new CompletionException(ex);
      }
    }
  }

  static class SlowReport {

    private final SemaphoreOperations semaphores;

    SlowReport(SemaphoreOperations semaphores) {
      this.semaphores = semaphores;
    }

    @DistributedSemaphore(key = "slow-report", permits = "1")
    public int permitsFreeAfterWorking(long workMs) {
      Work.sleep(workMs);
      return semaphores.availablePermits("slow-report");
    }
  }
}
