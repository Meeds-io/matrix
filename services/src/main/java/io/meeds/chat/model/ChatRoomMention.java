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
package io.meeds.chat.model;

import java.util.List;

import io.meeds.chat.constant.ChatRoomKind;

/**
 * A mention of a digital worker in a chat room, the data of the
 * {@code matrix.agent.mentioned} platform event (whose source is the room).
 * Every field is read back from the event by the administrator account, never
 * taken from the push, except the recipient, which the pusher's key names.
 *
 * @param roomKind the kind of room, {@link ChatRoomKind#SPACE} today
 * @param eventId the Matrix event id of the message
 * @param roomId the Matrix room id, as the push named it
 * @param spaceId the id of the room's space, null for a direct message
 * @param recipientLogin the login of the mentioned worker
 * @param senderLogin the login of the message's sender, null when the sender
 *          has no platform account
 * @param senderMatrixId the Matrix id of the message's sender
 * @param body the message's plain-text body
 * @param mentionedMatrixIds the Matrix ids the message's {@code m.mentions}
 *          names
 * @param originServerTs the message's {@code origin_server_ts}
 */
public record ChatRoomMention(ChatRoomKind roomKind,
                              String eventId,
                              String roomId,
                              Long spaceId,
                              String recipientLogin,
                              String senderLogin,
                              String senderMatrixId,
                              String body,
                              List<String> mentionedMatrixIds,
                              long originServerTs) {
}
