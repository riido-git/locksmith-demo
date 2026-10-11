package in.riido.locksmith.demo.keys;

import in.riido.locksmith.demo.keys.KeyService.KeyResult;
import in.riido.locksmith.demo.keys.KeyService.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/keys")
@RequiredArgsConstructor
public class KeyController {

  private final KeyService service;

  @PostMapping("/literal")
  public KeyResult literal(@RequestParam(defaultValue = "100") long workMs) {
    return service.literal(workMs);
  }

  @PostMapping("/users/{userId}")
  public KeyResult parameter(
      @PathVariable String userId, @RequestParam(defaultValue = "100") long workMs) {
    return service.parameter(userId, workMs);
  }

  @PostMapping("/orders")
  public KeyResult property(
      @RequestBody Order order, @RequestParam(defaultValue = "100") long workMs) {
    return service.property(order, workMs);
  }

  @PostMapping("/tenants/{tenant}/regions/{region}")
  public KeyResult position(
      @PathVariable String tenant,
      @PathVariable String region,
      @RequestParam(defaultValue = "100") long workMs) {
    return service.position(tenant, region, workMs);
  }
}
