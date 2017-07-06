package io.vertx.stackoverflow.user.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.stackoverflow.user.User;
import io.vertx.stackoverflow.user.UserService;

/**
 * Created by napal on 25/06/17.
 */
public class UserServiceImpl implements UserService {

  private static final String COLLECTION = "users";
  private final MongoClient mongoClient;
  private JWTAuth authProvider;

  public UserServiceImpl(Vertx vertx, JsonObject config) {
    this.mongoClient = MongoClient.createShared(vertx, getMongoDbConfig(config));
    initAuthProvider(vertx);
  }

  private JsonObject getMongoDbConfig(JsonObject config) {
    String uri = config.getString("mongo_uri");
    if (uri == null) {
      // running locally using local mongo db
      //uri = "mongodb://localhost:27017";

      // using mongo db docker container
      //uri = "mongodb://mongo:27017";

      // Use mongo db as a cloud service
      uri = "mongodb://10.128.0.6:27017";
    }
    String dbName = config.getString("mongo_db");
    if (dbName == null) {
      dbName = "cmad";
    }

    JsonObject mongoConfig = new JsonObject()
      .put("connection_string", uri)
      .put("db_name", dbName);

    return mongoConfig;
  }

  private void initAuthProvider(Vertx vertx) {
    JsonObject conf = new JsonObject().put("keyStore", new JsonObject()
      .put("path", "keystore.jceks")
      .put("type", "jceks")
      .put("password", "secret"));

    authProvider = JWTAuth.create(vertx, conf);
  }

  @Override
  public void addUser(User user, Handler<AsyncResult<JsonObject>> resultHandler) {

    System.out.println("Entered retrieveUser " + user.getUsername());
    // find if the user already exists
    JsonObject query = new JsonObject().put("username", user.getUsername());
    mongoClient.findOne(COLLECTION, query, new JsonObject(),
      asyncResult1 -> {
        if (asyncResult1.succeeded()) {
          if (asyncResult1.result() == null) {
            // add user
            mongoClient.save(COLLECTION, new JsonObject().put("username", user.getUsername())
                .put("password", user.getPassword())
                .put("email", user.getEmail())
                .put("firstName", user.getFirstName())
                .put("lastName", user.getLastName())
                .put("phone", user.getPhone()),
              asyncResult2 -> {
                if (asyncResult2.succeeded()) {
                  JsonObject result = new JsonObject().put("message", "user added successfully")
                    .put("username", user.getUsername());
                  resultHandler.handle(Future.succeededFuture(result));
                } else {
                  String failureMessage = "User can't be added :"  + user.getUsername();
                  resultHandler.handle(Future.failedFuture(asyncResult2.cause()));
                }
              });
          } else {
            String failureMessage = "Username already exists :"  + user.getUsername();
            resultHandler.handle(Future.failedFuture(asyncResult1.cause()));
          }
        } else {
          mongoClient.save(COLLECTION, new JsonObject().put("username", user.getUsername())
              .put("password", user.getPassword())
              .put("firstName", user.getFirstName())
              .put("lastName", user.getLastName())
              .put("phone", user.getPhone()),
            asyncResult2 -> {
              if (asyncResult2.succeeded()) {
                JsonObject result = new JsonObject().put("message", "user added successfully")
                  .put("username", user.getUsername());
                resultHandler.handle(Future.succeededFuture(result));
              } else {
                String failureMessage = "User can't be added :"  + user.getUsername();
                resultHandler.handle(Future.failedFuture(asyncResult2.cause()));
              }
            });
        }
      });


  }

  @Override
  public void loginUser(String username, String password, Handler<AsyncResult<JsonObject>> resultHandler) {
    JsonObject query = new JsonObject().put("username", username);
    mongoClient.findOne(COLLECTION, query, null,
      asyncResult -> {
        if (asyncResult.succeeded()) {
          if (asyncResult.result() != null) {
            JsonObject json = asyncResult.result();
            if (username.equals(json.getString("username")) && password.equals(json.getString("password"))) {
              // Generate JWT token for user and send as part of user info
              String token = generateAuthToken(json);
              System.out.println("JWT token generated for user " + username);
              json.put("Authorization", token);
              resultHandler.handle(Future.succeededFuture(json));
            } else {
              resultHandler.handle(Future.failedFuture(asyncResult.cause()));
            }
          } else {
            resultHandler.handle(Future.failedFuture(asyncResult.cause()));
          }
        } else {
          resultHandler.handle(Future.failedFuture(asyncResult.cause()));
        }
      }
    );
  }

  @Override
  public void deleteUser(String username, Handler<AsyncResult<Void>> resultHandler) {

    JsonObject query = new JsonObject().put("username", username);
    mongoClient.removeDocument(COLLECTION, query,
      asyncResult -> {
        if (asyncResult.succeeded()) {
          resultHandler.handle(Future.succeededFuture());
        } else {
          resultHandler.handle(Future.failedFuture(asyncResult.cause()));
        }
      }
    );
  }

  @Override
  public void retrieveUser(String username, Handler<AsyncResult<JsonObject>> resultHandler) {
    System.out.println("Entered retrieveUser " + username);

    JsonObject query = new JsonObject().put("username", username);
    mongoClient.findOne(COLLECTION, query, null,
      asyncResult -> {
      if (asyncResult.succeeded()) {
        if (asyncResult.result() != null) {
          JsonObject user = asyncResult.result();
          resultHandler.handle(Future.succeededFuture(user));
        }
      } else {
        resultHandler.handle(Future.failedFuture(asyncResult.cause()));
      }
    });
  }

  @Override
  public void logoutUser(Handler<AsyncResult<Void>> resultHandler) {


  }

  @Override
  public void updateUser(String username, User user, Handler<AsyncResult<Void>> resultHandler) {
    JsonObject query = new JsonObject().put("username", username);
    JsonObject userJson = new JsonObject().put("username", username)
      .put("password", user.getPassword())
      .put("email", user.getEmail())
      .put("firstName", user.getFirstName())
      .put("lastName", user.getLastName())
      .put("phone", user.getPhone());

    mongoClient.findOne(COLLECTION, query, null,
      asyncResult -> {
        if (asyncResult.succeeded()) {
          if (asyncResult.result() == null) {
            JsonObject existingUser = asyncResult.result();
            String userId = existingUser.getString("_id");
            userJson.put("_id", userId);
            mongoClient.save(COLLECTION, userJson,
              asyncResult1 -> {
                if (asyncResult.succeeded()) {
                  resultHandler.handle(Future.succeededFuture());
                } else {
                  resultHandler.handle(Future.failedFuture(asyncResult1.cause()));
                }
              });
          }
        } else {
          resultHandler.handle(Future.failedFuture(asyncResult.cause()));
        }
      });
  }

  private String generateAuthToken(JsonObject user) {
    JsonObject tokenJson = new JsonObject().
      put("sub", user.getString("username")).
      put("firstName", user.getString("firstName")).
      put("lastName", user.getString("lastName"));
    return authProvider.generateToken(tokenJson, new JWTOptions());
  }


}
