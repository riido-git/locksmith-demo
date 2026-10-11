package in.riido.locksmith.demo.repository;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductService {

  private final ProductRepository repository;

  public Optional<Product> find(String sku) {
    return repository.findBySku(sku);
  }

  /** findBySku takes the READ lock and releases it; save then takes the WRITE lock. */
  public Optional<Product> changePrice(String sku, int price) {
    return repository
        .findBySku(sku)
        .map(
            product ->
                repository.save(new Product(product.id(), product.sku(), product.name(), price)));
  }

  /**
   * Free permits seen from inside each call: 0 means the call holds both permits of its semaphore.
   */
  public FragmentPermits fragmentPermits() {
    return new FragmentPermits(
        repository.permitsLeftOnFragmentInterface(),
        repository.permitsLeftOnRepository(),
        repository.permitsLeftOnFragmentImpl());
  }

  public record FragmentPermits(
      int onFragmentInterface, int onRepositoryInterface, int onFragmentImpl) {}
}
