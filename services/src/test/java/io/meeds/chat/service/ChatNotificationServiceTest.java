package io.meeds.chat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import io.meeds.chat.MatrixBaseTest;
import io.meeds.chat.model.MatrixMessage;
import io.meeds.chat.model.Room;
import io.meeds.pwa.model.PwaNotificationMessage;
import io.meeds.pwa.service.PwaNotificationService;
import io.meeds.social.util.JsonUtils;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.model.UserSetting;
import org.exoplatform.commons.api.notification.model.WebNotificationFilter;
import org.exoplatform.commons.api.notification.service.WebNotificationService;
import org.exoplatform.commons.api.notification.service.setting.UserSettingService;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.services.resources.LocaleConfig;
import org.exoplatform.services.resources.Orientation;
import org.exoplatform.services.resources.ResourceBundleService;
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

import java.util.*;
import java.util.concurrent.ScheduledFuture;

import static io.meeds.chat.service.ChatNotificationService.PUSH_NOTIFICATIONS_SETTINGS;
import static io.meeds.chat.service.ChatNotificationService.USER_CHAT_NOTIFICATION_SCOPE;
import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_MENTION_RECEIVED_NOTIFICATION_PLUGIN;
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

  @Autowired
  WebNotificationService             webNotificationService;

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

  @Mock
  private ResourceBundleService      resourceBundleService;

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
    String roomId = matrixService.getRoomBySpace(space).getRoomId();

    when(userStateModel.getStatus()).thenReturn("available");
    when(userSetting.isSpaceMuted(anyLong())).thenReturn(false);
    String eventId = "eventIDOnMatrix";
    ScheduledFuture<?> action = chatNotificationService.sendCreateNotificationAction(eventId, "demo", roomId, 5);
    assertNotNull(action);

    // Create mention
    MatrixMessage matrixMessage = new MatrixMessage(eventId,
                                                    roomId,
                                                    "m.room.message",
                                                    "Message content",
                                                    "m.text",
                                                    "@sender:matrix.meeds.tn",
                                                    Collections.singletonList("@demo:matrix.meeds.tn"),
                                                    123456789);
    when(matrixHttpClient.getEventById(eventId, roomId, accessToken)).thenReturn(matrixMessage);

    action = chatNotificationService.sendCreateNotificationAction(eventId, "demo", roomId, 5);
    assertNotNull(action);

    when(userStateModel.getStatus()).thenReturn("donotdisturb");
    when(userSetting.isSpaceMuted(anyLong())).thenReturn(false);
    action = chatNotificationService.sendCreateNotificationAction(eventId, "demo", roomId, 5);
    assertNull(action);

    when(userStateModel.getStatus()).thenReturn("available");
    when(userSetting.isSpaceMuted(anyLong())).thenReturn(true);
    action = chatNotificationService.sendCreateNotificationAction(eventId, "demo", roomId, 5);
    assertNull(action);
  }

  @Test
  void createNotification() throws Exception {
    when(userStateModel.getStatus()).thenReturn("available");
    when(userSetting.isSpaceMuted(anyLong())).thenReturn(false);
    String eventId = "eventIDOnMatrix";
    Space space = getSpaceInstance(1);
    String roomId = matrixService.getRoomBySpace(space).getRoomId();
    String userName = "demo";
    Identity demoIdentity = identityManager.getOrCreateUserIdentity("demo");
    String userIdOnMatrix = matrixService.saveUserAccount(demoIdentity, true);

    MatrixMessage matrixMessage = new MatrixMessage(eventId,
                                                    roomId,
                                                    "m.room.message",
                                                    "This is a chat message",
                                                    "m.text",
                                                    userIdOnMatrix,
                                                    new ArrayList<>(),
                                                    123456789);
    when(matrixHttpClient.getEventById(eventId, matrixRoomId, accessToken)).thenReturn(matrixMessage);
    LocaleConfig localeConfig = new LocaleConfigImpl();
    localeConfig.setLocale(Locale.ENGLISH);
    localeConfig.setOrientation(Orientation.LT);
    PwaNotificationMessage pwaNotificationMessage = chatNotificationService.createNotification(eventId,
                                                                                               matrixRoomId,
                                                                                               userName,
                                                                                               0,
                                                                                               accessToken);
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
                                      new ArrayList<>(),
                                      123456789);
    when(matrixHttpClient.getEventById(eventId, oneToOneRoom.getRoomId(), accessToken)).thenReturn(matrixMessage);

    pwaNotificationMessage = chatNotificationService.createNotification(eventId,
                                                                        oneToOneRoom.getRoomId(),
                                                                        userName,
                                                                        0,
                                                                        accessToken);
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
    Scope scope = USER_CHAT_NOTIFICATION_SCOPE;
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
  void testToggleMutePrivateRoom() {
    String userName = "demo";
    String roomId = "!newRoom:matrix.meeds.tn";
    Scope scope = USER_CHAT_NOTIFICATION_SCOPE;
    String key = ChatNotificationService.MUTED_ROOMS;

    final Set<String>[] currentMutedRooms = new Set[] { new HashSet<>() };
    when(settingService.get(eq(Context.USER.id(userName)),
                            eq(scope),
                            eq(key))).thenAnswer(invocation -> SettingValue.create(JsonUtils.toJsonString(currentMutedRooms[0])));

    doAnswer(invocation -> {
      String json = invocation.getArgument(3, SettingValue.class).getValue().toString();
      currentMutedRooms[0] = JsonUtils.OBJECT_MAPPER.readValue(json, new TypeReference<>() {
      });
      return null;
    }).when(settingService).set(any(), any(), any(), any());

    // 1. Mute
    chatNotificationService.toggleMutePrivateRoom(userName, roomId);
    assertTrue(currentMutedRooms[0].contains(roomId));

    // 2. Unmute
    chatNotificationService.toggleMutePrivateRoom(userName, roomId);
    assertFalse(currentMutedRooms[0].contains(roomId));

    verify(settingService, times(2)).set(eq(Context.USER.id(userName)), eq(scope), eq(key), any());
  }

  @Test
  void createMentionNotification() throws Exception {
    String eventId = "eventIDOnMatrix";
    Identity demoIdentity = identityManager.getOrCreateUserIdentity("demo");
    String userIdOnMatrix = matrixService.saveUserAccount(demoIdentity, true);
    MatrixMessage matrixMessage = new MatrixMessage(eventId,
                                                    "fakeRoomId",
                                                    "m.room.message",
                                                    "This is a chat message",
                                                    "m.text",
                                                    userIdOnMatrix,
                                                    Collections.singletonList("@demo:matrix.meeds.tn"),
                                                    123456789);
    when(matrixHttpClient.getEventById(eventId, matrixRoomId, accessToken)).thenReturn(matrixMessage);

    boolean result = chatNotificationService.createMentionNotification(eventId, "fakeRoomId", "demo", null);
    assertFalse(result);

    Space space = getSpaceInstance(1);
    spacesToDelete.add(space);
    String roomId = matrixService.getRoomBySpace(space).getRoomId();
    matrixMessage.setRoomId(roomId);

    result = chatNotificationService.createMentionNotification(eventId, roomId, "demo", null);
    assertTrue(result);

    Room room = new Room();
    room.setRoomId("!privateRoomId");
    room.setFirstParticipant("demo");
    room.setSecondParticipant("raul");
    room = matrixService.createDirectMessagingRoom(room);
    matrixMessage.setRoomId(room.getRoomId());

    result = chatNotificationService.createMentionNotification(eventId, room.getRoomId(), "demo", null);
    assertFalse(result);

    when(matrixHttpClient.getAccessToken(anyString())).thenReturn("sys_thisIsAFakeAccessToken2025");
    when(matrixHttpClient.getEventById(eventId, room.getRoomId(), "sys_thisIsAFakeAccessToken2025")).thenReturn(matrixMessage);
    String userAsJson = """
        {
            "name": "@raul:matrix.meeds.tn",
            "displayname": "Raul Hamdi", // can be null if not set
            "threepids": [
                {
                    "medium": "email",
                    "address": "raul@platform.com",
                    "added_at": 1586458409743,
                    "validated_at": 1586458409743
                },
            ],
        }
        """;
    when(matrixHttpClient.getUser(anyString(), anyString())).thenReturn(userAsJson);
    result = chatNotificationService.createMentionNotification(eventId, room.getRoomId(), "demo", "ASamplePushKey");
    assertTrue(result);
  }

  @Test
  void sendPushNotification() throws ObjectNotFoundException, IllegalAccessException {
    NotificationInfo notificationInfo = NotificationInfo.instance()
                                                        .setFrom("raul")
                                                        .to("demo")
                                                        .with("ROOM_ID", "§roomIdenitfier:matrix.meeds.tn")
                                                        .with("MATRIX_ROOM_NAME", "Sample room")
                                                        .with("MATRIX_ROOM_TYPE", "SPACE")
                                                        .with("MATRIX_SENDER_FULL_NAME", "Raul Hamdi")
                                                        .with("MATRIX_ROOM_AVATAR", "/path/to/room")
                                                        .with("MATRIX_MESSAGE_URL", "/link/to/room/message")
                                                        .with("MATRIX_MESSAGE_CONTENT", "This is a message for testing !")
                                                        .key(MATRIX_MENTION_RECEIVED_NOTIFICATION_PLUGIN)
                                                        .end();
    webNotificationService.save(notificationInfo);
    WebNotificationFilter filter = new WebNotificationFilter("demo");
    List<NotificationInfo> notifications = webNotificationService.getNotificationInfos(filter, 0, 10);
    for (NotificationInfo notif : notifications) {
      PwaNotificationMessage pwaNotificationMessage = pwaNotificationService.getNotification(1L, "demo");
      assertNotNull(pwaNotificationMessage);
      assertEquals("Raul Hamdi mentioned you in Sample room", pwaNotificationMessage.getTitle());
      assertEquals("This is a message for testing !", pwaNotificationMessage.getBody());
    }
  }

  @Test
  void isPushNotificationsEnabled() {
    when(settingService.get(Context.USER.id("demo"), USER_CHAT_NOTIFICATION_SCOPE, PUSH_NOTIFICATIONS_SETTINGS)).thenReturn(null);
    boolean result = chatNotificationService.isPushNotificationsEnabled("demo");
    assertTrue(result);
  }

  @Test
  void updatePushNotificationSettings() {
    chatNotificationService.updatePushNotificationSettings("demo", true);
    verify(settingService, times(1)).set(eq(Context.USER.id("demo")),
                                         eq(USER_CHAT_NOTIFICATION_SCOPE),
                                         eq(PUSH_NOTIFICATIONS_SETTINGS),
                                         any());
  }
}
