package in.riido.locksmith.demo.limitation;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/limitations")
@RequiredArgsConstructor
public class SelfInvocationController {

  private final SelfInvocationService service;

  @GetMapping("/self-invocation")
  public Map<String, Boolean> selfInvocation() {
    Map<String, Boolean> lockHeld = new LinkedHashMap<>();
    lockHeld.put("throughProxy", service.lockHeldInside());
    lockHeld.put("calledOnThis", service.lockHeldInsideWhenCalledOnThis());
    return lockHeld;
  }
}
