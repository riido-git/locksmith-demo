package in.riido.locksmith.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;

class DemoApplicationTests extends FeatureTest {

  @Autowired RedissonClient redisson;

  @Test
  void contextLoads() {}

  /**
   * The Redisson client of the app writes to the test container, not to a Redis on localhost:6379.
   */
  @Test
  void redissonUsesTheTestContainer() {
    String marker = UUID.randomUUID().toString();
    redisson.getBucket("isolation-check", StringCodec.INSTANCE).set(marker);

    assertThat(SharedRedis.redisCli("GET", "isolation-check")).isEqualTo(marker);
    assertThat(redisson.getConfig().useSingleServer().getAddress())
        .endsWith(":" + SharedRedis.REDIS.getMappedPort(6379));
  }
}
