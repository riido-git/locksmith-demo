package in.riido.locksmith.demo.api;

import in.riido.locksmith.demo.api.CartController.CartResult;
import in.riido.locksmith.lock.LockFailureContext;
import in.riido.locksmith.lock.LockFailureHandler;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Called by Locksmith when CartController.delete cannot get its lock; its ResponseEntity is the
 * method's result.
 */
@Component
public class BusyCartHandler implements LockFailureHandler {

  @Override
  public Object onFailure(LockFailureContext context) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .header(HttpHeaders.RETRY_AFTER, "1")
        .body(new CartResult((String) context.args()[0], "busy"));
  }
}
