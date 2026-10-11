package in.riido.locksmith.demo;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Calls one running instance over HTTP. Safe to share between threads. */
public final class DemoHttp {

  private static final HttpClient CLIENT =
      HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_1_1)
          .connectTimeout(Duration.ofSeconds(5))
          .build();

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final String baseUrl;

  public DemoHttp(int port) {
    this.baseUrl = "http://localhost:" + port;
  }

  public Response get(String path) {
    return send("GET", path, null);
  }

  public Response post(String path) {
    return send("POST", path, null);
  }

  public Response put(String path) {
    return send("PUT", path, null);
  }

  public Response delete(String path) {
    return send("DELETE", path, null);
  }

  /** Sends a request; {@code jsonBody} may be null. */
  public Response send(String method, String path, String jsonBody) {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(60))
            .method(
                method,
                jsonBody == null ? BodyPublishers.noBody() : BodyPublishers.ofString(jsonBody));
    if (jsonBody != null) {
      request.header("Content-Type", "application/json");
    }
    long start = System.nanoTime();
    try {
      var response = CLIENT.send(request.build(), BodyHandlers.ofString());
      return new Response(
          response.statusCode(), response.body(), System.nanoTime() - start, response.headers());
    } catch (IOException ex) {
      throw new IllegalStateException(method + " " + path + " failed", ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(method + " " + path + " was interrupted", ex);
    }
  }

  public static int freePort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException ex) {
      throw new IllegalStateException(ex);
    }
  }

  public record Response(int status, String body, long nanos, HttpHeaders headers) {

    public JsonNode json() {
      return JSON.readTree(body);
    }
  }
}
