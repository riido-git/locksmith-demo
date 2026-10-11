package in.riido.locksmith.demo.tracker;

/** Stands in for the real work a guarded method would do. */
public final class Work {

  private Work() {}

  public static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while working", ex);
    }
  }
}
