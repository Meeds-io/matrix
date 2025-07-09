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
package io.meeds.chat.service.utils;

import org.exoplatform.commons.api.notification.model.ArgumentLiteral;
import org.exoplatform.commons.api.notification.model.PluginKey;

public class MatrixConstants {

  public static final String YOU_STRING                  = "matrix.words.you";

  public static final String LAST_MESSAGE_PATTERN_STRING = "matrix.chat.lastMessage.pattern";

  private MatrixConstants() {
  }

  // Meeds server configurations

  public static final String                   MATRIX_SERVER_URL                           = "meeds.matrix.server.url";

  public static final String                   MATRIX_ADMIN_USERNAME                       = "meeds.matrix.user.name";

  public static final String                   MATRIX_ADMIN_DISPLAY_NAME                   = "meeds.matrix.user.display.name";

  public static final String                   MATRIX_SERVER_NAME                          = "meeds.matrix.server.name";

  public static final String                   MATRIX_RESTRICTED_USERS_GROUP               =
                                                                             "meeds.matrix.restricted.users.groupId";

  public static final String                   SHARED_SECRET_REGISTRATION                  =
                                                                          "meeds.matrix.shared_secret_registration";

  public static final String                   MATRIX_SERVER_URL_IS_REQUIRED               =
                                                                             "The URL of the Matrix server is required, please provide it using System properties !";

  public static final String                   MATRIX_ADMIN_USERNAME_IS_REQUIRED           =
                                                                                 "The username of the admin the Matrix server is required, please provide it using System properties !";

  public static final String                   BEARER                                      = "Bearer ";

  public static final String                   AUTHORIZATION                               = "Authorization";

  public static final String                   MATRIX_JWT_SECRET                           = "meeds.matrix.jwt.secret";

  public static final String                   MATRIX_JWT_COOKIE                           = "matrix_jwt_token";

  public static final String                   CONTENT_TYPE                                = "Content-type";

  public static final String                   USER_MATRIX_ID                              = "matrixId";

  public static final String                   MESSAGE_USER_KICKED_SPACE                   =
                                                                         "The user is no more member of the space %s, thus he was kicked out of this room!";

  // User roles on Matrix
  public static final String                   ADMIN_ROLE                                  = "100";

  public static final String                   MANAGER_ROLE                                = "50";

  public static final String                   SIMPLE_USER_ROLE                            = "0";

  // Notification
  public static final ArgumentLiteral<String>  MATRIX_ROOM_ID                              =
                                                              new ArgumentLiteral<>(String.class, "MATRIX_ROOM_ID");

  public static final ArgumentLiteral<String>  MATRIX_ROOM_MEMBER                          =
                                                                  new ArgumentLiteral<>(String.class, "MATRIX_ROOM_MEMBER");

  public static final ArgumentLiteral<Integer> MATRIX_ROOM_UNREAD_COUNT                    =
                                                                        new ArgumentLiteral<>(Integer.class,
                                                                                              "MATRIX_ROOM_UNREAD_COUNT");
  public static final ArgumentLiteral<String> MATRIX_ROOM_NAME                    =
                                                                        new ArgumentLiteral<>(String.class,
                                                                                              "MATRIX_ROOM_NAME");
  public static final ArgumentLiteral<String> MATRIX_MESSAGE_SENDER_FULLNAME                    =
                                                                        new ArgumentLiteral<>(String.class,
                                                                                              "MATRIX_MESSAGE_SENDER_FULLNAME");

  public static final String                   MATRIX_MENTION_RECEIVED_NOTIFICATION_PLUGIN =
                                                                                           "MatrixMentionReceivedNotificationPlugin";


}
