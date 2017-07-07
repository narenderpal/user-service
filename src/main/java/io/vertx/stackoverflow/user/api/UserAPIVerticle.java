package io.vertx.stackoverflow.user.api;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.stackoverflow.user.BaseVerticle;
import io.vertx.stackoverflow.user.User;
import io.vertx.stackoverflow.user.UserService;

/**
 * Created by napal on 25/06/17.
 */
public class UserAPIVerticle extends BaseVerticle {

  private static final String ADD_USER = "/user";
  private static final String RETRIEVE_USER = "/user/:id";
  private static final String UPDATE_USER = "/user/:id";
  private static final String DELETE_USER = "/user/:id";
  private static final String USER_LOGIN = "/user/login";
  private static final String USER_LOGOUT = "/user/logout";

  private final UserService userService;

  public UserAPIVerticle(UserService userService) {
    this.userService = userService;
  }

  @Override
  public void start(Future<Void> future) throws Exception{
    super.start();

    final Router router = Router.router(vertx);
    // add body handler
    router.route().handler(BodyHandler.create());

    //add api route handler
    router.post(ADD_USER).handler(this:: addUser);
    router.get(RETRIEVE_USER).handler(this:: retrieveUser);
    router.put(UPDATE_USER).handler(this:: updateUser);
    router.delete(DELETE_USER).handler(this:: deleteUser);
    router.post(USER_LOGIN).handler(this:: loginUser);
    router.post(USER_LOGOUT).handler(this:: logoutUser);

    // http server host and port
    String host = config().getString("user.service.http.address", "0.0.0.0");
    int port = config().getInteger("user.service.http.port", 8080);

    // create HTTP server and publish REST service
    // TODO : Just create and start http server... for cloud deployment service discovery and publish endpoint is not needed
    createHttpServer(router, host, port)
      //.compose(serverCreated -> publishHttpEndpoint(SERVICE_NAME, host, port))
      .setHandler(future.completer());

    //vertx.createHttpServer().requestHandler(router::accept).listen(8080);

    /*
    vertx.createHttpServer()
      .requestHandler(router::accept).listen(port);

    ServiceDiscovery discovery = ServiceDiscovery.create(vertx);
    //A service Record is an object that describes a service published by a service provider.
    // It contains a name, some metadata, a location object (describing where is the service).
    // This record is the only object shared by the provider (having published it) and the consumer (retrieve it when doing a lookup).
    // When you run your service in a container or on the cloud, it may not know its public IP and public port,
    // so the publication must be done by another entity having this info. Generally it’s a bridge.


    discovery.publish(HttpEndpoint.createRecord("library", "localhost", 8080, "/titles"), ar -> {
      if (ar.succeeded()) {
        System.out.println("Server Ready!");

      } else {
        System.out.println("Unable to start " + ar.cause().getMessage());
      }
    });*/



  }

  private void addUser(RoutingContext context) {
    User user = new User(context.getBodyAsJson());
    if (user.getUsername() == null || user.getPassword() == null) {
      badRequest(context, new IllegalStateException("Username or password is not valid"));
    } else {
      userService.addUser(user, resultHandlerNonEmpty(context));
    }
  }

  private void loginUser(RoutingContext context) {
    JsonObject user = context.getBodyAsJson();
    String username = user.getString("username");
    String password = user.getString("password");
    if (username == null || password == null) {
      badRequest(context, new IllegalStateException("Username or password is not valid"));
    } else {
      JsonObject result = new JsonObject().put("message", "user login successful");
      userService.loginUser(username, password, resultHandlerNonEmpty(context));
    }
  }

  private void retrieveUser(RoutingContext context) {
    System.out.println("Entered retrieveUser");
    String username = context.request().getParam("id");
    System.out.println("Retrieve " + username);
    userService.retrieveUser(username, resultHandlerNonEmpty(context));
  }

  private void deleteUser(RoutingContext context) {
    String username = context.request().getParam("id");
    userService.deleteUser(username, deleteResultHandler(context));
  }

  private void logoutUser(RoutingContext context) {

  }

  private void updateUser(RoutingContext context) {
    User user = new User(context.getBodyAsJson());
    if (user.getUsername() == null || user.getPassword() == null) {
      badRequest(context, new IllegalStateException("Username or password is not valid"));
    } else {
      JsonObject result = new JsonObject().put("message", "user update")
        .put("username", user.getUsername());
      userService.updateUser(user.getUsername(), user, resultVoidHandler(context, result));
    }

  }

  protected Future<Void> createHttpServer(Router router, String host, int port) {
    Future<HttpServer> httpServerFuture = Future.future();
    vertx.createHttpServer()
      .requestHandler(router::accept)
      .listen(port, httpServerFuture.completer());
    return httpServerFuture.map(r -> null);
  }

  protected Handler<AsyncResult<Void>> resultVoidHandler(RoutingContext context, JsonObject result) {
    return resultVoidHandler(context, result, 200);
  }

  protected Handler<AsyncResult<Void>> resultVoidHandler(RoutingContext context, JsonObject result, int status) {
    return ar -> {
      if (ar.succeeded()) {
        context.response()
          .setStatusCode(status == 0 ? 200 : status)
          .putHeader("content-type", "application/json")
          .end(result.encodePrettily());
      } else {
        internalError(context, ar.cause());
        ar.cause().printStackTrace();
      }
    };
  }

  protected Handler<AsyncResult<Void>> deleteResultHandler(RoutingContext context) {
    return res -> {
      if (res.succeeded()) {
        context.response().setStatusCode(204)
          .putHeader("content-type", "application/json")
          .end(new JsonObject().put("message", "delete_success").encodePrettily());
      } else {
        internalError(context, res.cause());
        res.cause().printStackTrace();
      }
    };
  }

  protected <T> Handler<AsyncResult<T>> resultHandlerNonEmpty(RoutingContext context) {
    return ar -> {
      if (ar.succeeded()) {
        T res = ar.result();
        if (res == null) {
          notFound(context);
        } else {
          context.response()
            .putHeader("content-type", "application/json")
            .end(res.toString());
        }
      } else {
        internalError(context, ar.cause());
        ar.cause().printStackTrace();
      }
    };
  }

  // error handler api

  protected void internalError(RoutingContext context, Throwable ex) {
    context.response().setStatusCode(500)
      .putHeader("content-type", "application/json")
      .end(new JsonObject().put("error", ex.getMessage()).encodePrettily());
  }

  protected void badRequest(RoutingContext context, Throwable ex) {
    context.response().setStatusCode(400)
      .putHeader("content-type", "application/json")
      .end(new JsonObject().put("error", ex.getMessage()).encodePrettily());
  }

  protected void notFound(RoutingContext context) {
    context.response().setStatusCode(404)
      .putHeader("content-type", "application/json")
      .end(new JsonObject().put("message", "not_found").encodePrettily());
  }

}
