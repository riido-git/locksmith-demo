package in.riido.locksmith.demo.tracker;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** What the stress tests read after a run. Not a Locksmith feature. */
@RestController
@RequestMapping("/tracker")
@RequiredArgsConstructor
public class TrackerController {

  private final ConcurrencyTracker tracker;

  @GetMapping
  public Map<String, Map<String, Long>> snapshot() {
    return tracker.snapshot();
  }

  @DeleteMapping
  public void reset() {
    tracker.reset();
  }

  @GetMapping("/keys")
  public List<String> locksmithKeys() {
    return tracker.locksmithKeys();
  }
}
