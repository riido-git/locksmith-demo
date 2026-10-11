package in.riido.locksmith.demo.limitation;

import in.riido.locksmith.DistributedLock;
import in.riido.locksmith.LockType;
import in.riido.locksmith.lock.LockOperations;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SelfInvocationService {

  private final LockOperations locks;

  @DistributedLock(key = "self-invocation", waitTime = "5s")
  public boolean lockHeldInside() {
    return locks.isLocked("self-invocation", LockType.REENTRANT);
  }

  /**
   * LIMITATION: a call on this skips the Spring proxy, so the annotated method runs without its
   * lock.
   */
  public boolean lockHeldInsideWhenCalledOnThis() {
    return lockHeldInside();
  }
}
