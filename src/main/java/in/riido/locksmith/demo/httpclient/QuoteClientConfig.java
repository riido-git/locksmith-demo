package in.riido.locksmith.demo.httpclient;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

/** Registers QuoteClient; its base URL is spring.http.serviceclient.upstream.base-url. */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = "upstream", types = QuoteClient.class)
public class QuoteClientConfig {}
