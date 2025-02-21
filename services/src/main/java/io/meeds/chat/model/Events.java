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

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Events {
  private String name;
  private String powerLevels;
  private String historyVisibility;
  private String canonicalAlias;
  private String avatar;
  private String tombstone;
  private String serverAcl;
  private String encryption;
  public String toJson() {
    return """
            {
                "m.room.name": %s,
                "m.room.power_levels": %s,
                "m.room.history_visibility": %s,
                "m.room.canonical_alias": %s,
                "m.room.avatar": %s,
                "m.room.tombstone": %s,
                "m.room.server_acl": %s,
                "m.room.encryption": %s
             }
            """.formatted(this.getName(), this.getPowerLevels(), this.getHistoryVisibility(), this.getCanonicalAlias(), this.getAvatar(), this.getTombstone(), this.getServerAcl(), this.getEncryption());
  }
  public static Events fromJson(JsonValue jsonValue) {
    return new Events(jsonValue.getElement("m.room.name").getStringValue(),
            jsonValue.getElement("m.room.power_levels").getStringValue(),
            jsonValue.getElement("m.room.history_visibility").getStringValue(),
            jsonValue.getElement("m.room.canonical_alias").getStringValue(),
            jsonValue.getElement("m.room.avatar").getStringValue(),
            jsonValue.getElement("m.room.tombstone").getStringValue(),
            jsonValue.getElement("m.room.server_acl").getStringValue(),
            jsonValue.getElement("m.room.encryption").getStringValue());

  }
}
