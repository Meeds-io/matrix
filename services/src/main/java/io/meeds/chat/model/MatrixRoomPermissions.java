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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.exoplatform.ws.frameworks.json.value.JsonValue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MatrixRoomPermissions {
  private List<MatrixUserPermission> users;
  private String userDefault;
  private Events events;
  private String eventsDefault;
  private String stateDefault;
  private String ban;
  private String kick;
  private String redact;
  private String invite;
  private String historical;

  public String toJson() {
    StringBuilder usersString = new StringBuilder();
    for(int index = 0; index < users.size(); index ++) {
      usersString.append("\"") .append(this.users.get(index).getUserName()).append("\"").append(": ").append(this.users.get(index).getUserRole());
      if(index < users.size() - 1) {
        usersString.append(",");
      }
    }
    return """
              {
                  "users": {
                      %s
                  },
                  "users_default": %s,
                  "events": %s,
                  "events_default": %s,
                  "state_default": %s,
                  "ban": %s,
                  "kick": %s,
                  "redact": %s,
                  "invite": %s,
                  "historical": %s
              }
              """.formatted(usersString.toString(), this.userDefault, this.events.toJson(), this.eventsDefault, this.stateDefault, this.ban, this.kick, this.redact, this.invite, this.historical);
  }
  public static MatrixRoomPermissions fromJson(JsonValue jsonValue) {
    List<MatrixUserPermission> matrixUserPermissions = new ArrayList<>();
    Iterator<String> usersIterator = jsonValue.getElement("users").getKeys();
    while(usersIterator.hasNext()) {
      String userMatrixId = usersIterator.next();
      matrixUserPermissions.add(new MatrixUserPermission(userMatrixId, jsonValue.getElement("users").getElement(userMatrixId).getStringValue()));
    }
    return new MatrixRoomPermissions(matrixUserPermissions,
            jsonValue.getElement("users_default").getStringValue(),
            Events.fromJson(jsonValue.getElement("events")),
            jsonValue.getElement("events_default").getStringValue(),
            jsonValue.getElement("state_default").getStringValue(),
            jsonValue.getElement("ban").getStringValue(),
            jsonValue.getElement("kick").getStringValue(),
            jsonValue.getElement("redact").getStringValue(),
            jsonValue.getElement("invite").getStringValue(),
            jsonValue.getElement("historical").getStringValue());
  }
}
