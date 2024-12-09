package io.meeds.chat.service.utils;

import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.apache.commons.lang3.StringUtils;
import io.meeds.chat.model.MatrixRoomPermissions;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.User;
import org.exoplatform.ws.frameworks.json.impl.JsonException;
import org.exoplatform.ws.frameworks.json.impl.JsonGeneratorImpl;
import org.exoplatform.ws.frameworks.json.value.JsonValue;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import static io.meeds.chat.service.utils.MatrixConstants.*;

public class MatrixHttpClient {
  private static final Log LOG = ExoLogger.getLogger(MatrixHttpClient.class.toString());

  private MatrixHttpClient() {
  }

  /**
   * Get an authenticated access token for the administrative tasks
   *
   * @param userJWTToken JWT token used to authenticate the admin user
   * @return String the access token for the authenticated user
   * @throws JsonException
   * @throws IOException
   * @throws InterruptedException
   */
  public static String getAdminAccessToken(String userJWTToken) throws JsonException, IOException, InterruptedException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    if (StringUtils.isEmpty(userJWTToken)) {
      throw new IllegalArgumentException(MATRIX_ADMIN_USERNAME_IS_REQUIRED);
    }
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_matrix/client/r0/login";
    String payload = """
        {
          "type":"org.matrix.login.jwt",
          "token": "%s"
        }
        """.formatted(userJWTToken);
    try {
      HttpResponse<String> response = sendHttpPostRequest(url, null, payload);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        JsonGeneratorImpl jsonGenerator = new JsonGeneratorImpl();
        return jsonGenerator.createJsonObjectFromString(response.body()).getElement("access_token").getStringValue();
      } else {
        LOG.error("Error Authenticating admin account with JWT, Matrix server returned HTTP {} error {}",
                  String.valueOf(response.statusCode()),
                  response.body());
        return null;
      }
    } catch (Exception e) {
      LOG.error("Could not authenticate Admin account with JWT on Matrix", e);
      throw e;
    }

  }

  /**
   * Authenticates a user using his userName and password
   * @param userName the username
   * @param password the user password
   * @return String : access token for the authenticated user
   */
  public static String authenticateUser(String userName, String password) throws JsonException, IOException, InterruptedException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_matrix/client/v3/login";
    String payload = """
          {
            "identifier": {
              "type": "m.id.user",
              "user": "%s"
            },
            "password": "%s",
            "type": "m.login.password"
          }

        """.formatted(userName, password);
    try {
      HttpResponse<String> response = sendHttpPostRequest(url, null, payload);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        JsonGeneratorImpl jsonGenerator = new JsonGeneratorImpl();
        return jsonGenerator.createJsonObjectFromString(response.body()).getElement("access_token").getStringValue();
      } else {
        LOG.error("Error Authenticating admin account with JWT, Matrix server returned HTTP {} error {}",
                String.valueOf(response.statusCode()),
                response.body());
        return null;
      }
    } catch (Exception e) {
      LOG.error("Could not authenticate Admin account with JWT on Matrix", e);
      throw e;
    }

  }

  public static String createRoom(String name, String description, String token) throws Exception {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_matrix/client/v3/createRoom";

    String payload = """
        {
          "name": "%s",
          "topic": "%s",
          "preset": "private_chat",
          "visibility": "private",
          "initial_state": [
            {
              "type": "m.room.guest_access",
              "state_key": "",
              "content": {
                "guest_access": "forbidden"
              }
            }
          ]
        }
        """.formatted(name, Jsoup.parse(description).text());

    try {
      HttpResponse<String> response = sendHttpPostRequest(url, token, payload);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        JsonGeneratorImpl jsonGenerator = new JsonGeneratorImpl();
        String roomId = jsonGenerator.createJsonObjectFromString(response.body()).getElement("room_id").getStringValue();
        return roomId.substring(0, roomId.indexOf(":" + PropertyManager.getProperty(MATRIX_SERVER_NAME)));
      } else {
        LOG.error("Error creating a team, Matrix server returned HTTP {} error {}",
                  String.valueOf(response.statusCode()),
                  response.body());
        if (response.statusCode() == 429) {
          long sleepInMs = new JsonGeneratorImpl().createJsonObjectFromString(response.body())
                                                  .getElement("retry_after_ms")
                                                  .getLongValue();
          Thread.sleep(sleepInMs);
          return createRoom(name, description, token);
        } else {
          throw new Exception("Error creating a team, Matrix server returned HTTP %s error %s".formatted(String.valueOf(response.statusCode()),
                                                                                                         response.body()));
        }
      }
    } catch (Exception e) {
      LOG.error("Could not create a team on Matrix", e);
      throw e;
    }
  }

  protected static HttpResponse<String> sendHttpGetRequest(String url, String token) throws IOException, InterruptedException {
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header(AUTHORIZATION, BEARER + token).GET().build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  protected static HttpResponse<String> sendHttpPostRequest(String url, String token, String contentAsJson) throws IOException,
                                                                                                            InterruptedException {
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request;
    if (StringUtils.isNotBlank(token)) {
      request = HttpRequest.newBuilder()
                           .uri(URI.create(url))
                           .header(AUTHORIZATION, BEARER + token)
                           .POST(HttpRequest.BodyPublishers.ofString(contentAsJson))
                           .build();
    } else {
      request = HttpRequest.newBuilder().uri(URI.create(url)).POST(HttpRequest.BodyPublishers.ofString(contentAsJson)).build();
    }
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  protected static HttpResponse<String> sendHttpPostRequest(String url,
                                                            String token,
                                                            String mimeType,
                                                            byte[] fileContent) throws IOException, InterruptedException {
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request;
    request = HttpRequest.newBuilder()
                         .uri(URI.create(url))
                         .header(AUTHORIZATION, BEARER + token)
                         .header(CONTENT_TYPE, mimeType)
                         .POST(HttpRequest.BodyPublishers.ofByteArray(fileContent))
                         .build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  protected static HttpResponse<String> sendHttpPutRequest(String url, String token, String contentAsJson) throws IOException,
                                                                                                           InterruptedException {
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request = HttpRequest.newBuilder()
                                     .uri(URI.create(url))
                                     .header(AUTHORIZATION, BEARER + token)
                                     .PUT(HttpRequest.BodyPublishers.ofString(contentAsJson))
                                     .build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  public static void sendInvitationToMembers(ArrayList<String> strings, String matrixRoomId) {

  }

  public static String createUserAccount(User user, String token) {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }

    String nonce = getRegistrationNonce(token);
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_synapse/admin/v1/register";
    String hmac = hmacUserProperties(nonce, user.getUserName(), user.getUserName(), false);

    String payload = """
        {
           "nonce": "%s",
           "username": "%s",
           "displayname": "%s",
           "password": "%s",
           "admin": false,
           "mac": "%s"
          }
        """.formatted(nonce, user.getUserName(), user.getDisplayName(), user.getUserName(), hmac);

    try {
      HttpResponse<String> response = sendHttpPostRequest(url, token, payload);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        JsonGeneratorImpl jsonGenerator = new JsonGeneratorImpl();
        JsonValue userAccount = jsonGenerator.createJsonObjectFromString(response.body());
        return userAccount.getElement("user_id").getStringValue();
      } else {
        LOG.error("Error creating a user account, Matrix server returned HTTP {} error {}",
                  String.valueOf(response.statusCode()),
                  response.body());
        return null;
      }
    } catch (Exception e) {
      LOG.error("Could not create a user account on Matrix", e);
      return null;
    }
  }

  public static String saveUserAccount(User user,
                                       String matrixUserId,
                                       boolean isNew,
                                       String token,
                                       boolean isEnableUserOperation) {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }

    String fullMatrixUserId = "@%s:%s".formatted(matrixUserId, PropertyManager.getProperty(MATRIX_SERVER_NAME));
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_synapse/admin/v2/users/" + fullMatrixUserId;

    String payload;
    String password = null;
    if (isNew) {
      password = PasswordGenerator.generatePassword(10);
      // TODO removes this unneeded logging
      LOG.debug("User {} password on Matrix is {}", user.getUserName().toLowerCase(), password);
      payload = """
           {
            "password": "%s",
            "logout_devices": false,
            "displayname": "%s",
            "threepids": [
                {
                    "medium": "email",
                    "address": "%s"
                }
            ],
            "admin": false,
            "deactivated": %s,
            "user_type": null,
            "locked": false
          }
          """.formatted(password, user.getDisplayName(), user.getEmail(), String.valueOf(!user.isEnabled()));
    } else if (isEnableUserOperation && user.isEnabled()) {
      payload = """
          {
           "password": "%s",
           "displayname": "%s",
           "threepids": [
               {
                   "medium": "email",
                   "address": "%s"
               }
           ],
           "deactivated": %s
           }
          """.formatted(PasswordGenerator.generatePassword(10),
                        user.getDisplayName(),
                        user.getEmail(),
                        String.valueOf(!user.isEnabled()));
    } else {
      payload = """
          {
            "displayname": "%s",
            "threepids": [
              {
                "medium": "email",
                "address": "%s"
              }
            ],
            "deactivated": %s
          }
          """.formatted(user.getDisplayName(), user.getEmail(), String.valueOf(!user.isEnabled()));
    }
    try {
      HttpResponse<String> response = sendHttpPutRequest(url, token, payload);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        JsonGeneratorImpl jsonGenerator = new JsonGeneratorImpl();
        JsonValue userAccount = jsonGenerator.createJsonObjectFromString(response.body());
        String fullMatrixID = userAccount.getElement("name").getStringValue();
        // If the user is a new user, we need to authenticate him
        if(isNew) {
          String accessTokenOfCreatedUser = authenticateUser(matrixUserId, password);
          //TODO remove this
          LOG.info("User {} authenticated successfully : {}", user.getUserName(), accessTokenOfCreatedUser);
        }
        return fullMatrixID.substring(1, fullMatrixID.indexOf(":"));
      } else {
        LOG.error("Error creating a user account, Matrix server returned HTTP {} error {}",
                  String.valueOf(response.statusCode()),
                  response.body());
        return null;
      }
    } catch (Exception e) {
      LOG.error("Could not create a user account on Matrix", e);
      return null;
    }
  }

  private static String hmacUserProperties(String nonce, String userName, String password, boolean isAdmin) {
    String userProperties = nonce + "\0" + userName + "\0" + password + "\0" + (isAdmin ? "admin" : "notadmin");
    return new HmacUtils(HmacAlgorithms.HMAC_SHA_1,
                         PropertyManager.getProperty(SHARED_SECRET_REGISTRATION)).hmacHex(userProperties);
  }

  private static String getRegistrationNonce(String accessToken) {
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_synapse/admin/v1/register";
    try {
      HttpResponse<String> response = sendHttpGetRequest(url, accessToken);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        JsonGeneratorImpl jsonGenerator = new JsonGeneratorImpl();
        JsonValue jsonResponse = jsonGenerator.createJsonObjectFromString(response.body());
        return jsonResponse.getElement("nonce").getStringValue();
      } else {
        LOG.error("Error getting Nonce, Matrix server returned HTTloginP {} error {}",
                  String.valueOf(response.statusCode()),
                  response.body());
        return null;
      }
    } catch (Exception e) {
      LOG.error("Could not get the nonce on Matrix", e);
      return null;
    }
  }

  public static String disableAccount(String userName, boolean eraseData, String token) {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_synapse/admin/v1/deactivate/" + userName;
    String payload = """
        {
          "erase": %s
        }
        """.formatted(Boolean.FALSE.toString()); // erase or not the user data on Matrix, currently : Not erase

    try {
      HttpResponse<String> response = sendHttpPostRequest(url, token, payload);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        JsonGeneratorImpl jsonGenerator = new JsonGeneratorImpl();
        JsonValue jsonResponse = jsonGenerator.createJsonObjectFromString(response.body());
        return jsonResponse.getElement("id_server_unbind_result").getStringValue();
      } else {
        LOG.error("Error deactivating user, Matrix server returned HTTP {} error {}",
                  String.valueOf(response.statusCode()),
                  response.body());
        return null;
      }
    } catch (Exception e) {
      LOG.error("Could not deactivate the user on Matrix", e);
      return null;
    }
  }

  public static String renameRoom(String roomId, String newRoomName, String token) {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String fullRoomId = roomId + ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
    String url =
               PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_matrix/client/v3/rooms/" + fullRoomId + "/state/m.room.name/";
    String payload = """
        {
          "name": "%s"
        }
        """.formatted(newRoomName);

    try {
      HttpResponse<String> response = sendHttpPutRequest(url, token, payload);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        JsonGeneratorImpl jsonGenerator = new JsonGeneratorImpl();
        JsonValue jsonResponse = jsonGenerator.createJsonObjectFromString(response.body());
        return jsonResponse.getElement("event_id").getStringValue();
      } else {
        LOG.error("Error renaming the room {}, Matrix server returned HTTP {} error {}",
                  roomId,
                  String.valueOf(response.statusCode()),
                  response.body());
        return null;
      }
    } catch (Exception e) {
      LOG.error("Could not rename the room on Matrix", e);
      return null;
    }
  }

  /**
   * Invites a user to a specific room on Matrix
   * 
   * @param roomId the Id of the room
   * @param userMatrixId the Matrix id of the user
   * @param invitationMessage a custom invitation message
   * @return
   */
  public static boolean inviteUserToRoom(String roomId, String userMatrixId, String invitationMessage, String token) {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String fullRoomId = roomId + ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
    String fullMatrixUserId = "@%s:%s".formatted(userMatrixId, PropertyManager.getProperty(MATRIX_SERVER_NAME));
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_matrix/client/v3/rooms/" + fullRoomId + "/invite";
    String payload = """
          {
            "reason": "%s",
            "user_id": "%s"
          }
        """.formatted(invitationMessage, fullMatrixUserId);

    try {
      HttpResponse<String> response = sendHttpPostRequest(url, token, payload);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        LOG.info("User {} successfully invited to room {}", userMatrixId, roomId);
        return true;
      } else {
        LOG.error("Error inviting user {} to the room {}, Matrix server returned HTTP {} error {}",
                  userMatrixId,
                  roomId,
                  String.valueOf(response.statusCode()),
                  response.body());
        return false;
      }
    } catch (Exception e) {
      LOG.error("Could not invite a user to a room on Matrix", e);
      return false;
    }
  }

  /**
   * Kicks a user out of a specific room on Matrix
   *
   * @param roomId the Id of the room
   * @param userMatrixId the Matrix id of the user
   * @param raisonMessage the raison message
   */
  public static void kickUserFromRoom(String roomId, String userMatrixId, String raisonMessage, String token) {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String fullRoomId = roomId + ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
    String fullMatrixUserId = "@%s:%s".formatted(userMatrixId, PropertyManager.getProperty(MATRIX_SERVER_NAME));

    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_matrix/client/v3/rooms/" + fullRoomId + "/kick";
    String payload = """
          {
            "reason": "%s",
            "user_id": "%s"
          }
        """.formatted(raisonMessage, fullMatrixUserId);

    try {
      HttpResponse<String> response = sendHttpPostRequest(url, token, payload);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        LOG.info("User {} successfully kicked out of room {}", userMatrixId, roomId);
      } else {
        LOG.error("Error kicking user {} from room {}, Matrix server returned HTTP {} error {}",
                  userMatrixId,
                  roomId,
                  String.valueOf(response.statusCode()),
                  response.body());
      }
    } catch (Exception e) {
      LOG.error("Could not kick out a user from the room on Matrix", e);
    }
  }

  /**
   * Adds directly a user to a room
   * 
   * @param matrixRoomId the room ID
   * @param matrixIdOfUser the ID of the user of Matrix
   * @return Boolean true if operation succeeded
   */
  public static boolean joinUserToRoom(String matrixRoomId, String matrixIdOfUser, String token) {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String fullUserMatrixId = "@%s:%s".formatted(matrixIdOfUser, PropertyManager.getProperty(MATRIX_SERVER_NAME));
    String fullRoomId = matrixRoomId + ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_synapse/admin/v1/join/" + fullRoomId;
    String payload = """
          {
            "user_id": "%s"
          }
        """.formatted(fullUserMatrixId);

    try {
      HttpResponse<String> response = sendHttpPostRequest(url, token, payload);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        LOG.info("User {} successfully joined the room {}", matrixIdOfUser, matrixRoomId);
        return true;
      } else {
        if (response.statusCode() == 429) {
          long sleepInMs = new JsonGeneratorImpl().createJsonObjectFromString(response.body())
                                                  .getElement("retry_after_ms")
                                                  .getLongValue();
          Thread.sleep(sleepInMs);
          return joinUserToRoom(matrixRoomId, matrixIdOfUser, token);
        } else {
          LOG.error("Error joining user {} to the room {}, Matrix server returned HTTP {} error {}",
                    matrixIdOfUser,
                    matrixRoomId,
                    String.valueOf(response.statusCode()),
                    response.body());
          return false;
        }
      }
    } catch (Exception e) {
      LOG.error("Could not join a user to a room on Matrix", e);
      return false;
    }
  }

  /**
   * Make user an admin of the room
   * 
   * @param matrixRoomId the room ID
   * @param matrixIdOfUser the id of the user
   * @return Boolean true if succeeded
   */

  public static boolean makeUserAdminInRoom(String matrixRoomId, String matrixIdOfUser, String token) {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String fullUserMatrixId = "@%s:%s".formatted(matrixIdOfUser, PropertyManager.getProperty(MATRIX_SERVER_NAME));
    String fullRoomId = matrixRoomId + ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_synapse/admin/v1/rooms/" + fullRoomId + "/make_room_admin";
    String payload = """
          {
            "user_id": "%s"
          }
        """.formatted(fullUserMatrixId);

    try {
      HttpResponse<String> response = sendHttpPostRequest(url, token, payload);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        LOG.info("User {} is successfully an admin of the room {}", matrixIdOfUser, matrixRoomId);
        return true;
      } else {
        LOG.error("Error upgrading user {} to Admin in the room {}, Matrix server returned HTTP {} error {}",
                  matrixIdOfUser,
                  matrixRoomId,
                  String.valueOf(response.statusCode()),
                  response.body());
        return false;
      }
    } catch (Exception e) {
      LOG.error("Could not make a user an admin in a room on Matrix", e);
      return false;
    }
  }

  /**
   * Get permissions settings of a room
   * 
   * @param matrixRoomId
   * @return MatrixRoomPermissions object containing settings of the room
   */
  public static MatrixRoomPermissions getRoomSettings(String matrixRoomId, String accessToken) {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String fullRoomId = matrixRoomId + ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_matrix/client/v3/rooms/" + fullRoomId
        + "/state/m.room.power_levels/";

    try {
      HttpResponse<String> response = sendHttpGetRequest(url, accessToken);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        LOG.info("Permissions of room  {} were successfully loaded !", matrixRoomId);
        JsonGeneratorImpl jsonGenerator = new JsonGeneratorImpl();
        JsonValue jsonResponse = jsonGenerator.createJsonObjectFromString(response.body());
        return MatrixRoomPermissions.fromJson(jsonResponse);
      } else {
        LOG.error("Error getting room permissions of {} ,Matrix server returned HTTP {} error {}",
                  matrixRoomId,
                  String.valueOf(response.statusCode()),
                  response.body());
        return null;
      }
    } catch (Exception e) {
      LOG.error("Could not get room permissions on Matrix", e);
      return null;
    }
  }

  /**
   * Updates the room settings
   *
   * @param matrixRoomId the Id of the room
   * @return MatrixRoomPermissions updated room settings
   */
  public static String updateRoomSettings(String matrixRoomId, MatrixRoomPermissions roomSettings, String accessToken) {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }

    String payload = roomSettings.toJson();
    String fullRoomId = matrixRoomId + ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_matrix/client/v3/rooms/" + fullRoomId
        + "/state/m.room.power_levels/";

    try {
      HttpResponse<String> response = sendHttpPutRequest(url, accessToken, payload);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        LOG.info("Permissions of room  {} were successfully updated !", matrixRoomId);
        JsonGeneratorImpl jsonGenerator = new JsonGeneratorImpl();
        JsonValue jsonResponse = jsonGenerator.createJsonObjectFromString(response.body());
        return jsonResponse.getElement("event_id").getStringValue();
      } else {
        LOG.error("Error updating room permissions of {} ,Matrix server returned HTTP {} error {}",
                  matrixRoomId,
                  String.valueOf(response.statusCode()),
                  response.body());
        return null;
      }
    } catch (Exception e) {
      LOG.error("Could not update room permissions on Matrix", e);
      return null;
    }
  }

  public static String uploadFile(String fileName, String mimeType, byte[] imageBytes, String accessToken) {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }

    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_matrix/media/v3/upload?filename=" + fileName;
    try {
      HttpResponse<String> response = sendHttpPostRequest(url, accessToken, mimeType, imageBytes);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        LOG.info("File uploaded successfully !");
        JsonGeneratorImpl jsonGenerator = new JsonGeneratorImpl();
        JsonValue jsonResponse = jsonGenerator.createJsonObjectFromString(response.body());
        return jsonResponse.getElement("content_uri").getStringValue();
      } else {
        LOG.error("Error uploading the file ,Matrix server returned HTTP {} error {}",
                  String.valueOf(response.statusCode()),
                  response.body());
        return null;
      }
    } catch (Exception e) {
      LOG.error("Could not upload the file on Matrix", e);
      return null;
    }
  }

  /**
   * Update room avatar
   * 
   * @param matrixRoomId the room ID
   * @param avatarURL the Avatar URL on
   * @return true if succeeded otherwise false
   */
  public static boolean updateRoomAvatar(String matrixRoomId, String avatarURL, String accessToken) {
    String fullRoomId = matrixRoomId + ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_matrix/client/v3/rooms/"
        + URLEncoder.encode(fullRoomId, StandardCharsets.UTF_8) + "/state/m.room.avatar/";
    String payload = """
        {
          "url":"%s"
        }
        """.formatted(avatarURL);
    try {
      HttpResponse<String> response = sendHttpPutRequest(url, accessToken, payload);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        LOG.info("Avatar of room {} updated successfully !", matrixRoomId);
        return true;
      } else {
        LOG.error("Error updating the avatar of the room {} ,Matrix server returned HTTP {} error {}",
                  matrixRoomId,
                  String.valueOf(response.statusCode()),
                  response.body());
        return false;
      }
    } catch (Exception e) {
      LOG.error("Could not update the avatar of the room on Matrix", e);
      return false;
    }

  }

  /**
   * Update the user avatar
   * 
   * @param userMatrixId the Matrix room ID
   * @param avatarURL the avatar URL on Matrix
   * @return true if updated, false otherwise
   */
  public static boolean updateUserAvatar(String userMatrixId, String avatarURL, String accessToken) {
    String fullMatrixUserId = "@%s:%s".formatted(userMatrixId, PropertyManager.getProperty(MATRIX_SERVER_NAME));
    String url =
               PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_matrix/client/v3/profile/" + fullMatrixUserId + "/avatar_url";
    String payload = """
        {
          "avatar_url":"%s"
        }
        """.formatted(avatarURL);
    try {
      HttpResponse<String> response = sendHttpPutRequest(url, accessToken, payload);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        LOG.info("Avatar of user {} updated successfully !", userMatrixId);
        return true;
      } else {
        LOG.error("Error updating the avatar of the user {} ,Matrix server returned HTTP {} error {}",
                  userMatrixId,
                  String.valueOf(response.statusCode()),
                  response.body());
        return false;
      }
    } catch (Exception e) {
      LOG.error("Could not update the avatar of the user on Matrix", e);
      return false;
    }

  }

  /**
   * Updates the room description
   * 
   * @param matrixRoomId the ID of the room on Matrix
   * @param description the updated description
   * @return true if the operation is successful, false otherwise
   */
  public static boolean updateRoomDescription(String matrixRoomId, String description, String accessToken) {
    String fullRoomId = matrixRoomId + ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_matrix/client/v3/rooms/"
        + URLEncoder.encode(fullRoomId, StandardCharsets.UTF_8) + "/state/m.room.topic/";
    String plainDescription = description.replaceAll("<[^>]*>", "").replace("\n", "");
    String payload = """
        {
        "topic":"%s",
        "org.matrix.msc3765.topic":
          [
            {
              "body":"%s",
              "mimetype":"text/html"
            }
          ]
        }
        """.formatted(plainDescription, URLEncoder.encode(description, StandardCharsets.UTF_8));
    try {
      HttpResponse<String> response = sendHttpPutRequest(url, accessToken, payload);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        LOG.info("Description of room {} updated successfully !", matrixRoomId);
        return true;
      } else {
        LOG.error("Error updating the description of the room {} ,Matrix server returned HTTP {} error {}",
                  matrixRoomId,
                  String.valueOf(response.statusCode()),
                  response.body());
        return false;
      }
    } catch (Exception e) {
      LOG.error("Could not update the description of the room on Matrix", e);
      return false;
    }

  }
}
