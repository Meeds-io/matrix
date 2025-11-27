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

import io.meeds.chat.rest.model.MediaInfo;
import io.meeds.chat.service.MatrixService;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.ListenerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MatrixInfectedFileCleanupListenerTest {

  @Mock
  private ListenerService                   listenerService;

  @Mock
  private MatrixService                     matrixService;

  @InjectMocks
  private MatrixInfectedFileCleanupListener listener;

  @BeforeEach
  void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void shouldDeleteSynapseMediaAndBroadcastEvent() throws Exception {
    String path = "/scan/synapse/media/local_content/co/uJ/GGfSoxnTRbAOkmZwjsBh";
    String mediaId = "couJGGfSoxnTRbAOkmZwjsBh";

    Map<String, String> data = Map.of("infectedItemPath", path);
    Event<String, Object> event = new Event<>("clean-file-infected", "externalFileSystem", data);

    MediaInfo mediaInfo = new MediaInfo();
    mediaInfo.setFilename("test.png");
    mediaInfo.setMediaId(mediaId);
    mediaInfo.setOwner("john");
    mediaInfo.setCreatedTs(123456789L);

    when(matrixService.getMediaInfo(mediaId)).thenReturn(Optional.of(mediaInfo));

    listener.onEvent(event);

    verify(matrixService).getMediaInfo(mediaId);
    verify(matrixService).deleteMedia(mediaId);
    verify(listenerService).broadcast(anyString(), any(), any());
  }

  @Test
  void shouldIgnoreIfNotSynapsePath() throws Exception {
    Map<String, String> data = Map.of("infectedItemPath", "/scan/other/system/file.txt");
    Event<String, Object> event = new Event<>("clean-file-infected", "externalFileSystem", data);

    listener.onEvent(event);

    verifyNoInteractions(matrixService);
    verify(listenerService, never()).broadcast(any(), any(), any());
  }

  @Test
  void shouldIgnoreIfMediaNotFound() throws Exception {
    String path = "/scan/synapse/media/local_content/co/uJ/ABCDEF123";
    Map<String, String> data = Map.of("infectedItemPath", path);

    when(matrixService.getMediaInfo(anyString())).thenReturn(Optional.empty());

    Event<String, Object> event = new Event<>("clean-file-infected", "externalFileSystem", data);

    listener.onEvent(event);

    verify(matrixService).getMediaInfo("couJABCDEF123");
    verify(matrixService, never()).deleteMedia(anyString());
    verify(listenerService, never()).broadcast(any(), any(), any());
  }

}
