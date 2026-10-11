package in.riido.locksmith.demo;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;

/**
 * One Redis container for every feature test. Spring Boot reads its address
 * through @ServiceConnection, the same way it reads the Redis of compose.yaml.
 */
public interface SharedRedis {

  /** The image compose.yaml and compose.load.yaml use too. */
  String IMAGE = "redis:8.10.2";

  @ServiceConnection
  GenericContainer<?> REDIS = new GenericContainer<>(IMAGE).withExposedPorts(6379);

  /**
   * Runs redis-cli inside the container, so a check reads the Redis the app was given and no other.
   */
  static String redisCli(String... args) {
    String[] command = new String[args.length + 1];
    command[0] = "redis-cli";
    System.arraycopy(args, 0, command, 1, args.length);
    try {
      return REDIS.execInContainer(command).getStdout().trim();
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }
}
