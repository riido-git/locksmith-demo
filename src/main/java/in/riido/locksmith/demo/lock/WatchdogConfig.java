package in.riido.locksmith.demo.lock;

import java.time.Duration;
import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sets Redisson's lock watchdog timeout: a lock taken without leaseTime expires this long after its
 * holder stops renewing it. The demo lowers it so /locks/lease/renewed can show renewal within
 * seconds.
 */
@Configuration(proxyBeanMethods = false)
public class WatchdogConfig {

  @Bean
  RedissonAutoConfigurationCustomizer lockWatchdogTimeout(
      @Value("${demo.lock-watchdog-timeout}") Duration timeout) {
    return config -> config.setLockWatchdogTimeout(timeout.toMillis());
  }
}
