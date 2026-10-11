package in.riido.locksmith.demo.lock;

import in.riido.locksmith.demo.lock.LockService.DocumentResult;
import in.riido.locksmith.demo.lock.LockService.LeaseResult;
import in.riido.locksmith.demo.lock.LockService.OrderResult;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/locks")
@RequiredArgsConstructor
public class LockController {

  private final LockService service;

  @PostMapping("/orders/{orderId}")
  public OrderResult process(
      @PathVariable String orderId, @RequestParam(defaultValue = "1000") long workMs) {
    return service.process(orderId, workMs);
  }

  @PostMapping("/orders/{orderId}/wait")
  public OrderResult processWaiting(
      @PathVariable String orderId, @RequestParam(defaultValue = "1000") long workMs) {
    return service.processWaiting(orderId, workMs);
  }

  @PostMapping("/orders/{orderId}/skip")
  public Map<String, Boolean> processOrSkip(
      @PathVariable String orderId, @RequestParam(defaultValue = "1000") long workMs) {
    return Map.of("ran", service.processOrSkip(orderId, workMs));
  }

  @PostMapping("/orders/{orderId}/handler")
  public OrderResult processOrHandle(
      @PathVariable String orderId, @RequestParam(defaultValue = "1000") long workMs) {
    return service.processOrHandle(orderId, workMs);
  }

  @GetMapping("/documents/{id}")
  public DocumentResult read(
      @PathVariable String id, @RequestParam(defaultValue = "1000") long workMs) {
    return service.read(id, workMs);
  }

  @PutMapping("/documents/{id}")
  public DocumentResult write(
      @PathVariable String id, @RequestParam(defaultValue = "1000") long workMs) {
    return service.write(id, workMs);
  }

  @PostMapping("/lease/fixed")
  public LeaseResult fixedLease(@RequestParam(defaultValue = "3000") long workMs) {
    return service.fixedLease(workMs);
  }

  @PostMapping("/lease/renewed")
  public LeaseResult renewedLease(@RequestParam(defaultValue = "7000") long workMs) {
    return service.renewedLease(workMs);
  }
}
