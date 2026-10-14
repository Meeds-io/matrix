/*
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package io.meeds.chat.mcp;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import io.meeds.chat.model.Room;
import io.meeds.chat.service.MatrixService;
import io.meeds.mcp.server.plugin.SpaceScopeResolver;

/**
 * Tells the MCP server which space a chat room belongs to, for the chat tools
 * that reach a room by its id ({@code get_chat_messages},
 * {@code send_chat_message}) while their call is limited to one space: a
 * digital worker's run in a space's room can then read that room, and no
 * other.
 * <p>
 * Only an enabled space room answers. A direct message, an unknown or
 * disabled room, a room found by prefix only, and any room that cannot be
 * read answer no space, so the call is refused. The room id is compared
 * exactly once both sides are normalised, since a room is stored either as
 * its local part or as its full id.
 */
@Service("chatRoomSpaceScopeResolver")
public class ChatRoomSpaceScopeResolver implements SpaceScopeResolver {

  /** The name the chat tools' {@code space_scope.resolver} gives. */
  public static final String NAME = "chat_room";

  private static final Log   LOG  = ExoLogger.getLogger(ChatRoomSpaceScopeResolver.class);

  @Autowired
  private MatrixService      matrixService;

  /**
   * Returns the resolver's name.
   *
   * @return {@value #NAME}
   */
  @Override
  public String getName() {
    return NAME;
  }

  /**
   * Tells the space of a room, never throwing.
   *
   * @param objectId the Matrix room id, with or without its server name
   * @return the room's space id, null when the room is not an enabled space
   *         room or cannot be read
   */
  @Override
  public Long spaceOf(String objectId) {
    if (StringUtils.isBlank(objectId)) {
      return null;
    }
    try {
      Room room = matrixService.getById(objectId);
      if (room == null || !matrixService.extractRoomId(room.getRoomId()).equals(matrixService.extractRoomId(objectId))) {
        return null;
      }
      // a direct message has no space
      return room.getSpaceId();
    } catch (Exception e) {
      LOG.debug("The space of the chat room {} could not be read", objectId, e);
      return null;
    }
  }
}
