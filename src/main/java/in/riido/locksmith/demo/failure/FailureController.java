package in.riido.locksmith.demo.failure;

import in.riido.locksmith.semaphore.PermitHandle;
import in.riido.locksmith.semaphore.SemaphoreNotAcquiredException;
import in.riido.locksmith.semaphore.SemaphoreOperations;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/failures")
@RequiredArgsConstructor
public class FailureController {

  private final SkipDefaultsService service;
  private final SemaphoreOperations semaphores;

  /**
   * Holds the only permit, so every SKIP method below is skipped and returns its type's default.
   */
  @GetMapping("/skip-defaults")
  public Map<String, String> skipDefaults() {
    try (PermitHandle permit = semaphores.key(SkipDefaultsService.KEY).permits(1).acquire()) {
      if (!permit.acquired()) {
        throw new SemaphoreNotAcquiredException(permit.key(), 1, Duration.ZERO);
      }
      Map<String, String> returned = new LinkedHashMap<>();
      returned.put("boolean", String.valueOf(service.asBoolean()));
      returned.put("int", String.valueOf(service.asInt()));
      returned.put("Optional", String.valueOf(service.asOptional()));
      returned.put("OptionalInt", String.valueOf(service.asOptionalInt()));
      returned.put("String", String.valueOf(service.asString()));
      returned.put("List", String.valueOf(service.asList()));
      return returned;
    }
  }
}
