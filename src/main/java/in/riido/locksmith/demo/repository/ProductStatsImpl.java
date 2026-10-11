package in.riido.locksmith.demo.repository;

import in.riido.locksmith.DistributedSemaphore;
import in.riido.locksmith.semaphore.SemaphoreOperations;
import lombok.RequiredArgsConstructor;

/** Found by Spring Data through the Impl suffix. */
@RequiredArgsConstructor
public class ProductStatsImpl implements ProductStats {

  private final SemaphoreOperations semaphores;

  @Override
  public int permitsLeftOnFragmentInterface() {
    return semaphores.availablePermits("stats:on-fragment-interface");
  }

  @Override
  public int permitsLeftOnRepository() {
    return semaphores.availablePermits("stats:on-repository");
  }

  /**
   * Recommended placement for a fragment method: on the implementation class. One permit per call.
   */
  @Override
  @DistributedSemaphore(key = "stats:on-fragment-impl", permits = "2")
  public int permitsLeftOnFragmentImpl() {
    return semaphores.availablePermits("stats:on-fragment-impl");
  }
}
