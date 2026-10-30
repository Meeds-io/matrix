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
import io.meeds.chat.model.MatrixPushRule;
import io.meeds.chat.model.MatrixPusher;
import io.meeds.chat.model.MatrixUnreadRoom;
import io.meeds.chat.service.model.MediaInfo;
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
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static io.meeds.chat.service.utils.HTTPHelper.*;
import static io.meeds.chat.service.utils.MatrixConstants.*;

@Component
public class MatrixHttpClient {
  /** Longest wait a bounded login spends on a 429 answer before its one retry. */
  public static final Duration MAX_RATE_LIMIT_WAIT = Duration.ofSeconds(5);

  private static final Log    LOG                   = ExoLogger.getLogger(MatrixHttpClient.class.toString());

  /** Marker Matrix prepends to the fallback body of an edit event ("* the new text"). */
  private static final String EDIT_FALLBACK_PREFIX  = "* ";

  /** About 11 days in milliseconds: wider than any sync response cache duration. */
  private static final int    UNREAD_SYNC_TIMEOUT_RANGE = 1_000_000_000;

  /** Format a message content declares when it carries an HTML {@code formatted_body}. */
  private static final String HTML_MESSAGE_FORMAT   = "org.matrix.custom.html";

  /** Client API path of the pushers of the account owning the access token. */
  private static final String PUSHERS_API_PATH      = "/_matrix/client/v3/pushers";

  /** Client API path of the global push rules of the account owning the access token. */
  private static final String PUSH_RULES_API_PATH   = "/_matrix/client/v3/pushrules/global/";

  /** The push rule kinds, in the order Synapse evaluates them. */
  private static final List<String> PUSH_RULE_KINDS = List.of("override", "content", "postcontent", "room", "sender", "underride");

  /** The push rule action that makes a rule notify. */
  private static final String NOTIFY_ACTION         = "notify";

