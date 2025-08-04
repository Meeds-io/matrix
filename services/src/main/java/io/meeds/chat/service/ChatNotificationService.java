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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import io.meeds.chat.model.MatrixMessage;
import io.meeds.chat.model.Room;
import io.meeds.pwa.model.PwaNotificationMessage;
import io.meeds.pwa.service.PwaNotificationService;
import io.meeds.social.util.JsonUtils;
import org.apache.commons.lang3.StringUtils;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.api.notification.model.UserSetting;
import org.exoplatform.commons.api.notification.service.setting.UserSettingService;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.resources.LocaleConfig;
import org.exoplatform.services.resources.ResourceBundleService;
import org.exoplatform.services.user.UserStateModel;
import org.exoplatform.services.user.UserStateService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
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
  private SettingService            settingService;

  private static UserStateService   userStateService;

  private static UserSettingService userSettingService;

  public static String              IN_KEY                       = "matrix.words.in";

  private static final String       USER_STATUS_AVAILABLE        = "available";

  public static final Scope        USER_CHAT_NOTIFICATION_SCOPE = Scope.APPLICATION.id("ChatNotificationSettings");

  public static final String       MUTED_ROOMS                  = "mutedRooms";

    /**
   * Sends a notification Creation request to the Push service on the browser
   * based on the event contents
   *
   * @param eventId
   * @param userName
   * @param roomId
   * @param unreadCount
   * @return
   */
  public ScheduledFuture<?> sendCreateNotificationAction(String eventId, String userName, String roomId, int unreadCount) {
    if (!isPushEnabledForUser(userName, roomId)) {
      return null;
    }
    HashMap<String, Object> params = new HashMap<>();
    String encodedId = URLEncoder.encode(eventId + "|" + roomId, StandardCharsets.UTF_8).replace("+", "%20");
    params.put(EVENT_NOTIFICATION_ID_PARAM_NAME, encodedId);
    params.put("username", userName);
    params.put(EVENT_ACTION_PARAM_NAME, "open");
    params.put(EVENT_NOTIFICATION_TYPE_PARAM_NAME, "CHAT_NOTIFICATION");
    return pwaNotificationService.create(params);

    //createMentionNotification(eventId, roomId, userName);
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
  public PwaNotificationMessage createNotification(String eventId, String roomId, String userName, String token) {
    MatrixMessage message = matrixService.getRoomEvent(eventId, roomId, token);
    return createNotification(message, userName);
  }

  /**
   * Creates a web/Mail notification for mentions in space Chat rooms
   * 
   * @param eventId the event ID
   * @param roomId the room ID
   */
  public void createMentionNotification(String eventId, String roomId, String userName) {
    Room room = matrixService.getById(roomId);
    MatrixMessage message = matrixService.getRoomEvent(eventId, roomId);
    Identity receiverIdentity = identityManager.getOrCreateUserIdentity(userName);
    String matrixReceiverId = userName;
    if (receiverIdentity != null) {
      matrixReceiverId = (String) receiverIdentity.getProfile().getProperties().get(USER_MATRIX_ID);
    }
    String roomName = "";
    String senderFullName = "";
    if (message != null && message.getMentionedUsers() != null && !message.getMentionedUsers().isEmpty()
        && message.getMentionedUsers().contains(matrixReceiverId) && room != null && StringUtils.isNotBlank(room.getSpaceId())) {
      Space space = spaceService.getSpaceById(room.getSpaceId());
      roomName = space.getDisplayName();
      Identity senderIdentity = matrixService.findSpaceMemberByMatrixId(message.getSender(), space);
      if (senderIdentity != null) {
        senderFullName = senderIdentity.getProfile().getFullName();
      }
      NotificationContext ctx = NotificationContextImpl.cloneInstance();
      ctx.append(MATRIX_ROOM_ID, message.getRoomId());
      ctx.append(MATRIX_ROOM_NAME, roomName);
      ctx.append(MATRIX_ROOM_MEMBER, userName);
      ctx.append(MATRIX_MESSAGE_SENDER_FULLNAME, senderFullName);
      ctx.getNotificationExecutor()
         .with(ctx.makeCommand(PluginKey.key(MATRIX_MENTION_RECEIVED_NOTIFICATION_PLUGIN)))
         .execute(ctx);
    }
  }

  public boolean isPrivateRoomMutedForUser(String userName, String roomId) {
    return getMutedRooms(userName).contains(roomId);
  }

  public void mutePrivateRoom(String userName, String roomId) {
    Set<String> mutedRoomIds = new HashSet<>(getMutedRooms(userName));
    if (mutedRoomIds.add(roomId)) {
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
