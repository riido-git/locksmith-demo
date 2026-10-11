package in.riido.locksmith.demo;

import in.riido.locksmith.autoconfigure.LocksmithAutoConfiguration;
import in.riido.locksmith.autoconfigure.LocksmithDisabledAutoConfiguration;
import in.riido.locksmith.autoconfigure.LocksmithInactiveAutoConfiguration;
import org.redisson.spring.starter.RedissonAutoConfigurationV4;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * A small context with only the Redisson and Locksmith auto-configurations, on the shared Redis.
 * Tests add the beans they need, so a misconfigured bean never reaches the demo app's own context.
 */
public final class LocksmithContexts {

  private LocksmithContexts() {}

  public static ApplicationContextRunner runner() {
    SharedRedis.REDIS.start();
    return new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                RedissonAutoConfigurationV4.class,
                LocksmithAutoConfiguration.class,
                LocksmithDisabledAutoConfiguration.class,
                LocksmithInactiveAutoConfiguration.class))
        .withPropertyValues(
            "spring.data.redis.host=" + SharedRedis.REDIS.getHost(),
            "spring.data.redis.port=" + SharedRedis.REDIS.getMappedPort(6379));
  }
}
