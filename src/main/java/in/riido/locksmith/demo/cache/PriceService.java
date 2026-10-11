package in.riido.locksmith.demo.cache;

import in.riido.locksmith.DistributedLock;
import in.riido.locksmith.demo.tracker.ConcurrencyTracker;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PriceService {

  private final ConcurrencyTracker tracker;

  /**
   * Locksmith's advice runs before the cache, so every call takes the lock, a cache hit too. With
   * the default try-once, a cache hit that meets another caller holding the lock throws
   * LockNotAcquiredException.
   */
  @DistributedLock(key = "price:#{#sku}")
  @Cacheable(cacheNames = "prices", key = "#sku")
  public Price price(String sku, long workMs) {
    // Runs on a cache miss only.
    tracker.work("lock:price:" + sku, 1, workMs);
    return new Price(sku, Math.floorMod(sku.hashCode(), 1000), Instant.now());
  }

  public record Price(String sku, int cents, Instant computedAt) {}
}
