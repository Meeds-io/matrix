/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2026 Meeds Association contact@meeds.io
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
package io.meeds.chat.plugin;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.meeds.appcenter.plugin.ApplicationBadgePlugin;
import io.meeds.appcenter.service.ApplicationBadgePluginRegistry;

import jakarta.annotation.PostConstruct;

/**
 * Declares the chat badge and binds it to the Chat application, without
 * computing it.
 * <p>
 * Chat is the one contributor whose counter is <strong>authoritative in the
 * browser</strong>: the Matrix client maintains it from its own sync state, and
 * the topbar chat button renders it directly. Two consequences:
 * <ul>
 * <li>There is no cheap server-side count to expose.
 * {@code MatrixService.getUnreadConversations} does exist but performs a
 * {@code callAsUser}, a conversation-title resolution and a full Matrix
 * {@code /sync} per call — built for an on-demand MCP tool, not for a counter
 * rendered on the topbar of every page.</li>
 * <li>Even if it were cheap, it would be a <em>second</em> source for a number
 * already displayed next to it, and the two could disagree.</li>
 * </ul>
 * So the value is supplied client-side through the {@code AppCenterAppBadge}
 * rendering extension, reusing the very count the chat button shows, and this
 * plugin only carries the two things the frontend cannot know: the badge
 * identifier and which catalog entry it belongs to.
 */
@Component
public class ChatApplicationBadgePlugin implements ApplicationBadgePlugin {

  public static final String             BADGE_NAME = "chatUnread";

  /**
   * Optional on purpose: the badge is a nicety, not something chat depends on.
   * When the Application Center registry is absent — as in this module's own
   * Spring test context — the plugin simply does not register instead of
   * failing the whole context.
   */
  @Autowired(required = false)
  private ApplicationBadgePluginRegistry applicationBadgePluginRegistry;

  /**
   * The drawer this badge belongs to, matching the {@code QuickAction} the chat
   * registers and the entry shipped in this addon's {@code applications.json}.
   */
  @Value("${matrix.badge.drawerNames:chat}")
  private List<String>                   drawerNames;

  @PostConstruct
  public void init() {
    if (applicationBadgePluginRegistry != null) {
      applicationBadgePluginRegistry.addPlugin(this);
    }
  }

  @Override
  public String getName() {
    return BADGE_NAME;
  }

  @Override
  public List<String> getDrawerNames() {
    return drawerNames;
  }

  /**
   * Nothing is cached: there is no server-side value to cache. Declaring this
   * self-cached keeps App Center from storing — and later serving — the
   * placeholder returned by {@link #countBadge(String)}.
   */
  @Override
  public boolean isSelfCached() {
    return true;
  }

  /**
   * Always 0: the real count is rendered by the frontend extension. The badge is
   * still displayed, because a badge carrying a registered rendering extension
   * owns its own visibility rather than depending on this value.
   */
  @Override
  public long countBadge(String username) {
    return 0;
  }

}
