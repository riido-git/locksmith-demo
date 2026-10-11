package in.riido.locksmith.demo.programmatic;

import in.riido.locksmith.LockType;
import in.riido.locksmith.demo.programmatic.ProgrammaticService.HandleResult;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/programmatic")
@RequiredArgsConstructor
public class ProgrammaticController {

  private final ProgrammaticService service;

  @PostMapping("/reports/{reportId}/rebuild")
  public HandleResult rebuild(
      @PathVariable String reportId, @RequestParam(defaultValue = "1000") long workMs) {
    return service.rebuild(reportId, workMs);
  }

  @PostMapping("/exports")
  public HandleResult export(@RequestParam(defaultValue = "1000") long workMs) {
    return service.export(workMs);
  }

  /** The key without the prefix, for example order:42. */
  @GetMapping("/locks")
  public Map<String, Object> isLocked(
      @RequestParam String key, @RequestParam(defaultValue = "REENTRANT") LockType type) {
    return Map.of("key", key, "type", type, "locked", service.isLocked(key, type));
  }

  @GetMapping("/semaphores")
  public Map<String, Object> availablePermits(@RequestParam String key) {
    return Map.of("key", key, "availablePermits", service.availablePermits(key));
  }
}
