/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2026 Meeds Association contact@meeds.io
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.api.notification.plugin.NotificationPluginUtils;
import org.exoplatform.commons.api.notification.service.template.TemplateContext;
import org.exoplatform.commons.notification.mail.MailPalette;
import org.exoplatform.commons.notification.template.TemplateUtils;
import org.exoplatform.commons.utils.TimeConvertUtils;
import org.exoplatform.services.resources.ResourceBundleService;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.notification.LinkProviderUtils;
import org.exoplatform.social.notification.Utils;
import org.exoplatform.social.notification.plugin.SocialNotificationUtils;

import io.meeds.chat.notification.mail.MailTemplateProvider;

/**
 * The message a mention mail quotes is the sender's content: its black and white
 * text is given the recipient's text colour.
 */
class MatrixTemplateBuilderTest {

  private static final String BLACK_TEXT = "<span style=\"color:#000000\">hi</span>";

  @Test
  void theQuotedMessageIsGivenTheRecipientsTextColour() {
    assertEquals("<span>hi</span>", renderedMessageContent("dark"));
  }

  @Test
  void theQuotedMessageIsLeftAsItIsInLight() {
    assertEquals(BLACK_TEXT, renderedMessageContent("light"));
  }

  private static String renderedMessageContent(String mode) {
    NotificationInfo notification = NotificationInfo.instance()
                                                    .to("ben")
                                                    .setFrom("anne")
                                                    .key(PluginKey.key("MatrixMentionReceivedNotificationPlugin"))
                                                    .with("MATRIX_ROOM_TYPE", "SPACE")
                                                    .with("MATRIX_ROOM_NAME", "Room")
                                                    .with("MATRIX_MESSAGE_CONTENT", BLACK_TEXT);
    NotificationContext ctx = mock(NotificationContext.class);
    when(ctx.getNotificationInfo()).thenReturn(notification);
    IdentityManager identityManager = mock(IdentityManager.class);
    Identity sender = mock(Identity.class);
    Profile profile = mock(Profile.class);
    when(profile.getFullName()).thenReturn("Anne");
    when(sender.getProfile()).thenReturn(profile);
    when(sender.getRemoteId()).thenReturn("anne");
    when(identityManager.getOrCreateIdentity(any(), any())).thenReturn(sender);
    ResourceBundleService bundles = mock(ResourceBundleService.class);
    when(bundles.getSharedString(anyString(), any())).thenReturn("subject $USER $ROOM");
    try (MockedStatic<NotificationPluginUtils> pluginUtils = mockStatic(NotificationPluginUtils.class);
        MockedStatic<SocialNotificationUtils> socialUtils = mockStatic(SocialNotificationUtils.class, CALLS_REAL_METHODS);
        MockedStatic<TimeConvertUtils> timeUtils = mockStatic(TimeConvertUtils.class);
        MockedStatic<Utils> utils = mockStatic(Utils.class);
        MockedStatic<LinkProviderUtils> links = mockStatic(LinkProviderUtils.class);
        MockedStatic<TemplateUtils> templateUtils = mockStatic(TemplateUtils.class)) {
      pluginUtils.when(() -> NotificationPluginUtils.getLanguage("ben")).thenReturn("en");
      // the palette the real addFooterAndFirstName binds, reduced to the recipient's mode
      socialUtils.when(() -> SocialNotificationUtils.addFooterAndFirstName(anyString(), any(TemplateContext.class)))
                 .thenAnswer(invocation -> {
                   invocation.<TemplateContext> getArgument(1).put(MailPalette.CONTEXT_KEY, Map.of(MailPalette.MODE, mode));
                   return null;
                 });
      timeUtils.when(() -> TimeConvertUtils.convertXTimeAgoByTimeServer(any(), anyString(), any(), anyInt())).thenReturn("now");
      utils.when(Utils::getIdentityManager).thenReturn(identityManager);
      links.when(() -> LinkProviderUtils.getRedirectUrl(anyString(), anyString())).thenReturn("/profile");
      templateUtils.when(() -> TemplateUtils.processSubject(any())).thenReturn("subject");
      templateUtils.when(() -> TemplateUtils.processGroovy(any())).thenReturn("body");

      new MatrixTemplateBuilder(mock(MailTemplateProvider.class), bundles).makeMessage(ctx);

      ArgumentCaptor<TemplateContext> rendered = ArgumentCaptor.forClass(TemplateContext.class);
      templateUtils.verify(() -> TemplateUtils.processGroovy(rendered.capture()));
      return (String) rendered.getValue().get("MESSAGE_CONTENT");
    }
  }
}
