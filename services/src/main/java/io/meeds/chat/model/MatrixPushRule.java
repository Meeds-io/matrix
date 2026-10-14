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

/**
 * One rule of a Matrix account's global push rule set
 * ({@code /_matrix/client/v3/pushrules/global/}).
 *
 * @param kind the rule kind: {@code override}, {@code content},
 *          {@code postcontent}, {@code room}, {@code sender} or
 *          {@code underride}
 * @param ruleId the rule id; a server-default rule's id starts with {@code .}
 * @param enabled whether the rule is enabled
 * @param notifying whether the rule's actions contain {@code notify}
 */
public record MatrixPushRule(String kind, String ruleId, boolean enabled, boolean notifying) {

  /**
   * Tells whether the rule is one of the server's defaults, which Synapse
   * names with a leading dot, rather than a rule the account defined.
   *
   * @return true for a server-default rule
   */
  public boolean isServerDefault() {
    return ruleId != null && ruleId.startsWith(".");
  }
}
