/*
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2025 Meeds Association contact@meeds.io
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package io.meeds.chat.notification.channel;

import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.channel.AbstractChannel;
import org.exoplatform.commons.api.notification.channel.template.AbstractTemplateBuilder;
import org.exoplatform.commons.api.notification.channel.template.TemplateProvider;
import org.exoplatform.commons.api.notification.model.ChannelKey;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

public class ChatPushChannel extends AbstractChannel {

  private static final Log LOG = ExoLogger.getLogger(ChatPushChannel.class);

  public static final String ID  = "CHAT_PUSH_CHANNEL";

  public final ChannelKey    key = ChannelKey.key(ID);

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public ChannelKey getKey() {
    return key;
  }

  @Override
  public void dispatch(NotificationContext notificationContext, String userName) {
    NotificationInfo notificationInfo = notificationContext.getNotificationInfo();
    LOG.info("Sending a Push message {} to {}", notificationInfo.getTitle(), userName);
  }

  @Override
  public void registerTemplateProvider(TemplateProvider templateProvider) {
    // No registration is needed
  }

  @Override
  public boolean isDefaultChannel() {
    return false;
  }

  @Override
  protected AbstractTemplateBuilder getTemplateBuilderInChannel(PluginKey pluginKey) {
    // No template builder is needed
    return null;
  }
}
