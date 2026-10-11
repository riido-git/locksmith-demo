package in.riido.locksmith.demo.semaphore;

import in.riido.locksmith.demo.semaphore.SemaphoreService.ExportResult;
import in.riido.locksmith.demo.semaphore.SemaphoreService.ImportResult;
import in.riido.locksmith.demo.semaphore.SemaphoreService.PermitLeaseResult;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/semaphores")
@RequiredArgsConstructor
public class SemaphoreController {

  private final SemaphoreService service;

  @PostMapping("/exports")
  public ExportResult export(@RequestParam(defaultValue = "1000") long workMs) {
    return service.export(workMs);
  }

  @PostMapping("/exports/wait")
  public ExportResult exportWaiting(@RequestParam(defaultValue = "1000") long workMs) {
    return service.exportWaiting(workMs);
  }

  @PostMapping("/exports/skip")
  public Map<String, Boolean> exportOrSkip(@RequestParam(defaultValue = "1000") long workMs) {
    return Map.of("ran", service.exportOrSkip(workMs).isPresent());
  }

  @PostMapping("/exports/handler")
  public ExportResult exportOrHandle(@RequestParam(defaultValue = "1000") long workMs) {
    return service.exportOrHandle(workMs);
  }

  @PostMapping("/reports")
  public ExportResult report(@RequestParam(defaultValue = "1000") long workMs) {
    return service.report(workMs);
  }

  @PostMapping("/lease")
  public PermitLeaseResult leaseDemo(@RequestParam(defaultValue = "2000") long workMs) {
    return service.leaseDemo(workMs);
  }

  @PostMapping("/imports/{tenant}")
  public ImportResult runImport(
      @PathVariable String tenant, @RequestParam(defaultValue = "1000") long workMs) {
    return service.runImport(tenant, workMs);
  }
}
