/**
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
package io.meeds.chat.rest;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.meeds.chat.entity.RoomStatus;
import io.meeds.chat.model.Room;
import io.meeds.chat.rest.model.*;
import io.meeds.chat.service.ChatNotificationService;
import io.meeds.chat.service.MatrixService;
import io.meeds.chat.service.MatrixSynchronizationService;
import io.meeds.pwa.service.PwaNotificationService;
import io.meeds.spring.web.security.PortalAuthenticationManager;
import io.meeds.spring.web.security.WebSecurityConfiguration;
import jakarta.servlet.Filter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.exoplatform.commons.api.notification.service.storage.NotificationService;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.services.resources.ResourceBundleService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.service.LinkProvider;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.rest.api.EntityBuilder;
import org.exoplatform.social.rest.api.RestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.*;

import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_SERVER_NAME;
import static io.meeds.chat.service.utils.MatrixConstants.USER_MATRIX_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = { MatrixRest.class, PortalAuthenticationManager.class })
@ContextConfiguration(classes = { WebSecurityConfiguration.class })
@AutoConfigureWebMvc
@AutoConfigureMockMvc(addFilters = false)
@ExtendWith(MockitoExtension.class)
class MatrixRestTest {

  private static final String          SIMPLE_USER   = "user";

  private static final String          ADMIN_USER    = "admin";

  private static final String          TEST_PASSWORD = "testPassword";

  private static final String          REST_PATH     = "/matrix";     // NOSONAR

  static final ObjectMapper            OBJECT_MAPPER;

  @Autowired
  private SecurityFilterChain          filterChain;

  @Autowired
  private WebApplicationContext        context;

  @MockBean
  private SpaceService                 spaceService;

  @MockBean
  private MatrixService                matrixService;

  @MockBean
  private MatrixSynchronizationService matrixSynchronizationService;

  @MockBean
  private IdentityManager              identityManager;

  @MockBean
  private ResourceBundleService        resourceBundleService;

  @MockBean
  private NotificationService          notificationService;

  @MockBean
  private ChatNotificationService      chatNotificationService;

  @MockBean
  PwaNotificationService               pwaNotificationService;

  MockedStatic<LinkProvider>           LINK_PROVIDER;

  MockedStatic<RestUtils>              REST_UTILS;

  MockedStatic<EntityBuilder>          ENTITY_BUILDER;

  private MockMvc                      mockMvc;

  static {
    // Workaround when Jackson is defined in shared library with different
    // version and without artifact jackson-datatype-jsr310
    OBJECT_MAPPER = JsonMapper.builder()
                              .configure(JsonReadFeature.ALLOW_MISSING_VALUES, true)
                              .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                              .build();
    OBJECT_MAPPER.registerModule(new JavaTimeModule());
  }

  @BeforeEach
  public void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(filterChain.getFilters().toArray(new Filter[0])).build();
    PropertyManager.setProperty(MATRIX_SERVER_NAME, "matrix.meeds.tn");

    LINK_PROVIDER = mockStatic(LinkProvider.class);
    REST_UTILS = mockStatic(RestUtils.class);
    ENTITY_BUILDER = mockStatic(EntityBuilder.class);
  }

  @AfterEach
  void tearDown() {
    LINK_PROVIDER.close();
    REST_UTILS.close();
    ENTITY_BUILDER.close();
  }

  @Test
  public void testProcessRooms() throws Exception {
    RoomEntity roomEntity1 = createRoomEntity(1);
    roomEntity1.setSpaceId("1");
    RoomEntity roomEntity2 = createRoomEntity(2);
    roomEntity2.setSpaceId("2");
    RoomEntity roomEntity3 = createRoomEntity(3);
    roomEntity3.setSpaceId("3");
    Room room1 = new Room();
    room1.setRoomId("!testRoom1:matrix.meeds.tn");
    room1.setSpaceId("1");
    room1.setStatus(RoomStatus.ENABLED.name());
    Room room2 = new Room();
    room2.setRoomId("!testRoom2:matrix.meeds.tn");
    room2.setSpaceId("2");
    room2.setStatus(RoomStatus.ENABLED.name());
    Room room3 = new Room();
    room3.setRoomId("!testRoom3:matrix.meeds.tn");
    room3.setSpaceId("3");
    room3.setStatus(RoomStatus.ENABLED.name());

    when(matrixService.getById("!testRoom1", true)).thenReturn(room1);
    when(matrixService.getById("!testRoom1")).thenReturn(room1);
    when(matrixService.getById("!testRoom2", true)).thenReturn(room2);
    when(matrixService.getById("!testRoom2")).thenReturn(room2);
    when(matrixService.getById("!testRoom3", true)).thenReturn(room3);
    when(matrixService.getById("!testRoom3")).thenReturn(room3);
    Space space1 = new Space();
    space1.setDisplayName("Space of Heroes 1");
    space1.setAvatarUrl("/Url/Of/Avatar.png");
    space1.setMembers(new String[] { "user1", "user2" });
    when(spaceService.getSpaceById("1")).thenReturn(space1);
    when(matrixService.getRoomBySpaceId("1")).thenReturn(room1);
    Space space2 = new Space();
    space2.setDisplayName("Space of Heroes 2");
    space2.setAvatarUrl("/Url/Of/Avatar.png");
    space2.setMembers(new String[] { "user1", "user2" });
    when(spaceService.getSpaceById("2")).thenReturn(space2);
    when(matrixService.getRoomBySpaceId("2")).thenReturn(room2);
    Space space3 = new Space();
    space3.setDisplayName("Space of Heroes 3");
    space3.setAvatarUrl("/Url/Of/Avatar.png");
    space3.setMembers(new String[] { "user1", "user2" });
    when(spaceService.getSpaceById("3")).thenReturn(space3);
    when(matrixService.getRoomBySpaceId("3")).thenReturn(room3);

    RoomEntity privateRoomEntity1 = createRoomEntity(4);
    privateRoomEntity1.setDirectChat(true);
    Room privateRoom1 = new Room();
    privateRoom1.setRoomId("!testRoom4:matrix.meeds.tn");
    privateRoom1.setSpaceId(null);
    privateRoom1.setFirstParticipant(SIMPLE_USER);
    privateRoom1.setSecondParticipant("user2");
    privateRoom1.setStatus(RoomStatus.ENABLED.name());
    createUserIdentity("user2");
    when(matrixService.getById("!testRoom4", true)).thenReturn(privateRoom1);
    when(matrixService.getById("!testRoom4")).thenReturn(privateRoom1);

    RoomList roomsList = new RoomList();
    roomsList.setTotalUnreadMessages(5);
    roomsList.setRooms(List.of(roomEntity1, roomEntity2, privateRoomEntity1));

    when(spaceService.getMemberSpacesIds(SIMPLE_USER,
                                         0,
                                         -1)).thenReturn(new ArrayList<>(List.of(new String[] { "1", "2", "3" })));
    ResultActions response = mockMvc.perform(post(REST_PATH + "/processRooms").with(simpleUser())
                                                                              .contentType(MediaType.APPLICATION_JSON)
                                                                              .content(asJsonString(roomsList)));
    response.andExpect(status().isOk());
    response.andExpect(content().contentType(MediaType.APPLICATION_JSON));
    // we should have roomEntity3 inside the response RoomList
    RoomList expectedRoomList = fromJsonString(response.andReturn().getResponse().getContentAsString(), RoomList.class);
    assertNotNull(expectedRoomList);
    assertNotNull(expectedRoomList.getRooms());
    assertEquals(4, expectedRoomList.getRooms().size());
    assertEquals(5, expectedRoomList.getTotalUnreadMessages());

    //
    Room room = new Room();
    room.setSpaceId(null);
    room.setFirstParticipant("root");
    room.setSecondParticipant("user");
    createUserIdentity("root");

    ResultActions response1 = mockMvc.perform(post(REST_PATH + "/processRooms").with(simpleUser())
                                                                               .contentType(MediaType.APPLICATION_JSON)
                                                                               .content(asJsonString(roomsList)));
    response1.andExpect(status().isOk());
  }

  private void createUserIdentity(String userName) {
    Identity identity = new Identity();
    identity.setRemoteId(userName);
    identity.setId("1");
    Profile profile = new Profile();
    profile.setAvatarUrl("/avatar/of/root");
    profile.setProperty("firstName", userName);
    profile.setProperty("lastName", "The king");
    identity.setProfile(profile);
    when(identityManager.getOrCreateUserIdentity(userName)).thenReturn(identity);
  }

  private RoomEntity createRoomEntity(int index) {
    RoomEntity room = new RoomEntity();
    room.setId("!testRoom" + index + ":matrix.meeds.tn");
    room.setAvatarUrl("/avatar/" + index);
    room.setName("Chat number " + index);
    Member root = new Member("1", "userId", "matrixId", "root", "/user/avatar" + 1, System.currentTimeMillis());
    Member user = new Member("2", "userId", "matrixId", "user", "/user/avatar" + 2, System.currentTimeMillis());
    room.setMembers(Arrays.asList(user, root));
    room.setUnreadMessages(index);
    room.setPresence("online");
    room.setTopic("No topic");
    room.setUpdated(System.currentTimeMillis());
    LastMessage lastMessage = new LastMessage();
    lastMessage.setContent("This is a new message");
    lastMessage.setSender("@root:matrix.meeds.tn");
    room.setLastMessage(lastMessage);
    room.setStatus(RoomStatus.ENABLED.name());
    return room;
  }

  private RequestPostProcessor simpleUser() {
    return user(SIMPLE_USER).password(TEST_PASSWORD).authorities(new SimpleGrantedAuthority("users"));
  }

  private RequestPostProcessor adminUser() {
    return user(SIMPLE_USER).password(TEST_PASSWORD).authorities(new SimpleGrantedAuthority("administrators"));
  }

  @SneakyThrows
  public static String asJsonString(final Object obj) {
    return OBJECT_MAPPER.writeValueAsString(obj);
  }

  @Test
  void updatePresenceStatus() throws Exception {
    Presence presence = new Presence();
    presence.setPresence("online");
    presence.setStatusMessage("I am available");
    presence.setUserIdOnMatrix("@user:matrix.meeds.tn");
    when(matrixService.updateUserPresence(anyString(), anyString(), anyString())).thenReturn(presence.getPresence());

    ResultActions response = mockMvc.perform(put(REST_PATH + "/setStatus").with(simpleUser())
                                                                          .contentType(MediaType.APPLICATION_JSON)
                                                                          .content(asJsonString(presence)));
    response.andExpect(status().isOk());
    response.andExpect(content().string("online"));
  }

  @Test
  void getRoomById() throws Exception {
    String roomId = "!testRoomIdentifier:matrix.meeds.tn";
    ResultActions response = mockMvc.perform(get(REST_PATH + "/byRoomId").with(simpleUser())
                                                                         .contentType(MediaType.APPLICATION_JSON)
                                                                         .param("roomId", roomId));
    response.andExpect(status().isForbidden());

    Room room = new Room();
    room.setRoomId(roomId);
    room.setSpaceId("1");

    Space space = new Space();
    space.setAvatarUrl("/avatar/of/the/space");
    space.setDisplayName("Test space");
    when(spaceService.getSpaceById("1")).thenReturn(space);
    when(matrixService.getById(roomId)).thenReturn(room);
    when(matrixService.canAccess(eq(room), anyString())).thenReturn(true);

    ResultActions response1 = mockMvc.perform(get(REST_PATH + "/byRoomId").with(simpleUser())
                                                                          .contentType(MediaType.APPLICATION_JSON)
                                                                          .param("roomId", roomId));
    response1.andExpect(status().isOk());
  }

  @Test
  void getDirectMessagingRoom() throws Exception {
    String roomId = "!testRoomIdentifier:matrix.meeds.tn";
    ResultActions response = mockMvc.perform(get(REST_PATH + "/dmRoom").with(simpleUser())
                                                                       .contentType(MediaType.APPLICATION_JSON)
                                                                       .param("firstParticipant", "userOne")
                                                                       .param("secondParticipant", "userTwo"));
    response.andExpect(status().isNotFound());

    Room room = new Room();
    room.setRoomId(roomId);
    room.setSpaceId(null);
    room.setFirstParticipant("userOne");
    room.setSecondParticipant("userTwo");
    when(matrixService.getDirectMessagingRoom(eq("userOne"), eq("userTwo"))).thenReturn(room);
    ResultActions response1 = mockMvc.perform(get(REST_PATH + "/dmRoom").with(simpleUser())
                                                                        .contentType(MediaType.APPLICATION_JSON)
                                                                        .param("firstParticipant", "userOne")
                                                                        .param("secondParticipant", "userTwo"));
    response1.andExpect(status().isOk());
  }

  @Test
  void syncUsersAndSpaces() throws Exception {
    ResultActions response = mockMvc.perform(get(REST_PATH + "/sync").with(adminUser()).contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isOk());
  }

  @Test
  void getMatrixRoomBySpaceId() throws Exception {
    ResultActions response = mockMvc.perform(get(REST_PATH).with(adminUser()).contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isBadRequest());

    response = mockMvc.perform(get(REST_PATH).with(simpleUser()).param("spaceId", "1").contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isNotFound());

    Space space = new Space();
    space.setAvatarUrl("/avatar/of/the/space");
    space.setDisplayName("Test space");
    when(spaceService.getSpaceById("1")).thenReturn(space);
    when(spaceService.isMember(space, SIMPLE_USER)).thenReturn(true);
    Room room = new Room();
    room.setRoomId("!testRoom:matrix.meeds.tn");
    room.setSpaceId("1");
    when(matrixService.getRoomBySpace(space)).thenReturn(room);
    response = mockMvc.perform(get(REST_PATH).with(simpleUser()).param("spaceId", "1").contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isOk());
  }

  @Test
  void linkSpaceToRoom() throws Exception {
    ResultActions response = mockMvc.perform(get(REST_PATH + "/linkRoom").with(simpleUser())
                                                                         .contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isBadRequest());
    response = mockMvc.perform(get(REST_PATH + "/linkRoom").with(simpleUser())
                                                           .param("spaceGroupId", "groupOne")
                                                           .param("roomId", "!roomIdenitifier:matrix.meeds.tn")
                                                           .contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isNotFound());
    Space space = new Space();
    space.setAvatarUrl("/avatar/of/the/space");
    space.setDisplayName("Test space");
    when(spaceService.getSpaceByGroupId("/spaces/groupOne")).thenReturn(space);
    response = mockMvc.perform(get(REST_PATH + "/linkRoom").with(simpleUser())
                                                           .param("spaceGroupId", "groupOne")
                                                           .param("roomId", "!roomIdenitifier:matrix.meeds.tn")
                                                           .contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isOk());
  }

  @Test
  void getByRoomId() throws Exception {
    ResultActions response = mockMvc.perform(get(REST_PATH + "/byRoomId").with(simpleUser())
                                                                         .contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isBadRequest());

    response = mockMvc.perform(get(REST_PATH + "/byRoomId").with(simpleUser())
                                                           .param("roomId", "!roomIdentifier")
                                                           .contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isForbidden());

    Room room = new Room();
    room.setRoomId("!testRoom:matrix.meeds.tn");
    room.setSpaceId("1");
    when(matrixService.getById("!testRoom:matrix.meeds.tn")).thenReturn(room);
    when(matrixService.canAccess(room, SIMPLE_USER)).thenReturn(true);
    response = mockMvc.perform(get(REST_PATH + "/byRoomId").with(simpleUser())
                                                           .param("roomId", "!testRoom:matrix.meeds.tn")
                                                           .contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isOk());
  }

  @Test
  void getUserDirectMessagingRooms() throws Exception {
    ResultActions response = mockMvc.perform(get(REST_PATH + "/dmRooms").with(simpleUser())
                                                                        .contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isBadRequest());

    response = mockMvc.perform(get(REST_PATH + "/dmRooms").with(simpleUser())
                                                          .param("user", "john")
                                                          .contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isOk());
    response.andExpect(content().string("{}"));

    Room room = new Room();
    room.setRoomId("!ThisIsARoom:matrix.meeds.tn");
    room.setSpaceId(null);
    room.setFirstParticipant("userOne");
    room.setSecondParticipant(SIMPLE_USER);
    when(matrixService.getMatrixDMRoomsOfUser(SIMPLE_USER)).thenReturn(Collections.singletonList(room));

    Identity userIdentity = new Identity();
    userIdentity.setRemoteId("userOne");
    userIdentity.setId("1");
    Profile profile = new Profile(userIdentity);
    profile.getProperties().put(USER_MATRIX_ID, "userOne");
    userIdentity.setProfile(profile);
    when(identityManager.getOrCreateUserIdentity("userOne")).thenReturn(userIdentity);

    response = mockMvc.perform(get(REST_PATH + "/dmRooms").with(simpleUser())
                                                          .param("user", SIMPLE_USER)
                                                          .contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isOk());
    response.andExpect(content().string("{\"@userOne:matrix.meeds.tn\":[\"!ThisIsARoom:matrix.meeds.tn\"]}"));
  }

  @SneakyThrows
  public static final <T> T fromJsonString(String value, Class<T> resultClass) {
    if (StringUtils.isBlank(value)) {
      return null;
    }
    return OBJECT_MAPPER.readValue(value, resultClass);
  }

  @Test
  void enableChat() throws Exception {
    ResultActions response = mockMvc.perform(put(REST_PATH + "/enable/1").with(simpleUser())
                                                                         .contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isForbidden());

    Space space1 = new Space();
    space1.setId(1);
    space1.setDisplayName("Space of Heroes");
    space1.setAvatarUrl("/Url/Of/Avatar.png");
    space1.setMembers(new String[] { "user1", "user2" });
    Room room = new Room();
    room.setRoomId("!testRoom:matrix.meeds.tn");
    room.setSpaceId("1");
    when(matrixService.enableSpaceChat(space1, true)).thenReturn(room);
    when(matrixService.getRoomBySpace(space1, true)).thenReturn(room);

    when(spaceService.getSpaceById("1")).thenReturn(space1);
    when(spaceService.canManageSpace(space1, SIMPLE_USER)).thenReturn(true);

    response = mockMvc.perform(put(REST_PATH + "/enable/1").with(simpleUser()).contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isOk());
  }

  @Test
  void disableChat() throws Exception {
    ResultActions response = mockMvc.perform(put(REST_PATH + "/disable/1").with(simpleUser())
                                                                          .contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isForbidden());

    Space space1 = new Space();
    space1.setId(1);
    space1.setDisplayName("Space of Heroes");
    space1.setAvatarUrl("/Url/Of/Avatar.png");
    space1.setMembers(new String[] { "user1", "user2" });
    Room room = new Room();
    room.setRoomId("!testRoom:matrix.meeds.tn");
    room.setSpaceId("1");
    when(matrixService.enableSpaceChat(space1, false)).thenReturn(room);
    when(matrixService.getRoomBySpace(space1, true)).thenReturn(room);

    when(spaceService.getSpaceById("1")).thenReturn(space1);
    when(spaceService.canManageSpace(space1, SIMPLE_USER)).thenReturn(true);

    response = mockMvc.perform(put(REST_PATH + "/disable/1").with(simpleUser()).contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isOk());
  }
}
