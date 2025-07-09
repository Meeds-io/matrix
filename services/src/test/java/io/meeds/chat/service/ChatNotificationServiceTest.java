package io.meeds.chat.service;

import io.meeds.chat.MatrixBaseTest;
import io.meeds.chat.model.MatrixMessage;
import io.meeds.chat.model.Room;
import io.meeds.chat.service.utils.MatrixHttpClient;
import io.meeds.pwa.model.PwaNotificationMessage;
import io.meeds.pwa.service.PwaNotificationService;
import org.exoplatform.services.resources.LocaleConfig;
import org.exoplatform.services.resources.Orientation;
import org.exoplatform.services.resources.impl.LocaleConfigImpl;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.ArrayList;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(MatrixBaseTest.class)
class ChatNotificationServiceTest extends MatrixBaseTest {

  @Autowired
  MatrixService           matrixService;

  @Autowired
  IdentityManager         identityManager;

  @Autowired
  ChatNotificationService chatNotificationService;

  @Autowired
  PwaNotificationService  pwaNotificationService;

  @Test
  void sendCreateNotificationAction() {
  }

  @Test
  void createNotification() throws Exception {
    String eventId = "eventIDOnMatrix";
    Space space = getSpaceInstance(1);
    String roomId = matrixService.createRoom(space);
    roomsToDelete.add(roomId);
    String userName = "demo";
    Identity demoIdentity = identityManager.getOrCreateUserIdentity("demo");
    String userIdOnMatrix = matrixService.saveUserAccount(demoIdentity, true);

    MatrixMessage matrixMessage = new MatrixMessage(eventId,
                                                    roomId,
                                                    "m.room.message",
                                                    "This is a chat message",
                                                    "m.text",
                                                    userIdOnMatrix,
                                                    new ArrayList<>());
    when(matrixHttpClient.getEventById(eventId, matrixRoomId, accessToken)).thenReturn(matrixMessage);
    LocaleConfig localeConfig = new LocaleConfigImpl();
    localeConfig.setLocale(Locale.ENGLISH);
    localeConfig.setOrientation(Orientation.LT);
    PwaNotificationMessage pwaNotificationMessage = chatNotificationService.createNotification(eventId, matrixRoomId, userName, accessToken);
    assertNotNull(pwaNotificationMessage);
    assertEquals("Demo exo in my space 1", pwaNotificationMessage.getTitle());
    assertEquals("This is a chat message", pwaNotificationMessage.getBody());

    Room oneToOneRoom = new Room();
    oneToOneRoom.setRoomId("!oneToOneRoom:matrix.meeds.tn");
    oneToOneRoom.setFirstParticipant("demo");
    oneToOneRoom.setSecondParticipant("tom");
    oneToOneRoom = matrixService.createDirectMessagingRoom(oneToOneRoom);
    Identity tomIdentity = identityManager.getOrCreateUserIdentity("tom");
    matrixMessage = new MatrixMessage(eventId,
                                      oneToOneRoom.getRoomId(),
                                      "m.room.message",
                                      "This is a private chat message",
                                      "m.text",
                                      userIdOnMatrix,
                                      new ArrayList<>());
    when(matrixHttpClient.getEventById(eventId, oneToOneRoom.getRoomId(), accessToken)).thenReturn(matrixMessage);

    pwaNotificationMessage = chatNotificationService.createNotification(eventId, oneToOneRoom.getRoomId(), userName, accessToken);
    assertNotNull(pwaNotificationMessage);
    assertNotNull(pwaNotificationMessage.getIcon());
    assertEquals(tomIdentity.getProfile().getFullName(), pwaNotificationMessage.getTitle());
    assertEquals("This is a private chat message", pwaNotificationMessage.getBody());
  }
}
