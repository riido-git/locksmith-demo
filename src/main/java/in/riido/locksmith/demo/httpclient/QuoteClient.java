package in.riido.locksmith.demo.httpclient;

import in.riido.locksmith.DistributedSemaphore;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * An HTTP interface client. Spring creates the bean as a JDK proxy of this interface, so the
 * annotation sits on the interface method: at most 2 calls to the slow upstream at once, across all
 * instances.
 */
@HttpExchange("/upstream")
public interface QuoteClient {

  @GetExchange("/quotes/{sku}")
  @DistributedSemaphore(key = "upstream", permits = "2", waitTime = "10s")
  Quote quote(@PathVariable String sku, @RequestParam long workMs);

  record Quote(String sku, int cents) {}
}
