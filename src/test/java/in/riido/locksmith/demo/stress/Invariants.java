package in.riido.locksmith.demo.stress;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The rules both stress suites check, the JUnit one and the Gatling one, on what the app's own
 * endpoints report. Each method returns the rules that broke, in plain words; an empty list means
 * every rule held.
 */
public final class Invariants {

  private Invariants() {}

  /**
   * Rules on the {@code /tracker} snapshot. A lock never had two holders, and a semaphore never
   * more holders than permits. A semaphore under load had all its permits in use at some moment, so
   * it is not over-serialised. A writer never shared a read/write lock with a reader or another
   * writer, and readers did share it. Nobody is left inside once the load is over.
   */
  public static List<String> tracker(Map<String, ?> sections) {
    List<String> broken = new ArrayList<>();
    sections.forEach(
        (name, value) -> {
          Map<?, ?> counters = (Map<?, ?>) value;
          long limit = number(counters, "limit");
          long holderMax = number(counters, "holderMax");
          if (number(counters, "holderEntries") > 0) {
            if (holderMax > limit) {
              broken.add(name + " had " + holderMax + " holders at once; its limit is " + limit);
            }
            if (limit > 1 && holderMax < limit) {
              broken.add(
                  name + " never had all " + limit + " permits in use (at most " + holderMax + ")");
            }
          }
          long readers = number(counters, "readerEntries");
          long writers = number(counters, "writerEntries");
          if (readers > 0 || writers > 0) {
            if (number(counters, "overlaps") > 0) {
              broken.add(
                  name
                      + ": a reader and a writer were inside together "
                      + number(counters, "overlaps")
                      + " times");
            }
            if (number(counters, "writerMax") > 1) {
              broken.add(name + " had " + number(counters, "writerMax") + " writers at once");
            }
            if (readers == 0 || writers == 0) {
              broken.add(
                  name
                      + " saw "
                      + readers
                      + " reads and "
                      + writers
                      + " writes; the test needs both");
            }
            if (number(counters, "readerMax") < 2) {
              broken.add(name + ": readers never shared the lock");
            }
          }
          for (String role : List.of("holder", "reader", "writer")) {
            if (number(counters, role) != 0) {
              broken.add(name + " still has " + number(counters, role) + " " + role + "(s) inside");
            }
          }
        });
    return broken;
  }

  /**
   * Each guarded body ran exactly as often as clients were told it ran: a THROW, SKIP or HANDLER
   * outcome never ran the body, and every success did.
   */
  public static List<String> runs(Map<String, ?> sections, Map<String, Long> runsSeenByClients) {
    List<String> broken = new ArrayList<>();
    runsSeenByClients.forEach(
        (name, seen) -> {
          Map<?, ?> counters = (Map<?, ?>) sections.get(name);
          long counted =
              counters == null
                  ? 0
                  : number(counters, "holderEntries")
                      + number(counters, "readerEntries")
                      + number(counters, "writerEntries");
          if (counted != seen) {
            broken.add(name + ": clients saw " + seen + " runs, the app counted " + counted);
          }
        });
    return broken;
  }

  /** A released lock leaves no key in Redis. A semaphore keeps only its count key, by design. */
  public static List<String> leftoverKeys(Collection<String> keys, String keyPrefix) {
    return keys.stream()
        .filter(key -> !(key.startsWith(keyPrefix + "semaphore:") && !key.contains("}")))
        .map(key -> "key left in Redis: " + key)
        .toList();
  }

  /**
   * Every lock or permit an instance acquired was released once: as many locksmith.held records as
   * acquires.
   */
  public static List<String> releases(
      String instance, String primitive, double acquired, double held) {
    return acquired == held
        ? List.of()
        : List.of(
            instance
                + " acquired "
                + (long) acquired
                + " "
                + primitive
                + "s but recorded "
                + (long) held
                + " releases");
  }

  private static long number(Map<?, ?> counters, String field) {
    Object value = counters.get(field);
    return value == null ? 0 : ((Number) value).longValue();
  }
}
