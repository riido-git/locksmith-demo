package in.riido.locksmith.demo.async;

import static org.assertj.core.api.Assertions.assertThat;

import in.riido.locksmith.demo.FeatureTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class AsyncTest extends FeatureTest {

  @Test
  void completableFutureHoldsTheLockOnTheWorkerThreadForTheWholeBody() {
    assertHeldOnWorker(http.post("/async/jobs/a1?workMs=100").json());
  }

  @Test
  void futureHoldsTheLockOnTheWorkerThreadForTheWholeBody() {
    assertHeldOnWorker(http.post("/async/jobs/a2/future?workMs=100").json());
  }

  private static void assertHeldOnWorker(JsonNode job) {
    // task-N threads belong to Spring Boot's applicationTaskExecutor, not to Tomcat.
    assertThat(job.get("thread").asString()).startsWith("task-");
    assertThat(job.get("lockedAtStart").asBoolean()).isTrue();
    assertThat(job.get("lockedAtEnd").asBoolean()).isTrue();
  }
}
