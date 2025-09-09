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
package io.meeds.chat.notification.pwa;

import io.meeds.pwa.model.PwaNotificationMessage;
import io.meeds.pwa.plugin.PwaNotificationPlugin;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.services.resources.LocaleConfig;
import org.exoplatform.services.resources.ResourceBundleService;

import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_MENTION_RECEIVED_NOTIFICATION_PLUGIN;

public class MentionReceivedNotificationPwaPlugin implements PwaNotificationPlugin {

  private ResourceBundleService resourceBundleService;

  public MentionReceivedNotificationPwaPlugin(ResourceBundleService resourceBundleService) {
    this.resourceBundleService = resourceBundleService;
  }

  @Override
  public String getId() {
    return MATRIX_MENTION_RECEIVED_NOTIFICATION_PLUGIN;
  }

  @Override
  public PwaNotificationMessage process(NotificationInfo notification, LocaleConfig localeConfig) {
    PwaNotificationMessage notificationMessage = new PwaNotificationMessage();
    String roomType = notification.getValueOwnerParameter("MATRIX_ROOM_TYPE");
    String sender = notification.getValueOwnerParameter("MATRIX_SENDER_FULL_NAME");
    String roomName = notification.getValueOwnerParameter("MATRIX_ROOM_NAME");
    if ("SPACE".equals(roomType)) {
      notificationMessage.setTitle(resourceBundleService.getSharedString("Notification.body.space.MentionReceivedNotificationPlugin",
                                                                         localeConfig.getLocale())
                                                        .replace("{0}", sender)
                                                        .replace("{1}", roomName));
    } else {
      notificationMessage.setTitle(resourceBundleService.getSharedString("Notification.body.onetoone.MentionReceivedNotificationPlugin",
                                                                         localeConfig.getLocale())
                                                        .replace("{0}", sender));
    }
    notificationMessage.setBody(notification.getValueOwnerParameter("MATRIX_MESSAGE_CONTENT"));
    notificationMessage.setUrl(notification.getValueOwnerParameter("MATRIX_MESSAGE_URL"));
    return notificationMessage;
  }
}
