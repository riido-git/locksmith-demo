package in.riido.locksmith.demo.repository;

import in.riido.locksmith.demo.repository.ProductService.FragmentPermits;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

  private final ProductService service;

  @GetMapping("/{sku}")
  public ResponseEntity<Product> find(@PathVariable String sku) {
    return ResponseEntity.of(service.find(sku));
  }

  @PutMapping("/{sku}")
  public ResponseEntity<Product> changePrice(@PathVariable String sku, @RequestParam int price) {
    return ResponseEntity.of(service.changePrice(sku, price));
  }

  @GetMapping("/fragment-permits")
  public FragmentPermits fragmentPermits() {
    return service.fragmentPermits();
  }
}
