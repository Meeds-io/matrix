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
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.services.resources.LocaleConfig;
import org.exoplatform.services.resources.ResourceBundleService;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Locale;

import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_MESSAGE_RECEIVED_NOTIFICATION_PLUGIN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessageReceivedPWAPluginTest {

  private ResourceBundleService resourceBundleService = mock(ResourceBundleService.class);

  private PermanentLinkService  permanentLinkService  = mock(PermanentLinkService.class);

  @Test
  void process() {
    String roomId = "thisIsRoomId";
    MessageReceivedPWAPlugin messageReceivedPWAPlugin = new MessageReceivedPWAPlugin(resourceBundleService, permanentLinkService);
    NotificationInfo notificationInfo = NotificationInfo.instance()
                                                        .to("root")
                                                        .with("MATRIX_ROOM_ID", roomId)
                                                        .with("MATRIX_ROOM_UNREAD_COUNT", "10")
                                                        .key(MATRIX_MESSAGE_RECEIVED_NOTIFICATION_PLUGIN)
                                                        .end();
    LocaleConfig localeConfig = mock(LocaleConfig.class);
    when(localeConfig.getLocale()).thenReturn(Locale.ENGLISH);
    when(resourceBundleService.getSharedString(anyString(), any(Locale.class))).thenReturn("There are {0} unread messages in room {1}");
    when(permanentLinkService.getPermanentLink(any(PermanentLinkObject.class))).thenReturn("http://exo.com/portal/dw/matrixRoomId=" + roomId);

    PwaNotificationMessage pwaNotificationMessage = messageReceivedPWAPlugin.process(notificationInfo, localeConfig);
    assertNotNull(pwaNotificationMessage);
    assertEquals("There are 10 unread messages in room thisIsRoomId", pwaNotificationMessage.getTitle());
    assertEquals("http://exo.com/portal/dw/matrixRoomId=" + roomId, pwaNotificationMessage.getUrl());
  }
}
