package in.riido.locksmith.demo.transaction;

import in.riido.locksmith.demo.transaction.CounterService.Increment;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/counters")
@RequiredArgsConstructor
public class CounterController {

  private final CounterService service;

  @PostMapping("/{name}/increment")
  public Increment increment(
      @PathVariable String name, @RequestParam(defaultValue = "100") long workMs) {
    return service.increment(name, workMs);
  }

  @PostMapping("/{name}/increment-unlocked")
  public Increment incrementUnlocked(
      @PathVariable String name, @RequestParam(defaultValue = "100") long workMs) {
    return service.incrementUnlocked(name, workMs);
  }

  @GetMapping("/{name}")
  public Map<String, Object> value(@PathVariable String name) {
    return Map.of("name", name, "value", service.value(name));
  }
}
