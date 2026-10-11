package in.riido.locksmith.demo;

import static org.assertj.core.api.Assertions.assertThat;

import in.riido.locksmith.DistributedLock;
import in.riido.locksmith.DistributedSemaphore;
import in.riido.locksmith.LockType;
import in.riido.locksmith.LocksmithConfigurationException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Async;

/**
 * Each misconfiguration stops the application context from starting, with a message that names it.
 */
class StartupValidationTest {

  @Test
  void blankKey() {
    assertStartupFails(
        LocksmithContexts.runner().withBean(BlankKey.class), "BlankKey.run: key must not be blank");
  }

  @Test
  void keyVariableThatIsNotAParameter() {
    assertStartupFails(
        LocksmithContexts.runner().withBean(UnknownVariable.class),
        "uses variable #orderId which is not a parameter of the method; compile with -parameters or use #p0");
  }

  @Test
  void finalMethod() {
    assertStartupFails(
        LocksmithContexts.runner().withBean(FinalMethod.class),
        "FinalMethod.run: the method is final");
  }

  @Test
  void asyncMethodWithAReturnTypeSpringCannotReturn() {
    assertStartupFails(
        LocksmithContexts.runner().withBean(AsyncString.class),
        "@Async methods can return only void, Kotlin Unit, Future or CompletableFuture, got [java.lang.String]");
  }

  @Test
  void oneSemaphoreKeyWithTwoCounts() {
    assertStartupFails(
        LocksmithContexts.runner().withBean(TwoCounts.class),
        "@DistributedSemaphore key [reports] is used with permits");
  }

  @Test
  void reentrantAndReadOnOneKey() {
    assertStartupFails(
        LocksmithContexts.runner().withBean(TwoLockKinds.class),
        "a REENTRANT lock and a READ/WRITE lock of one key are separate locks");
  }

  @Test
  void misspelledEnabledFlag() {
    assertStartupFails(
        LocksmithContexts.runner().withPropertyValues("locksmith.enabled=treu"),
        "locksmith.enabled must be true or false, got [treu]");
  }

  @Test
  void durationTypo() {
    assertStartupFails(
        LocksmithContexts.runner().withBean(DurationTypo.class),
        "waitTime [5x] is not a duration such as 5s or PT5S");
  }

  private static void assertStartupFails(ApplicationContextRunner runner, String message) {
    runner.run(
        context -> {
          assertThat(context).hasFailed();
          assertThat(locksmithException(context.getStartupFailure())).hasMessageContaining(message);
        });
  }

  /**
   * Spring wraps the exception; it may also wrap one of its own, such as a duration parse error.
   */
  private static Throwable locksmithException(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof LocksmithConfigurationException) {
        return cause;
      }
    }
    throw new AssertionError("No LocksmithConfigurationException in " + failure);
  }

  static class BlankKey {

    @DistributedLock(key = " ")
    public void run() {}
  }

  static class UnknownVariable {

    @DistributedLock(key = "order:#{#orderId}")
    public void run(String id) {}
  }

  static class FinalMethod {

    @DistributedLock(key = "final")
    public final void run() {}
  }

  static class AsyncString {

    @Async
    @DistributedLock(key = "async")
    public String run() {
      return "done";
    }
  }

  static class TwoCounts {

    @DistributedSemaphore(key = "reports", permits = "2")
    public void daily() {}

    @DistributedSemaphore(key = "reports", permits = "3")
    public void weekly() {}
  }

  static class TwoLockKinds {

    @DistributedLock(key = "document")
    public void edit() {}

    @DistributedLock(key = "document", type = LockType.READ)
    public void view() {}
  }

  static class DurationTypo {

    @DistributedLock(key = "typo", waitTime = "5x")
    public void run() {}
  }
}
