package in.riido.locksmith.demo.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import in.riido.locksmith.demo.DemoHttp.Response;
import in.riido.locksmith.demo.FeatureTest;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class TransactionTest extends FeatureTest {

  @Test
  void theTransactionCommitsWhileTheLockIsStillHeld() {
    JsonNode result = http.post("/counters/tx1/increment?workMs=1").json();
    assertThat(result.get("value").asLong()).isEqualTo(1);
    assertThat(result.get("lockHeldAtCommit").asBoolean()).isTrue();
  }

  @Test
  void concurrentReadModifyWriteUnderTheLockLosesNoUpdate() {
    List<CompletableFuture<Response>> calls =
        IntStream.range(0, 20)
            .mapToObj(
                i ->
                    CompletableFuture.supplyAsync(
                        () -> http.post("/counters/tx2/increment?workMs=20")))
            .toList();
    calls.forEach(call -> assertThat(call.join().status()).isEqualTo(200));

    assertThat(http.get("/counters/tx2").json().get("value").asLong()).isEqualTo(20);
  }
}
