package in.riido.locksmith.demo.repository;

import in.riido.locksmith.DistributedSemaphore;

/**
 * A repository fragment. Spring Data hands these calls on to ProductStatsImpl, which is a Spring
 * bean of its own. Each method returns the free permits of its semaphore, seen from inside the
 * call.
 */
public interface ProductStats {

  /**
   * LIMITATION: annotated on the fragment interface, the repository and the fragment bean both
   * carry the annotation, so one call takes two permits.
   */
  @DistributedSemaphore(key = "stats:on-fragment-interface", permits = "2")
  int permitsLeftOnFragmentInterface();

  /** Annotated where ProductRepository redeclares it. */
  int permitsLeftOnRepository();

  /** Annotated on ProductStatsImpl. */
  int permitsLeftOnFragmentImpl();
}
