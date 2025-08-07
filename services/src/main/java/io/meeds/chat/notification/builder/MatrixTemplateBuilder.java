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
package io.meeds.chat.notification.builder;

import io.meeds.chat.notification.mail.MailTemplateProvider;
import org.apache.commons.lang3.StringUtils;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.channel.template.AbstractTemplateBuilder;
import org.exoplatform.commons.api.notification.channel.template.TemplateProvider;
import org.exoplatform.commons.api.notification.model.MessageInfo;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.service.template.TemplateContext;
import org.exoplatform.commons.notification.template.TemplateUtils;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.commons.utils.TimeConvertUtils;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.notification.LinkProviderUtils;
import org.exoplatform.social.notification.Utils;
import org.exoplatform.social.notification.plugin.SocialNotificationUtils;

import java.io.Writer;
import java.util.Calendar;
import java.util.Locale;

public class MatrixTemplateBuilder extends AbstractTemplateBuilder {

  private final TemplateProvider templateProvider;

  public MatrixTemplateBuilder(MailTemplateProvider mailTemplateProvider) {
    this.templateProvider = mailTemplateProvider;
  }

  @Override
  protected MessageInfo makeMessage(NotificationContext notificationContext) {
    NotificationInfo notification = notificationContext.getNotificationInfo();
    String language = getLanguage(notification);
    String pluginId = notification.getKey().getId();
    TemplateContext templateContext =
                                    TemplateContext.newChannelInstance(this.templateProvider.getChannelKey(), pluginId, language);
    SocialNotificationUtils.addFooterAndFirstName(notification.getTo(), templateContext);
    Calendar cal = Calendar.getInstance();
    cal.setTimeInMillis(notification.getLastModifiedDate());
    templateContext.put("NOTIFICATION_ID", notification.getId());
    templateContext.put("LAST_UPDATED_TIME",
                        TimeConvertUtils.convertXTimeAgoByTimeServer(cal.getTime(),
                                                                     "EE, dd yyyy",
                                                                     Locale.of(language),
                                                                     TimeConvertUtils.YEAR));

    Identity senderIdentity = Utils.getIdentityManager()
                                   .getOrCreateIdentity(OrganizationIdentityProvider.NAME, notification.getFrom());
    templateContext.put("USER", senderIdentity.getProfile().getFullName());
    templateContext.put("ROOM", notification.getValueOwnerParameter("MATRIX_ROOM_NAME"));
    templateContext.put("ROOM_TYPE", notification.getValueOwnerParameter("MATRIX_ROOM_TYPE"));
    templateContext.put("MESSAGE_LINK", notification.getValueOwnerParameter("MATRIX_MESSAGE_URL"));
    templateContext.put("PROFILE_URL", LinkProviderUtils.getRedirectUrl("user", senderIdentity.getRemoteId()));
    if (StringUtils.isNotBlank(notification.getValueOwnerParameter("MATRIX_ROOM_AVATAR"))) {
      templateContext.put("AVATAR", CommonsUtils.getCurrentDomain() + notification.getValueOwnerParameter("MATRIX_ROOM_AVATAR"));
    }
    String message = notification.getValueOwnerParameter("MATRIX_MESSAGE_CONTENT");

    templateContext.put("MESSAGE_CONTENT", message);

    MessageInfo messageInfo = new MessageInfo();
    messageInfo.subject(TemplateUtils.processSubject(templateContext));
    messageInfo.body(TemplateUtils.processGroovy(templateContext));
    notificationContext.setException(templateContext.getException());
    return messageInfo.end();
  }

  @Override
  protected boolean makeDigest(NotificationContext notificationContext, Writer writer) {
    return false;
  }
}
