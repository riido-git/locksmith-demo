package in.riido.locksmith.demo.failure;

import in.riido.locksmith.DistributedSemaphore;
import in.riido.locksmith.OnFailure;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.springframework.stereotype.Service;

/**
 * Each method returns a real value when it gets the single permit of "skip-defaults". When SKIP
 * applies, the method does not run and Locksmith returns the default of its return type instead.
 */
@Service
public class SkipDefaultsService {

  public static final String KEY = "skip-defaults";

  @DistributedSemaphore(key = KEY, permits = "1", onFailure = OnFailure.SKIP)
  public boolean asBoolean() {
    return true;
  }

  @DistributedSemaphore(key = KEY, permits = "1", onFailure = OnFailure.SKIP)
  public int asInt() {
    return 42;
  }

  @DistributedSemaphore(key = KEY, permits = "1", onFailure = OnFailure.SKIP)
  public Optional<String> asOptional() {
    return Optional.of("value");
  }

  @DistributedSemaphore(key = KEY, permits = "1", onFailure = OnFailure.SKIP)
  public OptionalInt asOptionalInt() {
    return OptionalInt.of(42);
  }

  @DistributedSemaphore(key = KEY, permits = "1", onFailure = OnFailure.SKIP)
  public String asString() {
    return "value";
  }

  @DistributedSemaphore(key = KEY, permits = "1", onFailure = OnFailure.SKIP)
  public List<String> asList() {
    return List.of("value");
  }
}
