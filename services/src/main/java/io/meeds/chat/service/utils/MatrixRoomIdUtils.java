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
package io.meeds.chat.service.utils;

import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_SERVER_NAME;

import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.utils.PropertyManager;

/**
 * Room identifiers come in two shapes. Up to room version 11 a room id is
 * {@code !<localpart>:<server_name>}, and the space rooms this addon created
 * are stored with the {@code :<server_name>} part removed. From room version
 * 12 (MSC4291) the room id is the create event's reference hash,
 * {@code !<43 URL-safe base64 characters>}, with no server part at all, so it
 * is already complete and is used as is.
 */
public final class MatrixRoomIdUtils {

  private static final Pattern HASH_ROOM_ID_PATTERN = Pattern.compile("^![A-Za-z0-9_-]{43}$");

  private MatrixRoomIdUtils() {
  }

  /**
   * @param roomId a room id as stored or as received from Synapse
   * @return true when the id is a room version 12 (MSC4291) id, which carries
   *         no server part
   */
  public static boolean isHashRoomId(String roomId) {
    return roomId != null && HASH_ROOM_ID_PATTERN.matcher(roomId).matches();
  }

  /**
   * @param roomId a room id as stored: complete
   *          ({@code !<localpart>:<server_name>} or a room version 12 id), or
   *          a legacy id stored without its server part
   * @return the room id to send to Synapse
   */
  public static String toFullRoomId(String roomId) {
    if (StringUtils.isBlank(roomId) || roomId.contains(":") || isHashRoomId(roomId)) {
      return roomId;
    }
    return roomId + ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
  }

  /**
   * @param fullRoomId the room id returned by Synapse
   * @return the id to store: without the {@code :<server_name>} part when it
   *         ends with this server's name, unchanged otherwise (a room version
   *         12 id has no server part)
   */
  public static String toStoredRoomId(String fullRoomId) {
    String serverSuffix = ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
    if (fullRoomId != null && fullRoomId.endsWith(serverSuffix)) {
      return fullRoomId.substring(0, fullRoomId.length() - serverSuffix.length());
    }
    return fullRoomId;
  }
}
