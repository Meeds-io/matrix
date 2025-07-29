package io.meeds.chat.service;

import io.meeds.chat.MatrixBaseTest;
import io.meeds.chat.model.MatrixMessage;
import io.meeds.chat.model.Room;
import io.meeds.pwa.model.PwaNotificationMessage;
import io.meeds.pwa.service.PwaNotificationService;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.services.resources.LocaleConfig;
import org.exoplatform.services.resources.Orientation;
import org.exoplatform.services.resources.impl.LocaleConfigImpl;
import org.exoplatform.services.user.UserStateModel;
import org.exoplatform.services.user.UserStateService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(MatrixBaseTest.class)
class ChatNotificationServiceTest extends MatrixBaseTest {

  @Autowired
  MatrixService                      matrixService;

  @Autowired
  IdentityManager                    identityManager;

  @Autowired
  ChatNotificationService            chatNotificationService;

  @Autowired
  PwaNotificationService             pwaNotificationService;

  @Mock
  private UserStateService           userStateService;

  @Mock
  private UserStateModel             userStateModel;

  private MockedStatic<CommonsUtils> commonsUtils;


  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();
    commonsUtils = mockStatic(CommonsUtils.class, CALLS_REAL_METHODS);
    commonsUtils.when(() -> CommonsUtils.getService(UserStateService.class)).thenReturn(userStateService);

    when(userStateService.getUserState(anyString())).thenReturn(userStateModel);
  }

  @AfterEach
  @Override
  public void tearDown() {
    if (commonsUtils != null) {
      commonsUtils.close();
    }
    super.tearDown();
  }

  @Test
  void sendCreateNotificationAction() {
    when(userStateModel.getStatus()).thenReturn("available");
    ScheduledFuture<?> action = chatNotificationService.sendCreateNotificationAction("eventIDOnMatrix",
                                                                                     "demo",
                                                                                     "!roomId:matrix.meeds.tn",
                                                                                     5);
    assertNotNull(action);
    when(userStateModel.getStatus()).thenReturn("donotdisturb");
    action = chatNotificationService.sendCreateNotificationAction("eventIDOnMatrix", "demo", "!roomId:matrix.meeds.tn", 5);
    assertNull(action);
  }

  @Test
  void createNotification() throws Exception {
    when(userStateModel.getStatus()).thenReturn("available");
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
