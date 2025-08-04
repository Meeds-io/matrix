package io.meeds.chat.service;

import io.meeds.chat.MatrixBaseTest;
import io.meeds.chat.model.MatrixMessage;
import io.meeds.chat.model.Room;
import io.meeds.pwa.model.PwaNotificationMessage;
import io.meeds.pwa.service.PwaNotificationService;
import io.meeds.social.util.JsonUtils;
import org.exoplatform.commons.api.notification.model.UserSetting;
import org.exoplatform.commons.api.notification.service.setting.UserSettingService;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
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

  @Mock
  private UserSetting                userSetting;

  @Mock
  private UserSettingService         userSettingService;

  @Mock
  private SettingService             settingService;

  private MockedStatic<CommonsUtils> commonsUtils;


  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();
    commonsUtils = mockStatic(CommonsUtils.class, CALLS_REAL_METHODS);
    commonsUtils.when(() -> CommonsUtils.getService(UserStateService.class)).thenReturn(userStateService);
    commonsUtils.when(() -> CommonsUtils.getService(UserSettingService.class)).thenReturn(userSettingService);
    when(userStateService.getUserState(anyString())).thenReturn(userStateModel);
    when(userSettingService.get(anyString())).thenReturn(userSetting);
    ReflectionTestUtils.setField(chatNotificationService, "settingService", settingService);
  }

  @AfterEach
  @Override
  public void tearDown() {
    super.tearDown();
    if (commonsUtils != null) {
      commonsUtils.close();
    }
  }

  @Test
  void sendCreateNotificationAction() throws Exception {
    Space space = getSpaceInstance(2);
    String roomId = matrixService.createRoom(space);
    roomsToDelete.add(roomId);
    when(userStateModel.getStatus()).thenReturn("available");
    when(userSetting.isSpaceMuted(anyLong())).thenReturn(false);
    ScheduledFuture<?> action = chatNotificationService.sendCreateNotificationAction("eventIDOnMatrix", "demo", roomId, 5);
    assertNotNull(action);

    when(userStateModel.getStatus()).thenReturn("donotdisturb");
    when(userSetting.isSpaceMuted(anyLong())).thenReturn(false);
    action = chatNotificationService.sendCreateNotificationAction("eventIDOnMatrix", "demo", roomId, 5);
    assertNull(action);

    when(userStateModel.getStatus()).thenReturn("available");
    when(userSetting.isSpaceMuted(anyLong())).thenReturn(true);
    action = chatNotificationService.sendCreateNotificationAction("eventIDOnMatrix", "demo", roomId, 5);
    assertNull(action);
  }

  @Test
  void createNotification() throws Exception {
    when(userStateModel.getStatus()).thenReturn("available");
    when(userSetting.isSpaceMuted(anyLong())).thenReturn(false);
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

  @Test
  void testIsRoomMutedForUser() {
    String userName = "demo";
    String mutedRoomId = "!mutedRoom:matrix.meeds.tn";
    String otherRoomId = "!otherRoom:matrix.meeds.tn";
    Scope scope = ChatNotificationService.USER_CHAT_NOTIFICATION_SCOPE;
    String key = ChatNotificationService.MUTED_ROOMS;

    // Case: room is muted
    SettingValue settingValue = SettingValue.create(JsonUtils.toJsonString(Set.of(mutedRoomId)));
    when(settingService.get(Context.USER.id(userName), scope, key)).thenReturn(settingValue);

    assertTrue(chatNotificationService.isPrivateRoomMutedForUser(userName, mutedRoomId));

    // Case: room is NOT muted
    SettingValue otherSettingValue = SettingValue.create(JsonUtils.toJsonString(Set.of(otherRoomId)));
    when(settingService.get(Context.USER.id(userName), scope, key)).thenReturn(otherSettingValue);
    assertFalse(chatNotificationService.isPrivateRoomMutedForUser(userName, mutedRoomId));

    // Case: no setting found (null)
    when(settingService.get(Context.USER.id(userName), scope, key)).thenReturn(null);
    assertFalse(chatNotificationService.isPrivateRoomMutedForUser(userName, mutedRoomId));
  }

  @Test
  void testMutePrivateRoom() {
    String userName = "demo";
    String newRoomId = "!newRoom:matrix.meeds.tn";
    Scope scope = ChatNotificationService.USER_CHAT_NOTIFICATION_SCOPE;
    String key = ChatNotificationService.MUTED_ROOMS;

    // Initially no muted rooms (empty list)
    SettingValue settingValue = SettingValue.create("[]");
    when(settingService.get(Context.USER.id(userName), scope, key)).thenReturn(settingValue);

    chatNotificationService.mutePrivateRoom(userName, newRoomId);

    // Verify set() is called with muted rooms containing newRoomId
    verify(settingService).set(eq(Context.USER.id(userName)), eq(scope), eq(key), argThat(setting -> {
      try {
        Set<String> rooms = JsonUtils.OBJECT_MAPPER.readValue(setting.getValue().toString(),
                                                              new com.fasterxml.jackson.core.type.TypeReference<>() {
                                                              });
        return rooms.contains(newRoomId);
      } catch (Exception e) {
        return false;
      }
    }));

    // If room is already muted, set() should NOT be called again
    Set<String> alreadyMuted = Set.of(newRoomId);
    SettingValue existingSettingValue = SettingValue.create(JsonUtils.toJsonString(alreadyMuted));
    when(settingService.get(Context.USER.id(userName), scope, key)).thenReturn(existingSettingValue);

    chatNotificationService.mutePrivateRoom(userName, newRoomId);
    // This second call should not trigger set
    verify(settingService, times(1)).set(any(), any(), any(), any());
  }
}
