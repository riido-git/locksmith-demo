package in.riido.locksmith.demo.lock;

import in.riido.locksmith.demo.lock.LockService.OrderResult;
import in.riido.locksmith.lock.LockFailureContext;
import in.riido.locksmith.lock.LockFailureHandler;
import org.springframework.stereotype.Component;

/**
 * Called by Locksmith when processOrHandle cannot get its lock; its return value becomes the
 * method's result.
 */
@Component
public class BusyOrderHandler implements LockFailureHandler {

  @Override
  public Object onFailure(LockFailureContext context) {
    return new OrderResult((String) context.args()[0], "busy");
  }
}
