package in.riido.locksmith.demo.async;

import in.riido.locksmith.demo.async.AsyncService.JobResult;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/async/jobs")
@RequiredArgsConstructor
public class AsyncController {

  private final AsyncService service;

  @PostMapping("/{jobId}")
  public CompletableFuture<JobResult> run(
      @PathVariable String jobId, @RequestParam(defaultValue = "1000") long workMs) {
    return service.run(jobId, workMs);
  }

  @PostMapping("/{jobId}/future")
  public JobResult runAsFuture(
      @PathVariable String jobId, @RequestParam(defaultValue = "1000") long workMs)
      throws InterruptedException, ExecutionException {
    return service.runAsFuture(jobId, workMs).get();
  }
}
