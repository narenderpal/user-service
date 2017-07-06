package io.vertx.stackoverflow.user;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.stackoverflow.user.api.UserAPIVerticle;
import io.vertx.stackoverflow.user.impl.UserServiceImpl;

/**
 * Main verticle for publishing the user micro service
 *
 * Created by napal on 25/06/17.
 */
public class UserVerticle extends BaseVerticle {

  private UserService service;

  @Override
  public void start(Future<Void> future) throws Exception {
    super.start();

    //create the service instance
    service = new UserServiceImpl(vertx, config());

    //ProxyHelper.registerService(StoreCRUDService.class, vertx, crudService, SERVICE_ADDRESS);

    // publish service and deploy REST verticle
    // TODO no need to publish event bus service, remove this
    //publishEventBusService("user-service", "service.user", UserService.class)
    //  .compose(servicePublished -> deployRestVerticle(service))
    //  .setHandler(future.completer());

    // TODO : just deploy the Rest verticle here , which will create service discovery and publish http end point
    deployRestVerticle(service);

  }

  private Future<Void> deployRestVerticle(UserService service) {
    Future<String> future = Future.future();
    vertx.deployVerticle(new UserAPIVerticle(service),
      new DeploymentOptions().setConfig(config()),
      future.completer());
    return future.map(r -> null);
  }
}
