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
package io.meeds.chat.notification;

import io.meeds.portal.permlink.model.PermanentLinkObject;
import io.meeds.portal.permlink.service.PermanentLinkService;
import io.meeds.pwa.model.PwaNotificationMessage;
import io.meeds.pwa.plugin.PwaNotificationPlugin;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.services.resources.LocaleConfig;
import org.exoplatform.services.resources.ResourceBundleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_MESSAGE_RECEIVED_NOTIFICATION_PLUGIN;
import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_ROOM_ID;

@Component
public class MessageReceivedPWAPlugin implements PwaNotificationPlugin {

  private ResourceBundleService resourceBundleService;

  private PermanentLinkService  permanentLinkService;

  private static final String   TITLE_LABEL_KEY = "matrix.message.received.pwa.notification.title";

  public MessageReceivedPWAPlugin(ResourceBundleService resourceBundleService, PermanentLinkService permanentLinkService) {
    this.resourceBundleService = resourceBundleService;
    this.permanentLinkService = permanentLinkService;
  }

  @Override
  public PwaNotificationMessage process(NotificationInfo notification, LocaleConfig localeConfig) {
    PwaNotificationMessage notificationMessage = new PwaNotificationMessage();

    String title = resourceBundleService.getSharedString(TITLE_LABEL_KEY, localeConfig.getLocale())
                                        .replace("{0}", notification.getValueOwnerParameter("MATRIX_ROOM_UNREAD_COUNT"))
                                        .replace("{1}", notification.getValueOwnerParameter("MATRIX_ROOM_ID"));

    notificationMessage.setTitle(title);
    notificationMessage.setUrl(permanentLinkService.getPermanentLink(new PermanentLinkObject("matrixChatRoom",
                                                                                             notification.getValueOwnerParameter("MATRIX_ROOM_ID"))));
    return notificationMessage;

  }

  @Override
  public String getId() {
    return MATRIX_MESSAGE_RECEIVED_NOTIFICATION_PLUGIN;
  }
}
