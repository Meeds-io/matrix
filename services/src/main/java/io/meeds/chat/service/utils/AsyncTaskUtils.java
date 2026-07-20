/**
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package io.meeds.chat.service.utils;

import org.exoplatform.commons.utils.PropertyManager;

import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_ASYNC_ENABLED;

public final class AsyncTaskUtils {

  private AsyncTaskUtils() {
  }

  /**
   * Runs the given task on a new background thread, unless
   * {@link MatrixConstants#MATRIX_ASYNC_ENABLED} is explicitly set to
   * {@code false} (used by tests to make room-enable/disable side effects
   * deterministic instead of racing with the calling thread).
   *
   * @param threadName the name of the background thread, when run
   *          asynchronously
   * @param task the task to run
   */
  public static void runAsync(String threadName, Runnable task) {
    String asyncEnabled = PropertyManager.getProperty(MATRIX_ASYNC_ENABLED);
    if (asyncEnabled == null || Boolean.parseBoolean(asyncEnabled)) {
      new Thread(task, threadName).start();
    } else {
      task.run();
    }
  }

}
