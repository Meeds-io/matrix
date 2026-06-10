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
package io.meeds.chat.listeners;

import io.meeds.chat.service.model.MediaInfo;
import io.meeds.chat.service.MatrixService;
import jakarta.annotation.PostConstruct;
import org.exoplatform.services.listener.Asynchronous;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.services.listener.ListenerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@Asynchronous
@Component
public class MatrixInfectedFileCleanupListener extends Listener<String, Object> {

  @Autowired
  private ListenerService     listenerService;

  @Autowired
  private MatrixService       matrixService;

  private static final String INFECTED_ITEM_PATH              = "infectedItemPath";

  public static final String  INFECTED_ITEM_NAME              = "infectedItemName";

  public static final String  INFECTED_ITEM_LAST_MODIFIER     = "infectedItemLastModifier";

  public static final String  INFECTED_ITEM_ID                = "infectedItemId";

  public static final String  INFECTED_ITEM_MODIFICATION_DATE = "infectedItemModificationDate";

  private static final String EXTERNAL_FILE_SYSTEM            = "externalFileSystem";

  private static final String SYNAPSE                         = "synapse";

  private static final String LOCAL_CONTENT_FOLDER            = "/local_content/";

  private static final String CLEAN_FILE_INFECTED             = "clean-file-infected";

  private final String[]      eventTypes                      = { CLEAN_FILE_INFECTED };

  @PostConstruct
  public void init() {
    for (String eventType : eventTypes) {
      listenerService.addListener(eventType, this);
    }
  }

  @Override
  public void onEvent(Event<String, Object> event) throws Exception {
    String type = event.getSource();
    Map<String, String> infectedItem = (Map<String, String>) event.getData();

    if (type.equals(EXTERNAL_FILE_SYSTEM) && infectedItem.containsKey(INFECTED_ITEM_PATH)) {
      String path = infectedItem.get(INFECTED_ITEM_PATH);

      if (path.contains(File.separator + SYNAPSE + File.separator)) {
        String mediaId = extractMediaId(path);
        if (mediaId != null) {
          MediaInfo mediaInfo = matrixService.getMediaInfo(mediaId).orElse(null);
          if (mediaInfo != null) {
            matrixService.deleteMedia(mediaId);
            listenerService.broadcast("infected-file-cleaned", null, buildFileInfo(mediaInfo));
          }
        }
      }
    }
  }

  private Map<String, String> buildFileInfo(MediaInfo mediaInfo) {
    Map<String, String> infectedItem = new HashMap<>();
    infectedItem.put(INFECTED_ITEM_NAME, mediaInfo.getFilename());
    infectedItem.put(INFECTED_ITEM_ID, mediaInfo.getMediaId());
    infectedItem.put(INFECTED_ITEM_LAST_MODIFIER, matrixService.findUserByMatrixId(mediaInfo.getOwner()));
    infectedItem.put(INFECTED_ITEM_MODIFICATION_DATE, String.valueOf(mediaInfo.getCreatedTs()));
    return infectedItem;
  }

  private String extractMediaId(String path) {
    if (path == null)
      return null;

    String normalizedPath = path.replace(File.separatorChar, '/');
    int localContentIndex = normalizedPath.indexOf(LOCAL_CONTENT_FOLDER);
    if (localContentIndex < 0) {
      return null;
    }
    String relativePath = normalizedPath.substring(localContentIndex + LOCAL_CONTENT_FOLDER.length());
    String[] parts = relativePath.split("/");
    if (parts.length < 2) {
      return null;
    }
    return parts[0] + parts[1] + parts[2];
  }
}
