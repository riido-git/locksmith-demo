package in.riido.locksmith.demo.failure;

import in.riido.locksmith.lock.LockNotAcquiredException;
import in.riido.locksmith.semaphore.SemaphoreNotAcquiredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns Locksmith's THROW policy into HTTP responses for every controller. */
@RestControllerAdvice
public class NotAcquiredExceptionHandler {

  /** Another caller holds the lock: 409 Conflict. */
  @ExceptionHandler
  public ProblemDetail lockNotAcquired(LockNotAcquiredException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    problem.setTitle("Lock not acquired");
    problem.setProperty("key", ex.key());
    problem.setProperty("waitTime", ex.waitTime().toString());
    return problem;
  }

  /** Every permit is taken: 429 Too Many Requests. */
  @ExceptionHandler
  public ProblemDetail semaphoreNotAcquired(SemaphoreNotAcquiredException ex) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    problem.setTitle("Permit not acquired");
    problem.setProperty("key", ex.key());
    problem.setProperty("permits", ex.permits());
    problem.setProperty("waitTime", ex.waitTime().toString());
    return problem;
  }
}
