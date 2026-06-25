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
package io.meeds.chat.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Internal representation of a room with unread notifications returned by the
 * Matrix {@code /sync} call: the room local id, its unread count and the recent
 * timeline messages.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatrixUnreadRoom {

  /** Room local part (without the server name suffix). */
  private String roomId;

  /** Number of unread notifications in the room. */
  private int unreadCount;

  /** Recent timeline messages, oldest first. */
  private List<MatrixMessage> messages;

}
