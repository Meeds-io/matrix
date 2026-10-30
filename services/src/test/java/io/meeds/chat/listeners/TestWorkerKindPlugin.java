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
package io.meeds.chat.listeners;

import org.springframework.stereotype.Service;

import io.meeds.social.core.identity.plugin.AccountKindPlugin;

/**
 * An account kind the matrix tests declare, as an add-on would, on a group
 * no other test uses.
 */
@Service("matrixTestWorkerKindPlugin")
public class TestWorkerKindPlugin implements AccountKindPlugin {

  /** The kind. */
  public static final String KIND     = "matrixtestworker";

  /** The kind's group. */
  public static final String GROUP_ID = "/matrix-test-workers";

  @Override
  public String getKind() {
    return KIND;
  }

  @Override
  public String getGroupId() {
    return GROUP_ID;
  }
}
