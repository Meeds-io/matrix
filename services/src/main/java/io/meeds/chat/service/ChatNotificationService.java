/*
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
package io.meeds.chat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import io.meeds.chat.model.MatrixMessage;
import io.meeds.chat.model.Room;
import io.meeds.portal.permlink.model.PermanentLinkObject;
import io.meeds.portal.permlink.service.PermanentLinkService;
import io.meeds.pwa.model.PwaNotificationMessage;
import io.meeds.pwa.service.PwaNotificationService;
import io.meeds.social.space.plugin.SpacePermanentLinkPlugin;
import io.meeds.social.util.JsonUtils;
import org.apache.commons.lang3.StringUtils;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.portal.config.UserPortalConfigService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.resources.LocaleConfig;
import org.exoplatform.services.resources.ResourceBundleService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.ws.frameworks.json.impl.JsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.concurrent.ScheduledFuture;

import static io.meeds.chat.service.utils.MatrixConstants.*;
import static io.meeds.pwa.service.PwaNotificationService.*;

@Service
public class ChatNotificationService {
  private static final Log          LOG                          = ExoLogger.getLogger(ChatNotificationService.class);

  @Autowired
  private MatrixService             matrixService;

  @Autowired
  private IdentityManager           identityManager;

  @Autowired
  private SpaceService              spaceService;

  @Autowired
  ResourceBundleService             resourceBundleService;

  @Autowired
  private PwaNotificationService    pwaNotificationService;

  @Autowired
  private PermanentLinkService      permanentLinkService;

  @Autowired
  private UserPortalConfigService   portalConfigService;

  private static UserStateService   userStateService;

  private static UserSettingService userSettingService;

  public static String              IN_KEY                       = "matrix.words.in";

  private static final String       USER_STATUS_AVAILABLE        = "available";

  public static final Scope         USER_CHAT_NOTIFICATION_SCOPE = Scope.APPLICATION.id("ChatNotificationSettings");

  public static final String        MUTED_ROOMS                  = "mutedRooms";

  /**
   * Sends a notification Creation request to the Push service on the browser
   * based on the event contents
   *
   * @param eventId the event ID
   * @param userName the user name
   * @param roomId the room ID
   * @param unreadCount the number of unread messages
   * @return thread to perform notification action
   */
  public ScheduledFuture<?> sendCreateNotificationAction(String eventId, String userName, String roomId, int unreadCount) {
    if (!isPushEnabledForUser(userName, roomId)) {
      return null;
    }
    // Create Push notification
    HashMap<String, Object> params = new HashMap<>();
    String encodedId = URLEncoder.encode(eventId + "|" + roomId, StandardCharsets.UTF_8).replace("+", "%20");
    params.put(EVENT_NOTIFICATION_ID_PARAM_NAME, encodedId);
    params.put("username", userName);
    params.put(EVENT_ACTION_PARAM_NAME, "open");
    params.put(EVENT_NOTIFICATION_TYPE_PARAM_NAME, "CHAT_NOTIFICATION");
    return pwaNotificationService.create(params);
  }

  /**
   * Creates a notification based on the received message
   * 
   * @param message the received message
   * @param userName the user who will receive the notification
   * @return a PWA push notification object
   */
  public PwaNotificationMessage createNotification(MatrixMessage message, String userName) {
    if (message != null) {
      PwaNotificationMessage pwaNotificationMessage = new PwaNotificationMessage();
      Room room = matrixService.getById(message.getRoomId());

      LocaleConfig localeConfig = pwaNotificationService.getLocaleConfig(userName);
      String sender = message.getSender();

      if (room != null) {
        if (StringUtils.isBlank(room.getSpaceId())) {
          String senderUserName = room.getFirstParticipant().equals(userName) ? room.getSecondParticipant()
                                                                              : room.getFirstParticipant();
          Identity senderIdentity = identityManager.getOrCreateUserIdentity(senderUserName);
          String senderFullName = senderIdentity != null ? senderIdentity.getProfile().getFullName() : sender;
          pwaNotificationMessage.setTitle(senderFullName);
          pwaNotificationMessage.setIcon(senderIdentity != null ? senderIdentity.getProfile().getAvatarUrl() : "");
        } else {
          Space space = spaceService.getSpaceById(room.getSpaceId());
          Identity senderIdentity = matrixService.findSpaceMemberByMatrixId(sender, space);
          String senderFullName = senderIdentity != null ? senderIdentity.getProfile().getFullName() : sender;
          pwaNotificationMessage.setTitle(senderFullName + " "
              + resourceBundleService.getSharedString(IN_KEY, localeConfig.getLocale()) + " " + space.getDisplayName());
          pwaNotificationMessage.setIcon(space.getAvatarUrl());
        }

        pwaNotificationMessage.setBody(message.getMessageContent());
        pwaNotificationMessage.setUrl(getMessageLink(message));
        pwaNotificationService.setDefaultNotificationMessageProperties(pwaNotificationMessage,
                                                                       message.getEventId(),
                                                                       localeConfig);

        return pwaNotificationMessage;
      } else {
        return null;
      }
    } else {
      return null;
    }
  }

  /**
   * Creates a PWA notification based on the event details
   *
   * @param eventId the event Id
   * @param roomId the room ID
   * @param userName the user who received the notification
   * @return notification object
   */
  public PwaNotificationMessage createNotification(String eventId, String roomId, String userName, long lastMessageTimeStamp, String token) {
    MatrixMessage message = matrixService.getRoomEvent(eventId, roomId, token);
    // Do not create a notification is the message is before the last message
    if (lastMessageTimeStamp >= message.getTimeStamp()) {
      return null;
    }
    return createNotification(message, userName);
  }

  /**
   * Creates a web/Mail notification for mentions in space Chat rooms
   * 
   * @param eventId the event ID
   * @param roomId the room ID
   * @param userName the username of the receiver of the notification
   * @param pushKey jwt token of one of the users in case the room is private
   */
  public boolean createMentionNotification(String eventId, String roomId, String userName, String pushKey) {
    Room room = matrixService.getById(roomId);
    MatrixMessage message;
    if (room == null) {
      return false;
    }
    if (StringUtils.isNotBlank(room.getSpaceId())) {
      message = matrixService.getRoomEvent(eventId, roomId, null);
    } else {
      String accessToken = null;
      try {
        accessToken = matrixService.getAccessToken(pushKey);
      } catch (JsonException | IOException e) {
        LOG.error("Could not get Matrix Access token for the administrator account !", e);
      } catch (InterruptedException interruptedException) {
        Thread.currentThread().interrupt();
        LOG.error("Could not get Matrix Access token for the administrator account !", interruptedException);
      }
      if (StringUtils.isBlank(accessToken)) {
        return false;
      }
      message = matrixService.getRoomEvent(eventId, roomId, accessToken);
      // Invalidate the access token
      matrixService.invalidateAccessToken(accessToken);
    }
    Identity receiverIdentity = identityManager.getOrCreateUserIdentity(userName);
    String matrixReceiverId = userName;
    if (receiverIdentity != null
        && StringUtils.isNotBlank((String) receiverIdentity.getProfile().getProperties().get(USER_MATRIX_ID))) {
      matrixReceiverId = matrixService.getUserFullMatrixID((String) receiverIdentity.getProfile()
                                                                                    .getProperties()
                                                                                    .get(USER_MATRIX_ID));
    }
    String roomName = "";
    String senderFullName = "";
    String roomAvatarUrl = "";
    if (message != null && message.getMentionedUsers() != null && !message.getMentionedUsers().isEmpty()
        && message.getMentionedUsers().contains(matrixReceiverId)) {
      Identity senderIdentity = identityManager.getOrCreateUserIdentity(matrixService.findUserByMatrixId(message.getSender()));
      if (senderIdentity != null) {
        senderFullName = senderIdentity.getProfile().getFullName();
      }
      if (StringUtils.isNotBlank(room.getSpaceId())) {
        Space space = spaceService.getSpaceById(room.getSpaceId());
        roomName = space.getDisplayName();
        roomAvatarUrl = space.getAvatarUrl();
      } else if (senderIdentity != null) {
        roomName = senderIdentity.getProfile().getFullName();
        roomAvatarUrl = senderIdentity.getProfile().getAvatarUrl();
      } else {
        roomName = message.getSender();
      }

      NotificationContext ctx = NotificationContextImpl.cloneInstance();
      ctx.append(MATRIX_ROOM_ID, message.getRoomId());
      ctx.append(MATRIX_MESSAGE_SENDER, matrixService.findUserByMatrixId(message.getSender()));
      ctx.append(MATRIX_ROOM_NAME, roomName);
      ctx.append(MATRIX_ROOM_TYPE, StringUtils.isNotBlank(room.getSpaceId()) ? "SPACE" : "ONE_TO_ONE");
      ctx.append(MATRIX_ROOM_AVATAR, roomAvatarUrl);
      ctx.append(MATRIX_MESSAGE_CONTENT, message.getMessageContent());
      ctx.append(MATRIX_ROOM_MEMBER, userName);
      ctx.append(MATRIX_MESSAGE_SENDER_FULLNAME, senderFullName);
      String permalink = getMessageLink(message);
      ctx.append(MATRIX_MESSAGE_URL, StringUtils.isNotBlank(permalink) ? permalink : "");
      return ctx.getNotificationExecutor()
                .with(ctx.makeCommand(PluginKey.key(MATRIX_MENTION_RECEIVED_NOTIFICATION_PLUGIN)))
                .execute(ctx);
    }
    return false;
  }

  public boolean isPrivateRoomMutedForUser(String userName, String roomId) {
    return getMutedRooms(userName).contains(roomId);
  }

  public void toggleMutePrivateRoom(String userName, String roomId) {
    Set<String> mutedRoomIds = new HashSet<>(getMutedRooms(userName));
    boolean changed;
    changed = mutedRoomIds.remove(roomId);
    if (!changed) {
      changed = mutedRoomIds.add(roomId);
    }
    if (changed) {
      settingService.set(Context.USER.id(userName),
                         USER_CHAT_NOTIFICATION_SCOPE,
                         MUTED_ROOMS,
                         SettingValue.create(JsonUtils.toJsonString(mutedRoomIds)));
    }
  }

  private Set<String> getMutedRooms(String userName) {
    try {
      SettingValue<?> settingValue = settingService.get(Context.USER.id(userName), USER_CHAT_NOTIFICATION_SCOPE, MUTED_ROOMS);
      if (settingValue == null || settingValue.getValue() == null) {
        return Collections.emptySet();
      }
      return JsonUtils.OBJECT_MAPPER.readValue(settingValue.getValue().toString(), new TypeReference<>() {
      });
    } catch (Exception e) {
      LOG.error("Error reading muted rooms setting value for user {}", userName, e);
      return Collections.emptySet();
    }
  }

  private String getMessageLink(MatrixMessage message) {
    String urlFormat = "%s?roomId=%s&message=%s";
    Room room = matrixService.getById(message.getRoomId());
    String link = "";
    try {
      if (StringUtils.isNotBlank(room.getSpaceId())) {
        link = permanentLinkService.getLink(new PermanentLinkObject(SpacePermanentLinkPlugin.OBJECT_TYPE, room.getSpaceId()));
        return urlFormat.formatted(link, room.getRoomId(), message.getEventId());
      } else {
        String sender;
        sender = matrixService.findUserByMatrixId(message.getSender());
        link = String.format("/portal/%s/profile/%s", this.portalConfigService.getMetaPortal(), sender);
      }
    } catch (Exception e) {
      link = String.format("/portal/%s", portalConfigService.getMetaPortal());
    }

    return urlFormat.formatted(link, room.getRoomId(), message.getEventId());
  }

  private boolean isPushEnabledForUser(String userName, String roomId) {
    Room room = matrixService.getById(roomId);
    if (room == null) {
      return false;
    }
    boolean roomMuted;
    if (StringUtils.isNotBlank(room.getSpaceId())) {
      UserSetting userSetting = getUserSettingService().get(userName);
      roomMuted = userSetting != null && userSetting.isSpaceMuted(Long.parseLong(room.getSpaceId()));
    } else {
      roomMuted = isPrivateRoomMutedForUser(userName, roomId);
    }
    UserStateModel userStatus = getUserStateService().getUserState(userName);
    return userStatus.getStatus().equals(USER_STATUS_AVAILABLE) && !roomMuted;
  }

  private static UserStateService getUserStateService() {
    if (userStateService == null) {
      userStateService = CommonsUtils.getService(UserStateService.class);
    }
    return userStateService;
  }

  private static UserSettingService getUserSettingService() {
    if (userSettingService == null) {
      userSettingService = CommonsUtils.getService(UserSettingService.class);
    }
    return userSettingService;
  }
}
