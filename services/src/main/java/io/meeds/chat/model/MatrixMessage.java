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

package io.meeds.chat.model;

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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MatrixMessage {
  private String       eventId;

  private String       roomId;

  private String       type;

  private String       messageContent;

  private String       messageType;

  private String       sender;

  private List<String> mentionedUsers;

  private long         timeStamp;

  /**
   * The HTML {@code formatted_body} of a message whose content declares the
   * {@code org.matrix.custom.html} format, as read by
   * {@code MatrixHttpClient#getEventById}; null otherwise, and null in the
   * messages the room and search reads build. The plain-text {@code body}
   * stays in {@code messageContent}.
   */
  private String       formattedContent;

  /**
   * The {@code rel_type} of the content's {@code m.relates_to}, as read by
   * {@code MatrixHttpClient#getEventById}: {@code m.replace} for an edit;
   * null when the event relates to nothing or only replies.
   */
  private String       relatesToType;

  /**
   * The event id the content's {@code m.relates_to.m.in_reply_to} names, as
   * read by {@code MatrixHttpClient#getEventById}; null when the event is not
   * a reply.
   */
  private String       inReplyTo;

  /**
   * The event id the content's {@code m.relates_to} names when its
   * {@code rel_type} is {@code m.replace}, as read by the room read; null for
   * any other event.
   */
  private String       replacedEventId;

  /**
   * The {@code body} of the content's {@code m.new_content} on an
   * {@code m.replace} event, as read by the room read; null when the event
   * carries none (older clients), the replaced text then being the event's
   * own body without its {@code "* "} marker.
   */
  private String       editedContent;

  /**
   * Builds a message without the edit fields, as every reader other than the
   * room read does.
   *
   * @param eventId the event id
   * @param roomId the room id
   * @param type the event type
   * @param messageContent the plain text body
   * @param messageType the message type ({@code msgtype})
   * @param sender the sender's Matrix id
   * @param mentionedUsers the mentioned Matrix ids
   * @param timeStamp the origin server timestamp
   * @param formattedContent the HTML body, if any
   * @param relatesToType the relation type, if any
   * @param inReplyTo the replied-to event id, if any
   */
  public MatrixMessage(String eventId,
                       String roomId,
                       String type,
                       String messageContent,
                       String messageType,
                       String sender,
                       List<String> mentionedUsers,
                       long timeStamp,
                       String formattedContent,
                       String relatesToType,
                       String inReplyTo) {
    this(eventId,
         roomId,
         type,
         messageContent,
         messageType,
         sender,
         mentionedUsers,
         timeStamp,
         formattedContent,
         relatesToType,
         inReplyTo,
         null,
         null);
  }
}
