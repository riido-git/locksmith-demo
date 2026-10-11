package in.riido.locksmith.demo.cache;

import in.riido.locksmith.demo.cache.PriceService.Price;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/prices")
@RequiredArgsConstructor
public class PriceController {

  private final PriceService service;

  @GetMapping("/{sku}")
  public Price price(@PathVariable String sku, @RequestParam(defaultValue = "1000") long workMs) {
    return service.price(sku, workMs);
  }
}
