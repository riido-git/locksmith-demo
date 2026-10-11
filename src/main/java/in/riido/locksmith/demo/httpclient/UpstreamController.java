package in.riido.locksmith.demo.httpclient;

import in.riido.locksmith.demo.httpclient.QuoteClient.Quote;
import in.riido.locksmith.demo.tracker.ConcurrencyTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stands in for a slow service owned by someone else. It records how many calls it serves at once,
 * across all instances; QuoteClient's semaphore keeps that at 2 or fewer. Call it through /quotes,
 * not directly.
 */
@RestController
@RequiredArgsConstructor
public class UpstreamController {

  private final ConcurrencyTracker tracker;

  @GetMapping("/upstream/quotes/{sku}")
  public Quote quote(@PathVariable String sku, @RequestParam long workMs) {
    tracker.work("semaphore:upstream", 2, workMs);
    return new Quote(sku, Math.floorMod(sku.hashCode(), 1000));
  }
}
