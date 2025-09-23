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
package io.meeds.chat.notification.mail;

import io.meeds.chat.notification.builder.MatrixTemplateBuilder;
import org.exoplatform.commons.api.notification.annotation.TemplateConfig;
import org.exoplatform.commons.api.notification.annotation.TemplateConfigs;
import org.exoplatform.commons.api.notification.channel.template.TemplateProvider;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.resources.ResourceBundleService;

import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_MENTION_RECEIVED_NOTIFICATION_PLUGIN;

@TemplateConfigs(templates = {
    @TemplateConfig(pluginId = MATRIX_MENTION_RECEIVED_NOTIFICATION_PLUGIN, template = "war:/conf/matrix/templates/notification/mail/MentionReceivedMailPlugin.gtmpl") })
public class MailTemplateProvider extends TemplateProvider {

  public MailTemplateProvider(InitParams initParams, ResourceBundleService resourceBundleService) {
    super(initParams);
    this.templateBuilders.put(PluginKey.key(MATRIX_MENTION_RECEIVED_NOTIFICATION_PLUGIN),
                              new MatrixTemplateBuilder(this, resourceBundleService));
  }
}