  /**
   * Gives each unread sync its own timeout value (see getUnreadRooms), seeded
   * from the clock so that another node, or this one after a restart, does not
   * walk the same values.
   */
  private final AtomicInteger unreadSyncCounter     =
                                                  new AtomicInteger((int) (System.currentTimeMillis() % UNREAD_SYNC_TIMEOUT_RANGE));

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
          LOG.warn("Could not authenticate admin account with JWT, Matrix server returned HTTP {}", response.statusCode());
          if (LOG.isDebugEnabled()) {
            LOG.debug("Matrix authentication error response body: {}", response.body());
          }
          throw new IllegalStateException("Could not authenticate Admin account on Matrix (HTTP " + response.statusCode() + ")");
        }
      }
    } catch (Exception e) {
      LOG.debug("Could not authenticate Admin account with JWT on Matrix", e);
      throw e;
    }

  }

  /**
   * Logs in with a JWT like {@link #getAccessToken(String)}, with a bounded
   * wait: each request is bounded by {@code timeout}, and a 429 answer is
   * waited for at most once, never more than {@link #MAX_RATE_LIMIT_WAIT},
   * before one retry. Any other failure, a second 429 included, throws.
   *
   * @param userJWTToken the JWT of the account to log in
   * @param timeout the timeout of each request
   * @return the access token
   * @throws IOException when a request fails or times out
   * @throws InterruptedException when the wait or a request is interrupted
   */
  public String getAccessToken(String userJWTToken, Duration timeout) throws IOException, InterruptedException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    if (StringUtils.isEmpty(userJWTToken)) {
      throw new IllegalArgumentException(MATRIX_ADMIN_USERNAME_IS_REQUIRED);
    }
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_matrix/client/r0/login";
    String payload = new JSONObject().put("type", "org.matrix.login.jwt").put("token", userJWTToken).toString();
    HttpResponse<String> response = sendBoundedPostRequest(url, "", payload, timeout);
    if (response.statusCode() == 429) {
      long retryAfterMs = new JSONObject(response.body()).optLong("retry_after_ms", MAX_RATE_LIMIT_WAIT.toMillis());
      Thread.sleep(Math.max(0, Math.min(retryAfterMs, MAX_RATE_LIMIT_WAIT.toMillis())));
      response = sendBoundedPostRequest(url, "", payload, timeout);
    }
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new MatrixException("Could not log in on Matrix with a JWT, Matrix server returned HTTP " + response.statusCode());
    }
    return new JSONObject(response.body()).getString("access_token");
  }

  /**
   * Lists the pushers of the account owning the access token.
   *
   * @param accessToken the account's access token
   * @param timeout the request timeout
   * @return the account's pushers, never null
   * @throws MatrixUnauthorizedException when the token is rejected
   * @throws MatrixException when Synapse answers another error
   * @throws IOException when the request fails or times out
   * @throws InterruptedException when the request is interrupted
   */
  public List<MatrixPusher> getPushers(String accessToken, Duration timeout) throws IOException, InterruptedException {
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + PUSHERS_API_PATH;
    HttpResponse<String> response = sendBoundedGetRequest(url, accessToken, timeout);
    checkClientApiResponse(response, "listing the pushers");
    JSONArray pushers = new JSONObject(response.body()).optJSONArray("pushers");
    List<MatrixPusher> result = new ArrayList<>();
    if (pushers == null) {
      return result;
    }
    for (int i = 0; i < pushers.length(); i++) {
      JSONObject pusher = pushers.getJSONObject(i);
      JSONObject data = pusher.optJSONObject("data");
      result.add(new MatrixPusher(pusher.optString("app_id", null),
                                  pusher.optString("pushkey", null),
                                  pusher.optString("kind", null),
                                  pusher.optString("app_display_name", null),
                                  pusher.optString("device_display_name", null),
                                  pusher.optString("profile_tag", null),
                                  pusher.optString("lang", null),
                                  data == null ? null : data.optString("url", null)));
    }
    return result;
  }

  /**
   * Sets an HTTP pusher of the account owning the access token, which pushes
   * event ids only ({@code event_id_only}). Synapse replaces any pusher of the
   * same {@code appId} and {@code pushkey}, and keeps the account's other
   * pushers ({@code append} false only concerns another account holding the
   * same pushkey).
   *
   * @param pusher the pusher to set
   * @param accessToken the account's access token
   * @param timeout the request timeout
   * @throws MatrixUnauthorizedException when the token is rejected
   * @throws MatrixException when Synapse answers another error
   * @throws IOException when the request fails or times out
   * @throws InterruptedException when the request is interrupted
   */
  public void setPusher(MatrixPusher pusher, String accessToken, Duration timeout) throws IOException, InterruptedException {
    JSONObject payload = new JSONObject().put("app_id", pusher.appId())
                                         .put("pushkey", pusher.pushkey())
                                         .put("kind", pusher.kind())
                                         .put("app_display_name", pusher.appDisplayName())
                                         .put("device_display_name", pusher.deviceDisplayName())
                                         .put("profile_tag", pusher.profileTag())
                                         .put("lang", pusher.lang())
                                         .put("data", new JSONObject().put("format", "event_id_only").put("url", pusher.url()))
                                         .put("append", false);
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + PUSHERS_API_PATH + "/set";
    checkClientApiResponse(sendBoundedPostRequest(url, accessToken, payload.toString(), timeout), "setting a pusher");
  }

  /**
   * Deletes a pusher of the account owning the access token: the client API
   * deletes a pusher by setting its pair ({@code appId}, {@code pushkey}) with
   * a null kind.
   *
   * @param appId the pusher's application id
   * @param pushkey the pusher's key
   * @param accessToken the account's access token
   * @param timeout the request timeout
   * @throws MatrixUnauthorizedException when the token is rejected
   * @throws MatrixException when Synapse answers another error
   * @throws IOException when the request fails or times out
   * @throws InterruptedException when the request is interrupted
   */
  public void deletePusher(String appId, String pushkey, String accessToken, Duration timeout) throws IOException,
                                                                                               InterruptedException {
    JSONObject payload = new JSONObject().put("app_id", appId).put("pushkey", pushkey).put("kind", JSONObject.NULL);
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + PUSHERS_API_PATH + "/set";
    checkClientApiResponse(sendBoundedPostRequest(url, accessToken, payload.toString(), timeout), "deleting a pusher");
  }

  /**
   * Lists the global push rules of the account owning the access token, every
   * kind, server defaults included.
   *
   * @param accessToken the account's access token
   * @param timeout the request timeout
   * @return the account's global push rules, never null
   * @throws MatrixUnauthorizedException when the token is rejected
   * @throws MatrixException when Synapse answers another error
   * @throws IOException when the request fails or times out
   * @throws InterruptedException when the request is interrupted
   */
  public List<MatrixPushRule> getPushRules(String accessToken, Duration timeout) throws IOException, InterruptedException {
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + PUSH_RULES_API_PATH;
    HttpResponse<String> response = sendBoundedGetRequest(url, accessToken, timeout);
    checkClientApiResponse(response, "listing the push rules");
    // GET .../pushrules/global/ answers the rule kinds at the top level; only
    // GET .../pushrules/ wraps them in a "global" object
    JSONObject global = new JSONObject(response.body());
    List<MatrixPushRule> result = new ArrayList<>();
    for (String kind : PUSH_RULE_KINDS) {
      JSONArray rules = global.optJSONArray(kind);
      for (int i = 0; rules != null && i < rules.length(); i++) {
        JSONObject rule = rules.getJSONObject(i);
        result.add(new MatrixPushRule(kind,
                                      rule.optString("rule_id", null),
                                      rule.optBoolean("enabled", true),
                                      isNotifying(rule.optJSONArray("actions"))));
      }
    }
    return result;
  }

  /**
   * Enables or disables one global push rule of the account owning the access
   * token. The rule id is percent-encoded into the path.
   *
   * @param kind the rule kind
   * @param ruleId the rule id
   * @param enabled the state to set
   * @param accessToken the account's access token
   * @param timeout the request timeout
   * @throws MatrixUnauthorizedException when the token is rejected
   * @throws MatrixException when Synapse answers another error
   * @throws IOException when the request fails or times out
   * @throws InterruptedException when the request is interrupted
   */
  public void setPushRuleEnabled(String kind,
                                 String ruleId,
                                 boolean enabled,
                                 String accessToken,
                                 Duration timeout) throws IOException, InterruptedException {
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + PUSH_RULES_API_PATH + kind + "/"
        + URLEncoder.encode(ruleId, StandardCharsets.UTF_8) + "/enabled";
    String payload = new JSONObject().put("enabled", enabled).toString();
    checkClientApiResponse(sendBoundedPutRequest(url, accessToken, payload, timeout), "updating the push rule " + ruleId);
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

    String fullMatrixUserId = "@%s:%s".formatted(matrixUserId,
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
          """.formatted(password, user.getProfile().getFullName(), user.getProfile().getEmail());
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
               PropertyManager.getProperty(MATRIX_SERVER_URL) + ROOMS_API_PATH + fullRoomId + "/state/m.room.name/";
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
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + ROOMS_API_PATH + fullRoomId + "/invite";
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

    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + ROOMS_API_PATH + fullRoomId + "/kick";
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
   * Checks if a user is a member of a room
   *
   * @param matrixRoomId the room ID
   * @param matrixIdOfUser the ID of the user on Matrix
   * @return true if the user is a member of the room, false otherwise
   */
  public boolean isUserMemberOfRoom(String matrixRoomId, String matrixIdOfUser, String accessToken) throws IOException,
                                                                                                      InterruptedException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String fullUserMatrixId = "@%s:%s".formatted(matrixIdOfUser, PropertyManager.getProperty(MATRIX_SERVER_NAME));
    String fullRoomId = matrixRoomId + ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + ROOMS_API_PATH + fullRoomId + "/joined_members";

    HttpResponse<String> response = sendHttpGetRequest(url, accessToken);
    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      JSONObject joinedMembers = new JSONObject(response.body()).optJSONObject("joined");
      return joinedMembers != null && joinedMembers.has(fullUserMatrixId);
    } else {
      LOG.error("Error getting members of room {}, Matrix server returned HTTP {} error {}",
                matrixRoomId,
                String.valueOf(response.statusCode()),
                response.body());
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
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + ROOMS_API_PATH + fullRoomId
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
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + ROOMS_API_PATH + fullRoomId
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
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + ROOMS_API_PATH
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
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + ROOMS_API_PATH
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
    return parseUserResponse(sendHttpGetRequest(userUrl(matrixIdOfUser), accessToken), matrixIdOfUser);
  }

  /**
   * Reads the details of a Matrix user like {@link #getUser(String, String)},
   * the request bounded by {@code timeout}.
   *
   * @param matrixIdOfUser the Matrix id of the user
   * @param accessToken the administrator's access token
   * @param timeout the request timeout
   * @return the user's details, as Synapse's admin API returns them
   * @throws IOException when the request fails or times out
   * @throws InterruptedException when the request is interrupted
   */
  public String getUser(String matrixIdOfUser, String accessToken, Duration timeout) throws IOException, InterruptedException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    return parseUserResponse(sendBoundedGetRequest(userUrl(matrixIdOfUser), accessToken, timeout), matrixIdOfUser);
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
   * Retrieves an event from Matrix by its Id. The message content is the
   * plain-text {@code body}; the HTML {@code formatted_body} is kept apart in
   * {@code MatrixMessage#getFormattedContent()} when the content declares the
   * {@code org.matrix.custom.html} format. The room id and the event id are
   * percent-encoded into the path: an event id of a room version 3 room can
   * contain {@code /} and {@code +}, and an unencoded {@code /} makes Synapse
   * answer 404.
   *
   * @param eventId the event ID
   * @param roomId the room ID
   * @param accessToken the access token used to read the event
   * @return the message, null when the event is missing or not readable with
   *         that token
   * @throws IOException when the request fails
   * @throws InterruptedException when the request is interrupted
   * @throws JsonException when the response cannot be parsed
   */
  public MatrixMessage getEventById(String eventId, String roomId, String accessToken) throws IOException,
                                                                                       InterruptedException,
                                                                                       JsonException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    return parseEventResponse(sendHttpGetRequest(eventUrl(eventId, roomId), accessToken), eventId);
  }

  /**
   * Reads one event like {@link #getEventById(String, String, String)}, the
   * request bounded by {@code timeout}.
   *
   * @param eventId the event ID
   * @param roomId the room ID
   * @param accessToken the access token used to read the event
   * @param timeout the request timeout
   * @return the message, null when the event is missing or not readable with
   *         that token
   * @throws IOException when the request fails or times out
   * @throws InterruptedException when the request is interrupted
   * @throws JsonException when the response cannot be parsed
   */
  public MatrixMessage getEventById(String eventId,
                                    String roomId,
                                    String accessToken,
                                    Duration timeout) throws IOException, InterruptedException, JsonException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    return parseEventResponse(sendBoundedGetRequest(eventUrl(eventId, roomId), accessToken, timeout), eventId);
  }

  /**
   * Retrieves the most recent messages of a room as the user owning the given
   * access token, using the Matrix client API
   * ({@code /_matrix/client/v3/rooms/{roomId}/messages}). Synapse enforces the
   * user's own visibility (membership, history visibility, redactions); if the
   * user is not allowed to read the room an empty list is returned. Events are
   * returned newest-first (the {@code dir=b} direction).
   *
   * @param matrixRoomId the room local part (without the server name suffix)
   * @param limit the maximum number of events to fetch
   * @param accessToken the requesting user's Matrix access token
   * @return the {@code m.room.message} events the user can see, newest first
   */
  public List<MatrixMessage> getRoomMessages(String matrixRoomId,
                                             int limit,
                                             String accessToken) throws IOException, InterruptedException, JsonException {
    return parseRoomMessagesResponse(sendHttpGetRequest(roomMessagesUrl(matrixRoomId, limit), accessToken), matrixRoomId);
  }

  /**
   * Reads the most recent messages of a room like
   * {@link #getRoomMessages(String, int, String)}, the request bounded by
   * {@code timeout}: a timeout or a transport failure is thrown, never answered
   * as an empty list.
   *
   * @param matrixRoomId the room local part (without the server name suffix)
   * @param limit the maximum number of events to fetch
   * @param accessToken the requesting user's Matrix access token
   * @param timeout the request timeout
   * @return the {@code m.room.message} events the user can see, newest first
   * @throws IOException when the request fails or times out
   * @throws InterruptedException when the request is interrupted
   * @throws JsonException when the response cannot be parsed
   */
  public List<MatrixMessage> getRoomMessages(String matrixRoomId,
                                             int limit,
                                             String accessToken,
                                             Duration timeout) throws IOException, InterruptedException, JsonException {
    return parseRoomMessagesResponse(sendBoundedGetRequest(roomMessagesUrl(matrixRoomId, limit), accessToken, timeout),
                                     matrixRoomId);
  }

  /**
   * Parses a single Matrix timeline event into a {@link MatrixMessage}, or returns
   * {@code null} when the event is not a textual {@code m.room.message}.
   *
   * @param event the JSON event
   * @param roomLocalId the room local part the event belongs to
   * @return the parsed message, or {@code null}
   */
  private MatrixMessage parseMessageEvent(JsonValue event, String roomLocalId) {
    JsonValue typeElement = event.getElement("type");
    if (typeElement == null || !"m.room.message".equals(typeElement.getStringValue())) {
      return null;
    }
    JsonValue content = event.getElement("content");
    if (content == null || content.getElement("body") == null) {
      return null;
    }
    MatrixMessage message = new MatrixMessage();
    message.setRoomId(roomLocalId);
    message.setType(typeElement.getStringValue());
    if (event.getElement("event_id") != null) {
      message.setEventId(event.getElement("event_id").getStringValue());
    }
    if (event.getElement("sender") != null) {
      message.setSender(event.getElement("sender").getStringValue());
    }
    if (event.getElement("origin_server_ts") != null) {
      message.setTimeStamp(Long.parseLong(event.getElement("origin_server_ts").getStringValue()));
    }
    message.setMessageContent(content.getElement("body").getStringValue());
    if (content.getElement("msgtype") != null) {
      message.setMessageType(content.getElement("msgtype").getStringValue());
    }
    readRelation(content, message);
    return message;
  }

  /**
   * Returns, for the user owning the given access token, the rooms that have
   * unread notifications together with their recent timeline messages, using a
   * lightweight {@code /sync} call. Synapse only returns rooms the user has
   * joined, so visibility is naturally enforced.
   *
   * @param accessToken the requesting user's Matrix access token
   * @param timelineLimit the maximum number of recent events to fetch per room
   * @return the user's unread rooms, never {@code null}
   */
  public List<MatrixUnreadRoom> getUnreadRooms(String accessToken,
                                               int timelineLimit) throws IOException, InterruptedException, JsonException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String filter = URLEncoder.encode("{\"room\":{\"timeline\":{\"limit\":" + timelineLimit + "}}}", StandardCharsets.UTF_8);
    // Synapse caches /sync responses per identical request
    // (caches.sync_response_cache_duration, a deployment setting, 2m by
    // default), read state included. A first sync ignores its timeout, so a
    // value distinct over a range no cache duration reaches only bypasses that
    // cache.
    int timeout = 1 + Math.floorMod(unreadSyncCounter.getAndIncrement(), UNREAD_SYNC_TIMEOUT_RANGE);
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_matrix/client/v3/sync?timeout=" + timeout + "&filter="
        + filter;

    HttpResponse<String> response = sendHttpGetRequest(url, accessToken);
    if (response.statusCode() == 401) {
      throw new MatrixUnauthorizedException("Access token rejected while syncing unread messages");
    }
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new MatrixException("Error syncing unread messages ,Matrix server returned HTTP %s error %s".formatted(String.valueOf(response.statusCode()),
                                                                                                                    response.body()));
    }
    List<MatrixUnreadRoom> unreadRooms = new ArrayList<>();
    JsonValue rooms = new JsonGeneratorImpl().createJsonObjectFromString(response.body()).getElement("rooms");
    if (rooms == null || rooms.getElement("join") == null) {
      return unreadRooms;
    }
    JsonValue joinedRooms = rooms.getElement("join");
    Iterator<String> roomIds = joinedRooms.getKeys();
    String serverName = PropertyManager.getProperty(MATRIX_SERVER_NAME);
    while (roomIds.hasNext()) {
      String fullRoomId = roomIds.next();
      JsonValue room = joinedRooms.getElement(fullRoomId);
      JsonValue notifications = room.getElement("unread_notifications");
      long unreadCount = notifications != null && notifications.getElement("notification_count") != null ?
                                                                                                          notifications.getElement("notification_count")
                                                                                                                       .getLongValue() :
                                                                                                          0;
      if (unreadCount <= 0) {
        continue;
      }
      String roomLocalId = fullRoomId.contains(":" + serverName) ? fullRoomId.substring(0, fullRoomId.indexOf(":" + serverName))
                                                                 : fullRoomId;
      List<MatrixMessage> messages = new ArrayList<>();
      JsonValue timeline = room.getElement("timeline");
      if (timeline != null && timeline.getElement("events") != null && timeline.getElement("events").isArray()) {
        Iterator<JsonValue> events = timeline.getElement("events").getElements();
        while (events.hasNext()) {
          MatrixMessage message = parseMessageEvent(events.next(), roomLocalId);
          if (message != null) {
            messages.add(message);
          }
        }
      }
      unreadRooms.add(new MatrixUnreadRoom(roomLocalId, (int) unreadCount, messages));
    }
    return unreadRooms;
  }

  /**
   * Reads a room's display name from Matrix itself, <strong>as the given user</strong>:
   * its {@code m.room.name}, or — for a two-member room, i.e. a direct message — the
   * other member's display name. Used to name a conversation the platform does not
   * track, so it is shown by its real name instead of a placeholder.
   *
   * @param roomIdLocalPart the room local part (without the server name suffix)
   * @param currentUserMatrixId the requesting user's full Matrix id, used to pick the
   *          other member of a direct message
   * @param accessToken the requesting user's Matrix access token
   * @return the room display name, or {@code null} when Matrix has none either
   */
  public String getRoomDisplayName(String roomIdLocalPart, String currentUserMatrixId, String accessToken) {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL)) || StringUtils.isBlank(roomIdLocalPart)) {
      return null;
    }
    String fullRoomId = roomIdLocalPart + ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
    String baseUrl = PropertyManager.getProperty(MATRIX_SERVER_URL) + ROOMS_API_PATH + fullRoomId;
    try {
      HttpResponse<String> nameResponse = sendHttpGetRequest(baseUrl + "/state/m.room.name", accessToken);
      if (nameResponse.statusCode() >= 200 && nameResponse.statusCode() < 300) {
        String name = new JSONObject(nameResponse.body()).optString("name", null);
        if (StringUtils.isNotBlank(name)) {
          return name;
        }
      }
      // No room name: a direct message is named after the person on the other side.
      HttpResponse<String> membersResponse = sendHttpGetRequest(baseUrl + "/joined_members", accessToken);
      if (membersResponse.statusCode() < 200 || membersResponse.statusCode() >= 300) {
        return null;
      }
      JSONObject joined = new JSONObject(membersResponse.body()).optJSONObject("joined");
      if (joined == null) {
        return null;
      }
      List<String> others = joined.keySet().stream().filter(id -> !id.equals(currentUserMatrixId)).toList();
      if (others.size() != 1) {
        return null;
      }
      JSONObject member = joined.optJSONObject(others.get(0));
      String displayName = member == null ? null : member.optString("display_name", null);
      return StringUtils.isNotBlank(displayName) ? displayName : null;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.debug("Interrupted while reading the display name of room {} from Matrix", roomIdLocalPart, e);
      return null;
    } catch (Exception e) {
      LOG.debug("Could not read the display name of room {} from Matrix", roomIdLocalPart, e);
      return null;
    }
  }

  /**
   * Runs a Matrix full-text search of message bodies <strong>as the user</strong>
   * owning the given access token, optionally scoped to a single room. Synapse
   * enforces the user's visibility, so only rooms the user can see are searched.
   * Uses the {@code /_matrix/client/v3/search} endpoint (room_events category),
   * ordered by recency.
   *
   * <p>
   * An edited message is stored by Matrix as an <strong>additional</strong>
   * {@code m.room.message} event ({@code m.relates_to.rel_type = m.replace}) whose
   * fallback body repeats the text, so Synapse returns both the original and every
   * edit as separate hits. Each edit is therefore folded back onto the event it
   * replaces, redacted events are dropped, and the hits are de-duplicated by event
   * id, so one message counts once and each hit points at an event the client
   * actually renders.
   *
   * @param query the free-text search term
   * @param roomIdLocalPart the room local part to scope the search to, or
   *          {@code null}/blank to search across all of the user's rooms
   * @param limit the maximum number of hits to return
   * @param accessToken the requesting user's Matrix access token
   * @return the matching messages, most recent first, each carrying its room id,
   *         one entry per message
   */
  public List<MatrixMessage> searchMessages(String query,
                                            String roomIdLocalPart,
                                            int limit,
                                            String accessToken) throws IOException, InterruptedException, JsonException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_matrix/client/v3/search";

    JSONObject filter = new JSONObject().put("limit", limit);
    if (StringUtils.isNotBlank(roomIdLocalPart)) {
      String fullRoomId = roomIdLocalPart + ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
      filter.put("rooms", new JSONArray().put(fullRoomId));
    }
    JSONObject roomEvents = new JSONObject().put("search_term", query).put("order_by", "recent").put("filter", filter);
    String payload = new JSONObject().put("search_categories", new JSONObject().put("room_events", roomEvents)).toString();

    HttpResponse<String> response = sendHttpPostRequest(url, accessToken, payload);
    if (response.statusCode() == 401) {
      throw new MatrixUnauthorizedException("Access token rejected while searching messages");
    }
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new RuntimeException("Error searching messages ,Matrix server returned HTTP %s error %s".formatted(String.valueOf(response.statusCode()),
                                                                                                              response.body()));
    }
    Map<String, MatrixMessage> messagesByEventId = new LinkedHashMap<>();
    Set<String> editedAwayEventIds = new HashSet<>();
    JsonValue searchCategories = new JsonGeneratorImpl().createJsonObjectFromString(response.body()).getElement("search_categories");
    if (searchCategories == null || searchCategories.getElement("room_events") == null) {
      return new ArrayList<>();
    }
    JsonValue results = searchCategories.getElement("room_events").getElement("results");
    if (results == null || !results.isArray()) {
      return new ArrayList<>();
    }
    String serverSuffix = ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
    Iterator<JsonValue> hits = results.getElements();
    while (hits.hasNext()) {
      collectSearchHit(hits.next().getElement("result"), serverSuffix, query, messagesByEventId, editedAwayEventIds);
    }
    return new ArrayList<>(messagesByEventId.values());
  }

  /**
   * Adds a single search hit to the collected results, folding edits onto the message
   * they replace and leaving out what must not be reported: redacted events, non
   * textual events and messages whose edited text no longer contains the searched
   * term.
   *
   * @param event the JSON event of the hit, may be {@code null}
   * @param serverSuffix the {@code :server.name} suffix to strip from the room id
   * @param query the free-text search term the user looked for
   * @param messagesByEventId the collected messages, keyed by the event the client
   *          renders, updated in place
   * @param editedAwayEventIds the ids of messages edited to no longer contain the
   *          term, updated in place so their outdated original is skipped too
   */
  private void collectSearchHit(JsonValue event,
                                String serverSuffix,
                                String query,
                                Map<String, MatrixMessage> messagesByEventId,
                                Set<String> editedAwayEventIds) {
    if (event == null || isRedacted(event)) {
      return;
    }
    MatrixMessage message = parseMessageEvent(event, extractRoomLocalId(event, serverSuffix));
    if (message == null) {
      return;
    }
    boolean stillMatches = applyEditRelation(event, message, query);
    String eventId = message.getEventId();
    if (eventId == null) {
      return;
    }
    if (!stillMatches) {
      // Edited to remove the term: drop the edit and the original event it replaces, which
      // Synapse still matches on its outdated body.
      editedAwayEventIds.add(eventId);
      messagesByEventId.remove(eventId);
      return;
    }
    if (!editedAwayEventIds.contains(eventId)) {
      // Hits come most recent first: an edit is seen before the event it replaces, and the
      // edited text is what the client renders, so the first entry for an id wins.
      messagesByEventId.putIfAbsent(eventId, message);
    }
  }

  /**
   * Extracts the room local part from a search hit, i.e. the room id without its
   * {@code :server.name} suffix.
   *
   * @param event the JSON event of the hit
   * @param serverSuffix the {@code :server.name} suffix to strip
   * @return the room local part, or {@code null} when the event carries no room id
   */
  private String extractRoomLocalId(JsonValue event, String serverSuffix) {
    if (event.getElement("room_id") == null) {
      return null;
    }
    String fullRoomId = event.getElement("room_id").getStringValue();
    return fullRoomId.contains(serverSuffix) ? fullRoomId.substring(0, fullRoomId.indexOf(serverSuffix)) : fullRoomId;
  }

  /**
   * Tells whether a Matrix event has been redacted (deleted). Synapse keeps redacted
   * events in its full-text index, so a deleted message would otherwise still be
   * returned — and shown — as a search hit.
   *
   * @param event the JSON event returned by the search
   * @return {@code true} when the event carries a redaction marker
   */
  private boolean isRedacted(JsonValue event) {
    JsonValue unsigned = event.getElement("unsigned");
    return unsigned != null && unsigned.getElement("redacted_because") != null;
  }

  /**
   * Folds a search hit that is an <em>edit</em> back onto the message it replaces:
   * the hit is re-pointed at the original event id (the one the client renders and
   * can scroll to) and its text is taken from {@code m.new_content} so the result
   * shows the current wording rather than the {@code * } fallback body. Hits that
   * are not edits are left untouched.
   * <p>
   * @param event the JSON event returned by the search
   * @param message the message parsed from that event, updated in place
   * @param query the free-text search term the user looked for
   * @return {@code false} when the hit is an edit whose new text no longer contains
   *         the searched term — the message must then stop being reported as a
   *         match; {@code true} for any other hit
   */
  private boolean applyEditRelation(JsonValue event, MatrixMessage message, String query) {
    JsonValue content = event.getElement("content");
    JsonValue relatesTo = content == null ? null : content.getElement("m.relates_to");
    JsonValue relationType = relatesTo == null ? null : relatesTo.getElement("rel_type");
    if (relationType == null || !"m.replace".equals(relationType.getStringValue())) {
      return true;
    }
    JsonValue replacedEventId = relatesTo.getElement("event_id");
    if (replacedEventId != null) {
      message.setEventId(replacedEventId.getStringValue());
    }
    JsonValue newContent = content.getElement("m.new_content");
    JsonValue newBody = newContent == null ? null : newContent.getElement("body");
    if (newBody != null) {
      message.setMessageContent(newBody.getStringValue());
    } else if (StringUtils.startsWith(message.getMessageContent(), EDIT_FALLBACK_PREFIX)) {
      // No m.new_content (older clients): strip the "* " fallback marker Matrix prepends.
      message.setMessageContent(message.getMessageContent().substring(EDIT_FALLBACK_PREFIX.length()));
    }
    String currentBody = message.getMessageContent();
    return StringUtils.isBlank(query) || StringUtils.isBlank(currentBody)
           || StringUtils.containsIgnoreCase(currentBody, query.trim());
  }

  /**
   * Sends a textual message to a room as the user owning the given access token,
   * using the Matrix client API. Synapse enforces that the user is allowed to send
   * to the room.
   *
   * @param matrixRoomId the room local part (without the server name suffix)
   * @param text the plain text message body
   * @param transactionId a unique client transaction id (for idempotency)
   * @param accessToken the requesting user's Matrix access token
   * @return the created event id
   */
  public String sendMessage(String matrixRoomId,
                            String text,
                            String transactionId,
                            String accessToken) throws IOException, InterruptedException, JsonException {
    String payload = new JSONObject().put("msgtype", "m.text").put("body", text).toString();
    return parseSendResponse(sendHttpPutRequest(sendMessageUrl(matrixRoomId, transactionId), accessToken, payload), matrixRoomId);
  }

  /**
   * Sends a message built by the caller to a room as the user owning the given
   * access token, the request bounded by {@code timeout}: a timeout or a
   * transport failure is thrown. The content is sent as is, so a reply, its
   * HTML body and its mentions travel exactly as the caller shaped them.
   *
   * @param matrixRoomId the room local part (without the server name suffix)
   * @param content the {@code m.room.message} content
   * @param transactionId a client transaction id; the same id for the same
   *          room and user makes a retry idempotent
   * @param accessToken the requesting user's Matrix access token
   * @param timeout the request timeout
   * @return the created event id
   * @throws IOException when the request fails or times out
   * @throws InterruptedException when the request is interrupted
   * @throws JsonException when the response cannot be parsed
   */
  public String sendMessage(String matrixRoomId,
                            JSONObject content,
                            String transactionId,
                            String accessToken,
                            Duration timeout) throws IOException, InterruptedException, JsonException {
    return parseSendResponse(sendBoundedPutRequest(sendMessageUrl(matrixRoomId, transactionId),
                                                   accessToken,
                                                   content.toString(),
                                                   timeout),
                             matrixRoomId);
  }

  /**
   * Posts an {@code m.read} receipt on behalf of a user: a Matrix read
   * receipt is a high-water mark, everything up to the event becomes read.
   *
   * @param matrixRoomId the room local id
   * @param eventId the event read up to
   * @param accessToken the user's Matrix access token
   */
  public void sendReadReceipt(String matrixRoomId, String eventId, String accessToken) throws IOException,
                                                                                       InterruptedException {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String fullRoomId = matrixRoomId.contains(":") ? matrixRoomId
                                                   : matrixRoomId + ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
    // the event id is client-supplied and, depending on the room version, may
    // carry ':', '/', '+' or '=' — it is a path segment, so it is encoded
    String url = PropertyManager.getProperty(MATRIX_SERVER_URL) + ROOMS_API_PATH + fullRoomId + "/receipt/m.read/"
        + URLEncoder.encode(eventId, StandardCharsets.UTF_8);
    // an unthreaded receipt: Synapse refuses a "main" receipt on a thread reply
    // (M_INVALID_PARAM), and only an unthreaded one also clears the threads'
    // notifications up to the event
    String payload = new JSONObject().toString();
    HttpResponse<String> response = sendHttpPostRequest(url, accessToken, payload);
    if (response.statusCode() == 401) {
      throw new MatrixUnauthorizedException("Access token rejected while sending a read receipt to room " + matrixRoomId);
    }
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new MatrixException("Error sending a read receipt to room %s, Matrix server returned HTTP %s error %s".formatted(matrixRoomId,
                                                                                                                             String.valueOf(response.statusCode()),
                                                                                                                             response.body()));
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

  public Optional<MediaInfo> getMediaInfo(String mediaId, String accessToken) throws IOException, InterruptedException {
    String serverUrl = PropertyManager.getProperty(MATRIX_SERVER_URL);
    String serverName = PropertyManager.getProperty(MATRIX_SERVER_NAME);
    String url = String.format("%s/_synapse/admin/v1/media/%s/%s", serverUrl, serverName, mediaId);
    HttpResponse<String> response = HTTPHelper.sendHttpGetRequest(url, accessToken);

    if (response.statusCode() == 200) {
        JSONObject json = new JSONObject(response.body());
        JSONObject mediaInfoJson = json.getJSONObject("media_info");

        MediaInfo info = new MediaInfo();
        info.setMediaId(mediaInfoJson.getString("media_id"));
        info.setServerName(serverName);
        info.setOwner(mediaInfoJson.optString("user_id", null));
        info.setFilename(mediaInfoJson.optString("upload_name", mediaId));
        info.setContentType(mediaInfoJson.optString("media_type", null));
        info.setContentLength(mediaInfoJson.has("media_length") ? mediaInfoJson.getLong("media_length") : null);
        info.setCreatedTs(mediaInfoJson.has("created_ts") ? mediaInfoJson.getLong("created_ts") : null);

        return Optional.of(info);
    }
    return Optional.empty();
  }

  public HttpResponse<String> deleteMedia(String mediaId, String accessToken) throws IOException, InterruptedException {
    String serverUrl = PropertyManager.getProperty(MATRIX_SERVER_URL);
    String serverName = PropertyManager.getProperty(MATRIX_SERVER_NAME);
    String url = String.format("%s/_synapse/admin/v1/media/%s/%s", serverUrl, serverName, mediaId);
    return HTTPHelper.sendHttpDeleteRequest(url, accessToken, "");
  }

  /**
   * Tells whether a push rule's actions contain {@code notify}.
   *
   * @param actions the rule's actions, strings or objects
   * @return true when one action is the string {@code notify}
   */
  private boolean isNotifying(JSONArray actions) {
    for (int i = 0; actions != null && i < actions.length(); i++) {
      if (NOTIFY_ACTION.equals(actions.opt(i))) {
        return true;
      }
    }
    return false;
  }

  /**
   * Throws the exception a client API response calls for: a rejected token, or
   * any other non-2xx answer.
   *
   * @param response the response
   * @param operation the operation, for the message
   * @throws MatrixUnauthorizedException on a 401
   * @throws MatrixException on another non-2xx status
   */
  private void checkClientApiResponse(HttpResponse<String> response, String operation) {
    if (response.statusCode() == 401) {
      throw new MatrixUnauthorizedException("Access token rejected while " + operation);
    }
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new MatrixException("Error while %s, Matrix server returned HTTP %s".formatted(operation,
                                                                                         String.valueOf(response.statusCode())));
    }
  }

  /**
   * Builds the admin API url of a Matrix user, its id percent-encoded.
   *
   * @param matrixIdOfUser the Matrix id of the user
   * @return the url
   */
  private String userUrl(String matrixIdOfUser) {
    return PropertyManager.getProperty(MATRIX_SERVER_URL) + "/_synapse/admin/v2/users/"
        + URLEncoder.encode(matrixIdOfUser, StandardCharsets.UTF_8);
  }

  /**
   * Answers the body of a user read, or throws on a non-2xx status.
   *
   * @param response the response
   * @param matrixIdOfUser the Matrix id read, for the message
   * @return the user's details
   */
  private String parseUserResponse(HttpResponse<String> response, String matrixIdOfUser) {
    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      return response.body();
    } else {
      throw new RuntimeException("Error retrieving the details of the user %s ,Matrix server returned HTTP %s error %s".formatted(matrixIdOfUser,
                                                                                                                                  String.valueOf(response.statusCode()),
                                                                                                                                  response.body()));
    }
  }

  /**
   * Builds the client API url of one event, the room id and the event id
   * percent-encoded into the path.
   *
   * @param eventId the event ID
   * @param roomId the room ID
   * @return the url
   */
  private String eventUrl(String eventId, String roomId) {
    return PropertyManager.getProperty(MATRIX_SERVER_URL) + ROOMS_API_PATH + URLEncoder.encode(roomId, StandardCharsets.UTF_8)
        + "/event/" + URLEncoder.encode(eventId, StandardCharsets.UTF_8);
  }

  /**
   * Parses the answer of an event read.
   *
   * @param response the response
   * @param eventId the event ID read
   * @return the message, null on a 404
   * @throws JsonException when the response cannot be parsed
   */
  private MatrixMessage parseEventResponse(HttpResponse<String> response, String eventId) throws JsonException {
    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      JsonValue jsonMessage = new JsonGeneratorImpl().createJsonObjectFromString(response.body());
      MatrixMessage message = new MatrixMessage();
      message.setEventId(eventId);
      message.setRoomId(jsonMessage.getElement("room_id").getStringValue());
      message.setType(jsonMessage.getElement("type").getStringValue());
      message.setSender(jsonMessage.getElement("sender").getStringValue());
      message.setTimeStamp(Long.parseLong(jsonMessage.getElement("origin_server_ts").getStringValue()));
      if (jsonMessage.getElement("content") != null) {
        JsonValue content = jsonMessage.getElement("content");
        if(content.getElement("body") != null) {
          message.setMessageContent(content.getElement("body").getStringValue());
        }
        if (content.getElement("msgtype") != null) {
          message.setMessageType(content.getElement("msgtype").getStringValue());
        }
        JsonValue format = content.getElement("format");
        JsonValue formattedBody = content.getElement("formatted_body");
        if (format != null && HTML_MESSAGE_FORMAT.equals(format.getStringValue()) && formattedBody != null) {
          message.setFormattedContent(formattedBody.getStringValue());
        }
        readRelation(content, message);
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
   * Builds the client API url reading the latest events of a room,
   * newest first.
   *
   * @param matrixRoomId the room local part (without the server name suffix)
   * @param limit the maximum number of events to fetch
   * @return the url
   */
  private String roomMessagesUrl(String matrixRoomId, int limit) {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String fullRoomId = matrixRoomId + ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
    return PropertyManager.getProperty(MATRIX_SERVER_URL) + ROOMS_API_PATH + fullRoomId + "/messages?dir=b&limit=" + limit;
  }

  /**
   * Turns the response of a room messages read into the textual messages it
   * carries: an empty list when the user may not read the room (HTTP 403/404).
   *
   * @param response the client API response
   * @param matrixRoomId the room local part the events belong to
   * @return the {@code m.room.message} events with a body, in response order
   * @throws MatrixUnauthorizedException when the access token is rejected
   * @throws MatrixException on any other error status
   * @throws JsonException when the response cannot be parsed
   */
  private List<MatrixMessage> parseRoomMessagesResponse(HttpResponse<String> response,
                                                       String matrixRoomId) throws JsonException {
    if (response.statusCode() == 401) {
      throw new MatrixUnauthorizedException("Access token rejected while reading messages of room " + matrixRoomId);
    }
    if (response.statusCode() == 403 || response.statusCode() == 404) {
      // The user is not a member of the room or cannot see its history
      LOG.debug("User is not allowed to read messages of room {} (HTTP {})", matrixRoomId, response.statusCode());
      return new ArrayList<>();
    }
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new MatrixException("Error retrieving messages of room %s ,Matrix server returned HTTP %s error %s".formatted(matrixRoomId,
                                                                                                                          String.valueOf(response.statusCode()),
                                                                                                                          response.body()));
    }
    List<MatrixMessage> messages = new ArrayList<>();
    JsonValue chunk = new JsonGeneratorImpl().createJsonObjectFromString(response.body()).getElement("chunk");
    if (chunk == null || !chunk.isArray()) {
      return messages;
    }
    Iterator<JsonValue> events = chunk.getElements();
    while (events.hasNext()) {
      MatrixMessage message = parseMessageEvent(events.next(), matrixRoomId);
      if (message != null) {
        messages.add(message);
      }
    }
    return messages;
  }

  /**
   * Copies the {@code m.relates_to} of a message content into the message: the
   * relation type, the replied-to event, and for an {@code m.replace} edit the
   * replaced event and the new body of its {@code m.new_content}.
   *
   * @param content the message content
   * @param message the message parsed from that content, updated in place
   */
  private void readRelation(JsonValue content, MatrixMessage message) {
    JsonValue relatesTo = content.getElement("m.relates_to");
    if (relatesTo == null) {
      return;
    }
    JsonValue relationType = relatesTo.getElement("rel_type");
    if (relationType != null) {
      message.setRelatesToType(relationType.getStringValue());
    }
    JsonValue inReplyTo = relatesTo.getElement("m.in_reply_to");
    JsonValue repliedEventId = inReplyTo == null ? null : inReplyTo.getElement("event_id");
    if (repliedEventId != null) {
      message.setInReplyTo(repliedEventId.getStringValue());
    }
    JsonValue replacedEventId = relatesTo.getElement("event_id");
    if ("m.replace".equals(message.getRelatesToType()) && replacedEventId != null) {
      message.setReplacedEventId(replacedEventId.getStringValue());
      JsonValue newContent = content.getElement("m.new_content");
      JsonValue newBody = newContent == null ? null : newContent.getElement("body");
      if (newBody != null) {
        message.setEditedContent(newBody.getStringValue());
      }
    }
  }

  /**
   * Builds the client API url sending a message to a room.
   *
   * @param matrixRoomId the room local part (without the server name suffix)
   * @param transactionId the client transaction id
   * @return the url
   */
  private String sendMessageUrl(String matrixRoomId, String transactionId) {
    if (StringUtils.isBlank(PropertyManager.getProperty(MATRIX_SERVER_URL))) {
      throw new IllegalArgumentException(MATRIX_SERVER_URL_IS_REQUIRED);
    }
    String fullRoomId = matrixRoomId + ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
    return PropertyManager.getProperty(MATRIX_SERVER_URL) + ROOMS_API_PATH + fullRoomId + "/send/m.room.message/" + transactionId;
  }

  /**
   * Reads the event id out of the response of a message send.
   *
   * @param response the client API response
   * @param matrixRoomId the room the message was sent to
   * @return the created event id
   * @throws MatrixUnauthorizedException when the access token is rejected
   * @throws MatrixException on any other error status
   * @throws JsonException when the response cannot be parsed
   */
  private String parseSendResponse(HttpResponse<String> response, String matrixRoomId) throws JsonException {
    if (response.statusCode() == 401) {
      throw new MatrixUnauthorizedException("Access token rejected while sending a message to room " + matrixRoomId);
    }
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new MatrixException("Error sending a message to room %s ,Matrix server returned HTTP %s error %s".formatted(matrixRoomId,
                                                                                                                        String.valueOf(response.statusCode()),
                                                                                                                        response.body()));
    }
    return new JsonGeneratorImpl().createJsonObjectFromString(response.body()).getElement("event_id").getStringValue();
  }
}
