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
