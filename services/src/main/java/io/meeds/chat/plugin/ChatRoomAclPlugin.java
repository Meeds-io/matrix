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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package io.meeds.chat.plugin;

import org.exoplatform.container.PortalContainer;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.services.security.Identity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.meeds.chat.model.Room;
import io.meeds.chat.service.MatrixService;
import io.meeds.portal.plugin.AclPlugin;

import jakarta.annotation.PostConstruct;

/**
 * ACL plugin making chat rooms eligible for the platform Favorites framework
 * (the same mechanism used by Notes, Activities and AI prompts). It registers
 * itself into {@link UserACL} so the generic {@code FavoriteService}/
 * {@code FavoriteRest} can authorize favoriting a chat room: the favorite's
 * {@code objectId} is the Matrix room id and a user may favorite a room only
 * when they can access it.
 */
@Component
public class ChatRoomAclPlugin implements AclPlugin {

  /** Favorite object type for chat rooms; the favorite objectId is the Matrix room id. */
  public static final String OBJECT_TYPE = "chatRoom";

  @Autowired
  private PortalContainer container;

  @Autowired
  private MatrixService    matrixService;

  @PostConstruct
  public void init() {
    UserACL userACL = container.getComponentInstanceOfType(UserACL.class);
    userACL.addAclPlugin(this);
  }

  @Override
  public String getObjectType() {
    return OBJECT_TYPE;
  }

  @Override
  public boolean hasPermission(String objectId, String permissionType, Identity identity) {
    if (objectId == null || identity == null) {
      return false;
    }
    Room room = matrixService.getById(objectId);
    return room != null && matrixService.canAccess(room, identity.getUserId());
  }

}
