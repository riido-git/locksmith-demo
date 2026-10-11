package in.riido.locksmith.demo.retry;

import in.riido.locksmith.demo.retry.RetryService.RetryResult;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/retries")
@RequiredArgsConstructor
public class RetryController {

  private final RetryService service;

  @PostMapping("/{callId}")
  public RetryResult call(
      @PathVariable String callId, @RequestParam(defaultValue = "2") int failures) {
    return service.call(callId, failures, new ArrayList<>());
  }
}
