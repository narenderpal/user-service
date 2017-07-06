package io.vertx.stackoverflow.user;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

/**
 * Created by napal on 25/06/17.
 */
public interface UserService {

  void addUser(User user, Handler<AsyncResult<JsonObject>> resultHandler);
  void deleteUser(String username, Handler<AsyncResult<Void>> resultHandler);
  void retrieveUser(String username, Handler<AsyncResult<JsonObject>> resultHandler);
  void loginUser(String username, String password, Handler<AsyncResult<JsonObject>> resultHandler);
  void logoutUser(Handler<AsyncResult<Void>> resultHandler);
  void updateUser(String username, User user, Handler<AsyncResult<Void>> resultHandler);
}
