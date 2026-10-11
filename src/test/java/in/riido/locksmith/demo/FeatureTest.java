package in.riido.locksmith.demo;

import in.riido.locksmith.lock.LockOperations;
import in.riido.locksmith.semaphore.SemaphoreOperations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The whole demo app on a real port, with a real Redis. Every feature test extends this class, so
 * they all share one application context. The port is fixed before startup because the HTTP
 * interface client needs it.
 */
@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT)
@ImportTestcontainers(SharedRedis.class)
public abstract class FeatureTest {

  private static final int PORT = DemoHttp.freePort();

  protected final DemoHttp http = new DemoHttp(PORT);

  @Autowired protected LockOperations locks;

  @Autowired protected SemaphoreOperations semaphores;

  @DynamicPropertySource
  static void serverPort(DynamicPropertyRegistry registry) {
    registry.add("server.port", () -> PORT);
  }
}
