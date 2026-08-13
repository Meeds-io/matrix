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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import io.meeds.appcenter.service.ApplicationBadgePluginRegistry;

/**
 * Binding-only plugin: the unread count is authoritative in the browser, so
 * what this class contributes is the identifier and the catalog binding. These
 * are exactly the values the frontend extension and App Center match on, so
 * they are pinned here.
 */
@ExtendWith(MockitoExtension.class)
class ChatApplicationBadgePluginTest {

  @Mock
  private ApplicationBadgePluginRegistry registry;

  @InjectMocks
  private ChatApplicationBadgePlugin     plugin;

  @Test
  void nameIsTheIdentifierTheRenderingExtensionDeclares() {
    // The AppCenterAppBadge extension matches on this exact value
    assertEquals("chatUnread", plugin.getName());
  }

  @Test
  void countIsAPlaceholderBecauseTheRealOneLivesInTheBrowser() {
    assertEquals(0L, plugin.countBadge("testuser"));
  }

  @Test
  void isSelfCachedSoAppCenterNeverStoresThePlaceholder() {
    // Without this, App Center would cache the 0 above and serve it as a count
    assertTrue(plugin.isSelfCached());
  }

  @Test
  void bindsToTheChatDrawer() {
    ReflectionTestUtils.setField(plugin, "drawerNames", List.of("chat"));

    assertTrue(plugin.getDrawerNames().contains("chat"));
  }

  @Test
  void registersItselfWhenTheRegistryIsPresent() {
    plugin.init();

    verify(registry).addPlugin(plugin);
  }

  @Test
  void startsWithoutTheApplicationCenterRegistry() {
    ReflectionTestUtils.setField(plugin, "applicationBadgePluginRegistry", null);

    // Matrix must boot, and its own Spring test context must load, with no
    // Application Center around
    assertDoesNotThrow(() -> plugin.init());
  }

}
