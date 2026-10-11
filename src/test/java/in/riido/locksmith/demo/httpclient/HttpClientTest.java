package in.riido.locksmith.demo.httpclient;

import static org.assertj.core.api.Assertions.assertThat;

import in.riido.locksmith.demo.DemoHttp.Response;
import in.riido.locksmith.demo.FeatureTest;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

class HttpClientTest extends FeatureTest {

  @Autowired QuoteClient client;

  @Test
  void theClientBeanIsAJdkProxy() {
    Object target = AopProxyUtils.getSingletonTarget(client);
    assertThat(Proxy.isProxyClass(target.getClass())).isTrue();
  }

  @Test
  void atMostTwoUpstreamCallsAtOnce() {
    http.delete("/tracker");
    List<CompletableFuture<Response>> calls =
        IntStream.range(0, 6)
            .mapToObj(
                i -> CompletableFuture.supplyAsync(() -> http.get("/quotes/h" + i + "?workMs=300")))
            .toList();
    calls.forEach(call -> assertThat(call.join().status()).isEqualTo(200));

    JsonNode upstream = http.get("/tracker").json().get("semaphore:upstream");
    assertThat(upstream.get("holderEntries").asLong()).isEqualTo(6);
    assertThat(upstream.get("holderMax").asLong()).isEqualTo(2);
  }
}
