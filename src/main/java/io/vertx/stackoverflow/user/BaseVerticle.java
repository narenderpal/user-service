package io.vertx.stackoverflow.user;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.ServiceDiscoveryOptions;
import io.vertx.servicediscovery.types.EventBusService;
import io.vertx.servicediscovery.types.HttpEndpoint;

/**
 * Created by napal on 25/06/17.
 */
public class BaseVerticle extends AbstractVerticle {

  private static Logger logger = LoggerFactory.getLogger(BaseVerticle.class);

  private ServiceDiscovery discovery;
  private CircuitBreaker circuitBreaker;
  private Set<Record> registeredRecords = new ConcurrentHashSet<>();

  @Override
  public void start() throws Exception {
    // init service discovery instance
    discovery = ServiceDiscovery.create(vertx, new ServiceDiscoveryOptions().setBackendConfiguration(config()));

    // init circuit breaker instance
    JsonObject cbOptions = config().getJsonObject("circuit-breaker") != null ?
      config().getJsonObject("circuit-breaker") : new JsonObject();

    circuitBreaker = CircuitBreaker.create(cbOptions.getString("name", "circuit-breaker"), vertx,
      new CircuitBreakerOptions()
        .setMaxFailures(cbOptions.getInteger("max-failures", 5))
        .setTimeout(cbOptions.getLong("timeout", 10000L))
        .setFallbackOnFailure(true)
        .setResetTimeout(cbOptions.getLong("reset-timeout", 30000L))
    );
  }

  protected Future<Void> publishHttpEndpoint(String name, String host, int port) {
    Record record = HttpEndpoint.createRecord(name, host, port, "/",
      new JsonObject().put("api.name", config().getString("api.name", ""))
    );
    return publish(record);
  }

  protected Future<Void> publishEventBusService(String name, String address, Class serviceClass) {
    Record record = EventBusService.createRecord(name, address, serviceClass);
    return publish(record);
  }

  /**
   * Publish a service with record.
   *
   * @param record service record
   * @return async result
   */
  private Future<Void> publish(Record record) {
    if (discovery == null) {
      try {
        start();
      } catch (Exception e) {
        throw new IllegalStateException("Cannot create discovery service");
      }
    }

    Future<Void> future = Future.future();
    // publish the service
    discovery.publish(record, ar -> {
      if (ar.succeeded()) {
        registeredRecords.add(record);
        logger.info("Service <" + ar.result().getName() + "> published");
        future.complete();
      } else {
        future.fail(ar.cause());
      }
    });

    return future;
  }

  @Override
  public void stop(Future<Void> future) throws Exception {
    // In current design, the publisher is responsible for removing the service
    List<Future> futures = new ArrayList<>();
    registeredRecords.forEach(record -> {
      Future<Void> cleanupFuture = Future.future();
      futures.add(cleanupFuture);
      discovery.unpublish(record.getRegistration(), cleanupFuture.completer());
    });

    if (futures.isEmpty()) {
      discovery.close();
      future.complete();
    } else {
      CompositeFuture.all(futures)
        .setHandler(ar -> {
          discovery.close();
          if (ar.failed()) {
            future.fail(ar.cause());
          } else {
            future.complete();
          }
        });
    }
  }

  protected void addCorsHandler(Router router) {
    router.route().handler(CorsHandler.create("*")
      .allowedHeaders(allowedHeaders())
      .allowedMethods(allowedMethods()));
  }

  private Set<String> allowedHeaders() {
    Set<String> allowHeaders = new HashSet<>();
    allowHeaders.add("x-requested-with");
    allowHeaders.add("Access-Control-Allow-Origin");
    allowHeaders.add("origin");
    allowHeaders.add("Content-Type");
    allowHeaders.add("accept");
    return allowHeaders;
  }

  private Set<HttpMethod> allowedMethods() {
    Set<HttpMethod> allowMethods = new HashSet<>();
    allowMethods.add(HttpMethod.GET);
    allowMethods.add(HttpMethod.PUT);
    allowMethods.add(HttpMethod.OPTIONS);
    allowMethods.add(HttpMethod.POST);
    allowMethods.add(HttpMethod.DELETE);
    allowMethods.add(HttpMethod.PATCH);
    return allowMethods;
  }

}
