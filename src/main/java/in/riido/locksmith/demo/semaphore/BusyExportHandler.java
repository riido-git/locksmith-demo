package in.riido.locksmith.demo.semaphore;

import in.riido.locksmith.demo.semaphore.SemaphoreService.ExportResult;
import in.riido.locksmith.semaphore.SemaphoreFailureContext;
import in.riido.locksmith.semaphore.SemaphoreFailureHandler;
import org.springframework.stereotype.Component;

/**
 * Called by Locksmith when exportOrHandle gets no permit; its return value becomes the method's
 * result.
 */
@Component
public class BusyExportHandler implements SemaphoreFailureHandler {

  @Override
  public Object onFailure(SemaphoreFailureContext context) {
    return new ExportResult("busy");
  }
}
