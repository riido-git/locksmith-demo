package in.riido.locksmith.demo.repository;

import in.riido.locksmith.DistributedLock;
import in.riido.locksmith.DistributedSemaphore;
import in.riido.locksmith.LockType;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

/**
 * A Spring Data repository is a JDK proxy of this interface, so the annotations sit on interface
 * methods. Both locks try once: a reader is refused while a writer holds the product, and the other
 * way round.
 */
public interface ProductRepository extends ListCrudRepository<Product, Long>, ProductStats {

  /** A query method with a shared READ lock per product. */
  @DistributedLock(key = "product:#{#sku}", type = LockType.READ)
  Optional<Product> findBySku(String sku);

  /** A CRUD method, redeclared here to carry an exclusive WRITE lock on the same product. */
  @Override
  @DistributedLock(key = "product:#{#p0.sku}", type = LockType.WRITE)
  <S extends Product> S save(S product);

  /**
   * Recommended placement for a fragment method: on the repository interface. One permit per call.
   */
  @Override
  @DistributedSemaphore(key = "stats:on-repository", permits = "2")
  int permitsLeftOnRepository();
}
