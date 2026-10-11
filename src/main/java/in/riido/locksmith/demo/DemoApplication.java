package in.riido.locksmith.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.annotation.EnableAsync;

// @Async, @Cacheable and @Retryable are switched on because the demo shows Locksmith next to each
// of them.
@SpringBootApplication
@EnableAsync
@EnableCaching
@EnableResilientMethods
public class DemoApplication {

  public static void main(String[] args) {
    SpringApplication.run(DemoApplication.class, args);
  }
}
