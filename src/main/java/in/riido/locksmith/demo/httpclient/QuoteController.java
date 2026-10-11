package in.riido.locksmith.demo.httpclient;

import in.riido.locksmith.demo.httpclient.QuoteClient.Quote;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The client is the service layer here: it is the bean that carries the annotation. */
@RestController
@RequiredArgsConstructor
public class QuoteController {

  private final QuoteClient client;

  @GetMapping("/quotes/{sku}")
  public Quote quote(@PathVariable String sku, @RequestParam(defaultValue = "1000") long workMs) {
    return client.quote(sku, workMs);
  }
}
