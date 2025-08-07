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
package io.meeds.chat.notification;

import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.plugin.BaseNotificationPlugin;
import org.exoplatform.container.xml.InitParams;

import static io.meeds.chat.service.utils.MatrixConstants.*;

public class MentionReceivedNotificationPlugin extends BaseNotificationPlugin {
  public MentionReceivedNotificationPlugin(InitParams initParams) {
    super(initParams);
  }

  @Override
  public String getId() {
    return MATRIX_MENTION_RECEIVED_NOTIFICATION_PLUGIN;
  }

  @Override
  public boolean isValid(NotificationContext notificationContext) {
    return true;
  }

  @Override
  protected NotificationInfo makeNotification(NotificationContext notificationContext) {
    String sender = notificationContext.value(MATRIX_MESSAGE_SENDER);
    String roomId = notificationContext.value(MATRIX_ROOM_ID);
    String roomName = notificationContext.value(MATRIX_ROOM_NAME);
    String roomType = notificationContext.value(MATRIX_ROOM_TYPE);
    String userName = notificationContext.value(MATRIX_ROOM_MEMBER);
    String senderFullName = notificationContext.value(MATRIX_MESSAGE_SENDER_FULLNAME);
    String avatarUrl = notificationContext.value(MATRIX_ROOM_AVATAR);
    String messageUrl = notificationContext.value(MATRIX_MESSAGE_URL);
    String messageContent = notificationContext.value(MATRIX_MESSAGE_CONTENT);
    return NotificationInfo.instance()
                           .setFrom(sender)
                           .to(userName)
                           .with("ROOM_ID", roomId)
                           .with("MATRIX_ROOM_NAME", roomName)
                           .with("MATRIX_ROOM_TYPE", roomType)
                           .with("MATRIX_SENDER_FULL_NAME", senderFullName)
                           .with("MATRIX_ROOM_AVATAR", avatarUrl)
                           .with("MATRIX_MESSAGE_URL", messageUrl)
                           .with("MATRIX_MESSAGE_CONTENT", messageContent)
                           .key(getId())
                           .end();
  }
}
