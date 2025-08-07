/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2025 Meeds Association contact@meeds.io
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package io.meeds.chat.service.utils;

import io.meeds.chat.model.MatrixMessage;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.apache.commons.lang3.StringUtils;
import io.meeds.chat.model.MatrixRoomPermissions;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.User;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.ws.frameworks.json.impl.JsonException;
import org.exoplatform.ws.frameworks.json.impl.JsonGeneratorImpl;
import org.exoplatform.ws.frameworks.json.value.JsonValue;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static io.meeds.chat.service.utils.HTTPHelper.*;
import static io.meeds.chat.service.utils.MatrixConstants.*;

@Component
public class MatrixHttpClient {
  private static final Log LOG = ExoLogger.getLogger(MatrixHttpClient.class.toString());

  /**
   * Get an authenticated access token for the administrative tasks
   *
   * @param userJWTToken JWT token used to authenticate the admin user
   * @return String the access token for the authenticated user
   * @throws JsonException
   * @throws IOException
   * @throws InterruptedException
   */
  public String getAccessToken(String userJWTToken) throws JsonException, IOException, InterruptedException {
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
      HttpResponse<String> response = sendHttpPostRequest(url, "", payload);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        JsonGeneratorImpl jsonGenerator = new JsonGeneratorImpl();
        return jsonGenerator.createJsonObjectFromString(response.body()).getElement("access_token").getStringValue();
      } else {
        if (response.statusCode() == 429) {
          long sleepInMs = new JsonGeneratorImpl().createJsonObjectFromString(response.body())
                                                  .getElement("retry_after_ms")
                                                  .getLongValue();

          LOG.warn("Too many requests on Matrix server, retrying the authentication of the admin after {}ms", sleepInMs);
          Thread.sleep(sleepInMs);
          return getAccessToken(userJWTToken);
        } else {
          LOG.error("Error Authenticating admin account with JWT, Matrix server returned HTTP {} error {}",
                    String.valueOf(response.statusCode()),
                    response.body());
          throw new IllegalStateException("Could not authenticate Admin account on Matrix");
        }
      }
    } catch (Exception e) {
      LOG.error("Could not authenticate Admin account with JWT on Matrix", e.getMessage());
      throw e;
    }

  }

  /**
   * Authenticates a user using his userName and password
   * 
   * @param userName the username
   * @param password the user password
   * @return String : access token for the authenticated user
   */
  public String authenticateUser(String userName, String password) throws JsonException, IOException, InterruptedException {
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
      HttpResponse<String> response = sendHttpPostRequest(url, "", payload);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        JsonGeneratorImpl jsonGenerator = new JsonGeneratorImpl();
        return jsonGenerator.createJsonObjectFromString(response.body()).getElement("access_token").getStringValue();
      } else {
        if (response.statusCode() == 429) {
          long sleepInMs = new JsonGeneratorImpl().createJsonObjectFromString(response.body())
                                                  .getElement("retry_after_ms")
                                                  .getLongValue();
          LOG.warn("Too many requests on Matrix server, retrying the authentication of {} after {}ms", userName, sleepInMs);
          Thread.sleep(sleepInMs);
          return authenticateUser(userName, password);
        } else {
          LOG.error("Error Authenticating user {} with a password, Matrix server returned HTTP {} error {}",
                    userName,
                    String.valueOf(response.statusCode()),
                    response.body());
          return null;
        }
      }
    } catch (Exception e) {
      LOG.error("Could not authenticate Admin account with JWT on Matrix", e);
      throw e;
    }

  }

  public String createRoom(String name, String description, String token) throws Exception {
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
        """.formatted(name.replace("\"", "\\\""), cleanDescription(description));

    try {
      HttpResponse<String> response = sendHttpPostRequest(url, token, payload);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        JsonGeneratorImpl jsonGenerator = new JsonGeneratorImpl();
        String roomId = jsonGenerator.createJsonObjectFromString(response.body()).getElement("room_id").getStringValue();
        return roomId.substring(0, roomId.indexOf(":" + PropertyManager.getProperty(MATRIX_SERVER_NAME)));
      } else {
        if (response.statusCode() == 429) {
          long sleepInMs = new JsonGeneratorImpl().createJsonObjectFromString(response.body())
                                                  .getElement("retry_after_ms")
                                                  .getLongValue();
          LOG.warn("Too many requests on Matrix server, retrying the creation of the room of {} after {}ms", name, sleepInMs);
          Thread.sleep(sleepInMs);
          return createRoom(name, description, token);
        } else {
          LOG.error("Error creating a team, Matrix server returned HTTP {} error {}",
                    String.valueOf(response.statusCode()),
                    response.body());

          throw new Exception("Error creating a team, Matrix server returned HTTP %s error %s".formatted(String.valueOf(response.statusCode()),
                                                                                                         response.body()));
        }
      }
    } catch (Exception e) {
      LOG.error("Could not create a team on Matrix", e);
      throw e;
    }
  }

  private String cleanDescription(String description) {
    String plainTextDescription = Jsoup.parse(description).text();
    if (StringUtils.isNotBlank(plainTextDescription)) {
      return plainTextDescription.replace("\"", "\\\"");
    }
    return "";
  }

  public String createUserAccount(User user, String token) {
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
        """.formatted(nonce, cleanMatrixUsername(user.getUserName()), user.getDisplayName(), user.getUserName(), hmac);

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

  /**
   * Sets a new display name for the user
   * 
   * @param userMatrixId the ID of the user on Matrix
   * @param displayName the new display name
   * @param token the access token
   * @throws IOException
   * @throws InterruptedException
   * @throws JsonException
   */
  public void updateUserDisplayName(String userMatrixId, String displayName, String token) throws IOException,
                                                                                           InterruptedException,
                                                                                           JsonException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String encodedUserMatrixId = URLEncoder.encode(userMatrixId, StandardCharsets.UTF_8);

    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_matrix/client/v3/profile/" + encodedUserMatrixId
        + "/displayname";

    String payload = """
        {
          "displayname": "%s"
        }
        """.formatted(displayName);
    HttpResponse<String> response = sendHttpPutRequest(url, token, payload);
    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      LOG.info("The display name of the User {} was updated successfully", userMatrixId);
    } else {
      if (response.statusCode() == 429) {
        long sleepInMs = new JsonGeneratorImpl().createJsonObjectFromString(response.body())
                                                .getElement("retry_after_ms")
                                                .getLongValue();
        LOG.warn("Too many requests on Matrix server, retrying to update the display name of the user {} after {}ms",
                 userMatrixId,
                 sleepInMs);
        Thread.sleep(sleepInMs);
        updateUserDisplayName(userMatrixId, displayName, token);
      } else {
        throw new RuntimeException("Error Updating the display name of the user %s, Matrix server returned HTTP %s error %s".formatted(userMatrixId,
                                                                                                                                       String.valueOf(response.statusCode()),
                                                                                                                                       response.body()));
      }
    }

  }

  /**
   * Saves the user account on Matrix
   * 
   * @param user the user identity
   * @param matrixUserId the user Matrix ID to set
   * @param isNew if the user has been just created
   * @param token the authorization token
   * @return the user Matrix ID
   */
  public String saveUserAccount(Identity user, String matrixUserId, boolean isNew, String token) {
    return saveUserAccount(user, matrixUserId, isNew, token, false, true);
  }

  /**
   * Saves the user account
   * 
   * @param user the User
   * @param matrixUserId the corresponding matrix ID
   * @param isNew if the user account is one
   * @param token the access token
   * @param isEnableUserOperation is this an operation to enable/disable the user
   *          on Matrix
   * @return the user Matrix ID
   */

  public String saveUserAccount(Identity user,
                                String matrixUserId,
                                boolean isNew,
                                String token,
                                boolean isEnableUserOperation,
                                boolean isUserEnabled) {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }

    String fullMatrixUserId = "@%s:%s".formatted(cleanMatrixUsername(matrixUserId),
                                                 PropertyManager.getProperty(MATRIX_SERVER_NAME));
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_synapse/admin/v2/users/" + fullMatrixUserId;

    String payload;
    String password = null;
    if (isNew) {
      password = PasswordKeyGenerator.generatePassword(10);
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
            "user_type": null,
            "locked": false
          }
          """.formatted(password, user.getRemoteId(), user.getProfile().getEmail());
    } else if (isEnableUserOperation && isUserEnabled) {
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
          """.formatted(PasswordKeyGenerator.generatePassword(10),
                        user.getProfile().getFullName(),
                        user.getProfile().getEmail(),
                        String.valueOf(false));
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
          """.formatted(user.getProfile().getFullName(), user.getProfile().getEmail(), String.valueOf(!isUserEnabled));
    }
    try {
      HttpResponse<String> response = sendHttpPutRequest(url, token, payload);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        JsonGeneratorImpl jsonGenerator = new JsonGeneratorImpl();
        JsonValue userAccount = jsonGenerator.createJsonObjectFromString(response.body());
        String fullMatrixID = userAccount.getElement("name").getStringValue();
        // If the user is a new user, we need to authenticate him
        if (isNew) {
          authenticateUser(matrixUserId, password);
          LOG.info("User {} authenticated successfully", user.getRemoteId());
        }
        return fullMatrixID.contains(":") ? fullMatrixID.substring(1, fullMatrixID.indexOf(":")) : fullMatrixID;
      } else {
        throw new RuntimeException("Error creating a user account, Matrix server returned HTTP %s error %s".formatted(response.statusCode(),
                                                                                                                      response.body()));
      }
    } catch (Exception e) {
      throw new RuntimeException("Could not create a user account on Matrix", e);
    }
  }

  private String hmacUserProperties(String nonce, String userName, String password, boolean isAdmin) {
    String userProperties = nonce + "\0" + userName + "\0" + password + "\0" + (isAdmin ? "admin" : "notadmin");
    return new HmacUtils(HmacAlgorithms.HMAC_SHA_1,
                         PropertyManager.getProperty(SHARED_SECRET_REGISTRATION)).hmacHex(userProperties);
  }

  private String getRegistrationNonce(String accessToken) {
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

  public String disableAccount(String userName, boolean eraseData, String token) {
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

  public String renameRoom(String roomId, String newRoomName, String token) {
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
  public boolean inviteUserToRoom(String roomId, String userMatrixId, String invitationMessage, String token) {
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
  public boolean kickUserFromRoom(String roomId, String userMatrixId, String raisonMessage, String token) {
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
        return true;
      } else {
        LOG.error("Error kicking user {} from room {}, Matrix server returned HTTP {} error {}",
                  userMatrixId,
                  roomId,
                  String.valueOf(response.statusCode()),
                  response.body());
        return false;
      }
    } catch (Exception e) {
      LOG.error("Could not kick out a user from the room on Matrix", e);
      return false;
    }
  }

  /**
   * Adds directly a user to a room
   * 
   * @param matrixRoomId the room ID
   * @param matrixIdOfUser the ID of the user of Matrix
   * @return Boolean true if operation succeeded
   */
  public boolean joinUserToRoom(String matrixRoomId, String matrixIdOfUser, String token) {
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
          LOG.warn("Too many requests on Matrix server, retrying to join the user {} on the room {} after {}ms",
                   matrixIdOfUser,
                   matrixRoomId,
                   sleepInMs);
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

  public boolean makeUserAdminInRoom(String matrixRoomId, String matrixIdOfUser, String token) {
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
  public MatrixRoomPermissions getRoomSettings(String matrixRoomId,
                                               String accessToken) throws IOException, InterruptedException, JsonException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String fullRoomId = matrixRoomId + ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_matrix/client/v3/rooms/" + fullRoomId
        + "/state/m.room.power_levels/";

    HttpResponse<String> response = sendHttpGetRequest(url, accessToken);
    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      LOG.info("Permissions of room  {} were successfully loaded !", matrixRoomId);
      JsonGeneratorImpl jsonGenerator = new JsonGeneratorImpl();
      JsonValue jsonResponse = jsonGenerator.createJsonObjectFromString(response.body());
      return MatrixRoomPermissions.fromJson(jsonResponse);
    } else {
      throw new RuntimeException("Error getting room permissions of %s ,Matrix server returned HTTP %s error %s".formatted(matrixRoomId,
                                                                                                                           String.valueOf(response.statusCode()),
                                                                                                                           response.body()));
    }
  }

  /**
   * Updates the room settings
   *
   * @param matrixRoomId the Id of the room
   * @return MatrixRoomPermissions updated room settings
   */
  public String updateRoomSettings(String matrixRoomId,
                                   MatrixRoomPermissions roomSettings,
                                   String accessToken) throws IOException, InterruptedException, JsonException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }

    String payload = roomSettings.toJson();
    String fullRoomId = matrixRoomId + ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_matrix/client/v3/rooms/" + fullRoomId
        + "/state/m.room.power_levels/";

    HttpResponse<String> response = sendHttpPutRequest(url, accessToken, payload);
    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      LOG.info("Permissions of room  {} were successfully updated !", matrixRoomId);
      JsonGeneratorImpl jsonGenerator = new JsonGeneratorImpl();
      JsonValue jsonResponse = jsonGenerator.createJsonObjectFromString(response.body());
      return jsonResponse.getElement("event_id").getStringValue();
    } else {
      if (response.statusCode() == 429) {
        long sleepInMs = new JsonGeneratorImpl().createJsonObjectFromString(response.body())
                                                .getElement("retry_after_ms")
                                                .getLongValue();
        LOG.warn("Too many requests on Matrix server, retrying to update the settings of the room {} after {}ms",
                 matrixRoomId,
                 sleepInMs);
        Thread.sleep(sleepInMs);
        return updateRoomSettings(matrixRoomId, roomSettings, accessToken);
      } else {
        throw new RuntimeException("Error updating room permissions of %s ,Matrix server returned HTTP %s error %s".formatted(matrixRoomId,
                                                                                                                              String.valueOf(response.statusCode()),
                                                                                                                              response.body()));
      }
    }
  }

  public String uploadFile(String fileName, String mimeType, byte[] imageBytes, String accessToken) {
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
  public boolean updateRoomAvatar(String matrixRoomId, String avatarURL, String accessToken) {
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
        if (response.statusCode() == 429) {
          long sleepInMs = new JsonGeneratorImpl().createJsonObjectFromString(response.body())
                                                  .getElement("retry_after_ms")
                                                  .getLongValue();

          LOG.warn("Too many requests on Matrix server, retrying the update of the room avatar after {}ms", sleepInMs);
          Thread.sleep(sleepInMs);
          return updateRoomAvatar(matrixRoomId, avatarURL, accessToken);
        } else {
          throw new RuntimeException("Error updating the avatar of the room %s ,Matrix server returned HTTP %s error %s".formatted(matrixRoomId,
                                                                                                                                   String.valueOf(response.statusCode()),
                                                                                                                                   response.body()));
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Could not update the avatar of the room on Matrix", e);
    }

  }

  /**
   * Update the user avatar
   * 
   * @param userMatrixId the Matrix room ID
   * @param avatarURL the avatar URL on Matrix
   * @return true if updated, false otherwise
   */
  public boolean updateUserAvatar(String userMatrixId, String avatarURL, String accessToken) {
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
        if (response.statusCode() == 429) {
          long sleepInMs = new JsonGeneratorImpl().createJsonObjectFromString(response.body())
                                                  .getElement("retry_after_ms")
                                                  .getLongValue();

          LOG.warn("Too many requests on Matrix server, retrying the update of the room avatar after {}ms", sleepInMs);
          Thread.sleep(sleepInMs);
          return updateUserAvatar(userMatrixId, avatarURL, accessToken);
        } else {
          LOG.error("Error updating the avatar of the user {} ,Matrix server returned HTTP {} error {}",
                    userMatrixId,
                    String.valueOf(response.statusCode()),
                    response.body());
          return false;
        }
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
  public boolean updateRoomDescription(String matrixRoomId, String description, String accessToken) {
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

  /**
   * Deletes a Matrix room
   * 
   * @param matrixRoomId the ID of the room
   * @param accessToken the access token
   * @return boolean True if the deletion is successful otherwise False
   */
  public boolean deleteRoom(String matrixRoomId, String accessToken) {
    String fullRoomId = matrixRoomId + ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_synapse/admin/v1/rooms/"
        + URLEncoder.encode(fullRoomId, StandardCharsets.UTF_8);
    String payload = """
          {
          }
        """;
    try {
      HttpResponse<String> response = sendHttpDeleteRequest(url, accessToken, payload);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        LOG.info("The room {} was deleted successfully !", matrixRoomId);
        return true;
      } else {
        LOG.error("Error deleting the room {} ,Matrix server returned HTTP {} error {}",
                  matrixRoomId,
                  String.valueOf(response.statusCode()),
                  response.body());
        return false;
      }
    } catch (Exception e) {
      LOG.error("Could not delete the room on Matrix", e);
      return false;
    }
  }

  /**
   * Cleans the username to be compatible with the usernames on Matrix server
   * 
   * @param userName the user identifier
   * @return String the cleaned username
   */
  public String cleanMatrixUsername(String userName) {
    return userName.replaceAll("[^a-zA-Z0-9=_\\-\\.\\/+]+", "-").toLowerCase();
  }

  public String getUserDisplayName(String userMatrixId,
                                   String matrixAccessToken) throws IOException, InterruptedException, JsonException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String encodedUserMatrixId = URLEncoder.encode(userMatrixId, StandardCharsets.UTF_8);

    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_matrix/client/v3/profile/" + encodedUserMatrixId
        + "/displayname";

    HttpResponse<String> response = sendHttpGetRequest(url, matrixAccessToken);
    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      return new JsonGeneratorImpl().createJsonObjectFromString(response.body()).getElement("displayname").getStringValue();
    } else {
      throw new RuntimeException("Error Updating the display name of the user %s, Matrix server returned HTTP %s error %s".formatted(userMatrixId,
                                                                                                                                     String.valueOf(response.statusCode()),
                                                                                                                                     response.body()));
    }

  }

  /**
   * Retrieve the user presence from Matrix server
   * 
   * @param matrixIdOfUser the ID of the user on Matrix
   * @param accessToken the access token
   * @return the value of the presence
   * @throws IOException
   * @throws InterruptedException
   * @throws JsonException
   */
  public String getUserPresence(String matrixIdOfUser,
                                String accessToken) throws IOException, InterruptedException, JsonException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String encodedUserMatrixId = URLEncoder.encode(matrixIdOfUser, StandardCharsets.UTF_8);
    String url =
               PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_matrix/client/v3/presence/" + encodedUserMatrixId + "/status";

    HttpResponse<String> response = sendHttpGetRequest(url, accessToken);
    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      return new JsonGeneratorImpl().createJsonObjectFromString(response.body()).getElement("presence").getStringValue();
    } else {
      throw new RuntimeException("Error retrieving the presence of the user %s ,Matrix server returned HTTP %s error %s".formatted(matrixIdOfUser,
                                                                                                                                   String.valueOf(response.statusCode()),
                                                                                                                                   response.body()));
    }
  }

  /**
   * Retrieve the user details from Matrix server
   *
   * @param matrixIdOfUser the ID of the user on Matrix
   * @param accessToken the access token
   * @return String representing JSON of the user
   * @throws IOException
   * @throws InterruptedException
   */
  public String getUser(String matrixIdOfUser, String accessToken) throws IOException, InterruptedException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String encodedUserMatrixId = URLEncoder.encode(matrixIdOfUser, StandardCharsets.UTF_8);
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_synapse/admin/v2/users/" + encodedUserMatrixId;

    HttpResponse<String> response = sendHttpGetRequest(url, accessToken);
    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      return response.body();
    } else {
      throw new RuntimeException("Error retrieving the details of the user %s ,Matrix server returned HTTP %s error %s".formatted(matrixIdOfUser,
                                                                                                                                  String.valueOf(response.statusCode()),
                                                                                                                                  response.body()));
    }
  }

  /**
   * Set the user presence on Matrix server
   * 
   * @param matrixIdOfUser the ID of the user n Matrix
   * @param presence the presence value : 'online, offline , unavailable"
   * @param statusMessage : the personalized status message
   * @param accessToken the access token
   * @return
   * @throws IOException
   * @throws InterruptedException
   * @throws JsonException
   */
  public String setUserPresence(String matrixIdOfUser,
                                String presence,
                                String statusMessage,
                                String accessToken) throws IOException, InterruptedException, JsonException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String encodedUserMatrixId = URLEncoder.encode(matrixIdOfUser, StandardCharsets.UTF_8);
    String url =
               PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_matrix/client/v3/presence/" + encodedUserMatrixId + "/status";

    String payload = """
        {
          "presence": "%s",
          "status_msg": "%s"
        }
        """.formatted(presence, statusMessage);

    HttpResponse<String> response = sendHttpPutRequest(url, accessToken, payload);
    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      return response.body();
    } else {
      if (response.statusCode() == 429) {
        long sleepInMs = new JsonGeneratorImpl().createJsonObjectFromString(response.body())
                                                .getElement("retry_after_ms")
                                                .getLongValue();
        LOG.warn("Too many requests on Matrix server, retrying to retrieve the user presence after {}ms", sleepInMs);
        Thread.sleep(sleepInMs);
        return setUserPresence(matrixIdOfUser, presence, statusMessage, accessToken);
      } else {
        throw new RuntimeException("Error retrieving the presence of the user %s ,Matrix server returned HTTP %s error %s".formatted(matrixIdOfUser,
                                                                                                                                     String.valueOf(response.statusCode()),
                                                                                                                                     response.body()));
      }
    }
  }

  /**
   * Overrides the rate limits for a given user
   * 
   * @param userIdOnMatrix the user Id on Matrix
   * @param messagesPerSecond the allowed number of messages per second
   * @param burstCount how many actions that can be performed before being limited
   * @param accessToken the access token
   * @return String the applied rate limits configuration
   * @throws IOException
   * @throws InterruptedException
   */
  public String overrideRateLimitForUser(String userIdOnMatrix,
                                         int messagesPerSecond,
                                         int burstCount,
                                         String accessToken) throws IOException, InterruptedException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String encodedUserMatrixId = URLEncoder.encode(userIdOnMatrix, StandardCharsets.UTF_8);
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_synapse/admin/v1/users/" + encodedUserMatrixId
        + "/override_ratelimit";

    String payload = """
        {
          "messages_per_second": %s,
          "burst_count": %s
        }
        """.formatted(messagesPerSecond, burstCount);

    HttpResponse<String> response = sendHttpPostRequest(url, accessToken, payload);
    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      return response.body();
    } else {
      throw new RuntimeException("Error overriding the rate limits for the user %s ,Matrix server returned HTTP %s error %s".formatted(userIdOnMatrix,
                                                                                                                                       String.valueOf(response.statusCode()),
                                                                                                                                       response.body()));
    }
  }

  /**
   * Gets the overridden the rate limits for a given user
   * 
   * @param userIdOnMatrix the user Id on Matrix
   * @param accessToken the access token
   * @return String the applied rate limits configuration
   * @throws IOException
   * @throws InterruptedException
   */
  public String getOverriddenRateLimitForUser(String userIdOnMatrix, String accessToken) throws IOException,
                                                                                         InterruptedException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String encodedUserMatrixId = URLEncoder.encode(userIdOnMatrix, StandardCharsets.UTF_8);
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_synapse/admin/v1/users/" + encodedUserMatrixId
        + "/override_ratelimit";

    HttpResponse<String> response = sendHttpGetRequest(url, accessToken);
    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      return response.body();
    } else {
      throw new RuntimeException("Error overriding the rate limits for the user %s ,Matrix server returned HTTP %s error %s".formatted(userIdOnMatrix,
                                                                                                                                       String.valueOf(response.statusCode()),
                                                                                                                                       response.body()));
    }
  }

  /**
   * Retrieves an event from Matrix by its Id
   *
   * @param eventId the event ID
   * @return Map representing the message
   */
  public MatrixMessage getEventById(String eventId, String roomId, String accessToken) throws IOException,
                                                                                       InterruptedException,
                                                                                       JsonException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_matrix/client/v3/rooms/" + roomId + "/event/" + eventId;

    HttpResponse<String> response = sendHttpGetRequest(url, accessToken);
    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      JsonValue jsonMessage = new JsonGeneratorImpl().createJsonObjectFromString(response.body());
      MatrixMessage message = new MatrixMessage();
      message.setEventId(eventId);
      message.setRoomId(jsonMessage.getElement("room_id").getStringValue());
      message.setType(jsonMessage.getElement("type").getStringValue());
      message.setSender(jsonMessage.getElement("sender").getStringValue());
      if (jsonMessage.getElement("content") != null) {
        JsonValue content = jsonMessage.getElement("content");
        message.setMessageContent(content.getElement("body").getStringValue());
        message.setMessageType(content.getElement("msgtype").getStringValue());
        if ("m.text".equals(message.getMessageType()) && content.getElement("org.matrix.custom.html") != null) {
          message.setMessageContent(jsonMessage.getElement("formatted_body").getStringValue());
        }
        JsonValue mentionsElement = content.getElement("m.mentions");
        if (mentionsElement != null) {
          JsonValue mentionedUsersElement = mentionsElement.getElement("user_ids");
          if (mentionedUsersElement != null) {
            Iterator<JsonValue> mentionedUsersIterator = mentionedUsersElement.getElements();
            List<String> mentionedUsers = new ArrayList<>();
            while (mentionedUsersIterator.hasNext()) {
              JsonValue nextMentioned = mentionedUsersIterator.next();
              mentionedUsers.add(nextMentioned.getStringValue());
            }
            message.setMentionedUsers(mentionedUsers);
          }
        }
      }
      return message;
    } else {
      if (response.statusCode() != 404) {
        throw new RuntimeException("Error retrieving the message of the event %s ,Matrix server returned HTTP %s error %s".formatted(eventId,
                                                                                                                                     String.valueOf(response.statusCode()),
                                                                                                                                     response.body()));
      } else {
        // if the event is missing or the user has not the right to access it (event in
        // a private room)
        return null;
      }
    }
  }

  /**
   * Invalidates an access token on Matrix server
   * 
   * @param accessToken
   * @return
   * @throws IOException
   * @throws InterruptedException
   */
  public boolean invalidateAccessToken(String accessToken) throws IOException, InterruptedException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_matrix/client/v3/logout";

    HttpResponse<String> response = sendHttpPostRequest(url, accessToken, "");
    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      return true;
    } else {
      throw new RuntimeException("Error invalidating access token ,Matrix server returned HTTP %s error %s".formatted(String.valueOf(response.statusCode()),
                                                                                                                      response.body()));
    }
  }
}
