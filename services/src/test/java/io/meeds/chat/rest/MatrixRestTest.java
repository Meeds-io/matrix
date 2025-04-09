package io.meeds.chat.rest;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.meeds.chat.model.MatrixRoomPermissions;
import io.meeds.chat.model.Room;
import io.meeds.chat.rest.model.*;
import io.meeds.chat.service.MatrixService;
import io.meeds.chat.service.MatrixSynchronizationService;
import io.meeds.spring.web.security.PortalAuthenticationManager;
import io.meeds.spring.web.security.WebSecurityConfiguration;
import jakarta.servlet.Filter;
import lombok.SneakyThrows;
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
import org.exoplatform.social.rest.entity.ProfileEntity;
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

  MockedStatic<LinkProvider> LINK_PROVIDER = mockStatic(LinkProvider.class);
  MockedStatic<RestUtils> REST_UTILS = mockStatic(RestUtils.class);
  MockedStatic<EntityBuilder> ENTITY_BUILDER = mockStatic(EntityBuilder.class);

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
  }

  @AfterEach
  void tearDown() {
    LINK_PROVIDER.close();
    REST_UTILS.close();
    ENTITY_BUILDER.close();
  }

  @Test
  public void testProcessRooms() throws Exception {
    RoomEntity roomEntity = createRoomEntity(0);
    Room room = new Room();
    room.setRoomId("!testRoom0:matrix.meeds.tn");
    room.setSpaceId("spaceId");
    RoomList roomsList = new RoomList();
    roomsList.setTotalUnreadMessages(5);
    roomsList.setRooms(Collections.singletonList(roomEntity));
    when(matrixService.getById("!testRoom0")).thenReturn(room);
    Space space = new Space();
    space.setDisplayName("Space of Heroes");
    space.setAvatarUrl("/Url/Of/Avatar.png");
    when(spaceService.getSpaceById("spaceId")).thenReturn(space);
    ResultActions response = mockMvc.perform(post(REST_PATH + "/processRooms").with(simpleUser())
                                                                              .contentType(MediaType.APPLICATION_JSON)
                                                                              .content(asJsonString(roomsList)));
    response.andExpect(status().isOk());

    //
    room.setSpaceId(null);
    room.setFirstParticipant("root");
    room.setSecondParticipant("user");
    Identity identity = new Identity();
    identity.setRemoteId("user");
    identity.setId("1");
    Profile profile = new Profile();
    profile.setAvatarUrl("/avatar/of/root");
    profile.setProperty("firstName", "User");
    profile.setProperty("lastName", "Root");
    identity.setProfile(profile);
    when(identityManager.getOrCreateUserIdentity("root")).thenReturn(identity);
    when(matrixService.extractUserId(anyString())).thenCallRealMethod();
    when(resourceBundleService.getSharedString(anyString(), any())).thenReturn("This is a translated Message ");

    ResultActions response1 = mockMvc.perform(post(REST_PATH + "/processRooms").with(simpleUser())
                                                                               .contentType(MediaType.APPLICATION_JSON)
                                                                               .content(asJsonString(roomsList)));
    response1.andExpect(status().isOk());
  }

  private RoomList createRoomsList(int numberOfRooms) {
    List<RoomEntity> rooms = new ArrayList<>();
    for (int i = 0; i < numberOfRooms; i++) {
      rooms.add(createRoomEntity(i));
    }
    RoomList roomList = new RoomList();
    roomList.setTotalUnreadMessages(20);
    roomList.setRooms(rooms);
    return roomList;
  }

  private RoomEntity createRoomEntity(int index) {
    RoomEntity room = new RoomEntity();
    room.setId("!testRoom" + index + ":matrix.meeds.tn");
    room.setAvatarUrl("/avatar/" + index);
    room.setName("Chat number " + index);
    Member root = new Member("1", "root", "/user/avatar" + 1, System.currentTimeMillis());
    Member user = new Member("2", "user", "/user/avatar" + 2, System.currentTimeMillis());
    room.setMembers(Arrays.asList(user, root));
    room.setUnreadMessages(index);
    room.setPresence("online");
    room.setTopic("No topic");
    room.setUpdated(System.currentTimeMillis());
    Message message = new Message("This is a new message", "@root:matrix.meeds.tn");
    room.setLastMessage(message);
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
  void getIdentityByUserMatrixId() throws Exception {
    ResultActions response = mockMvc.perform(get(REST_PATH + "/userByMatrixId").with(simpleUser())
                                                                               .contentType(MediaType.APPLICATION_JSON)
                                                                               .param("userMatrixId", "@user:matrix.meeds.tn"));
    response.andExpect(status().isNotFound());

    Identity identity = new Identity();
    identity.setRemoteId("user");
    ENTITY_BUILDER.when(() -> EntityBuilder.buildEntityProfile(any(Profile.class), anyString(), anyString()))
                  .thenReturn(new ProfileEntity());
    REST_UTILS.when(() -> RestUtils.getRestUrl(anyString(), anyString(), anyString())).thenReturn("/matrix/rest/matrix");
    when(matrixService.findUserByMatrixId(eq("@user:matrix.meeds.tn"))).thenReturn(identity);
    ResultActions response1 = mockMvc.perform(get(REST_PATH + "/userByMatrixId").with(simpleUser())
                                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                                .param("userMatrixId", "@user:matrix.meeds.tn"));
    response1.andExpect(status().isOk());
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
    void redirectToProfile() throws Exception {
      Identity identity = new Identity();
      identity.setRemoteId("user");
      identity.setId("1");
      Profile profile = new Profile();
      profile.setAvatarUrl("/avatar/of/userOne");
      profile.setProperty("firstName", "User");
      profile.setProperty("lastName", "One");
      identity.setProfile(profile);
      when(matrixService.findUserByMatrixId(anyString())).thenReturn(identity);
      when(matrixService.extractUserId(anyString())).thenCallRealMethod();
      LINK_PROVIDER.when(() -> LinkProvider.getProfileUri(anyString())).thenReturn("/portal/meeds/profile/userOne");
      ResultActions response = mockMvc.perform(get(REST_PATH + "/profile/@userOne:matrix.meeds.tn").with(simpleUser()).contentType(MediaType.APPLICATION_JSON));
      response.andExpect(status().is3xxRedirection());
    }
}
