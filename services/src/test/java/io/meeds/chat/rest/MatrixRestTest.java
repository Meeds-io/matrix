package io.meeds.chat.rest;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.meeds.chat.rest.model.Member;
import io.meeds.chat.rest.model.Message;
import io.meeds.chat.rest.model.RoomEntity;
import io.meeds.chat.rest.model.RoomList;
import io.meeds.chat.service.MatrixService;
import io.meeds.spring.web.security.PortalAuthenticationManager;
import io.meeds.spring.web.security.WebSecurityConfiguration;
import jakarta.servlet.Filter;
import lombok.SneakyThrows;
import org.exoplatform.commons.api.notification.service.storage.NotificationService;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

import java.sql.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = { MatrixRest.class, PortalAuthenticationManager.class })
@ContextConfiguration(classes = { WebSecurityConfiguration.class })
@AutoConfigureWebMvc
@AutoConfigureMockMvc(addFilters = false)
@ExtendWith(MockitoExtension.class)
class MatrixRestTest {

  private static final String SIMPLE_USER   = "user";

  private static final String TEST_PASSWORD = "testPassword";

  private static final String REST_PATH     = "/matrix";      // NOSONAR


  static final ObjectMapper OBJECT_MAPPER;


  @Autowired
  private SecurityFilterChain filterChain;

  @Autowired
  private WebApplicationContext context;

  @MockBean
  private SpaceService spaceService;

  @MockBean
  private MatrixService matrixService;

  @MockBean
  private IdentityManager identityManager;

  @MockBean
  private NotificationService notificationService;

  private MockMvc mockMvc;

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
    mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters(filterChain.getFilters().toArray(new Filter[0]))
            .build();
  }

  @Test
  public void testProcessRooms() throws Exception {
    RoomList roomsList = createRoomsList(3);
    when(matrixService.processRooms(any(RoomList.class), anyString())).thenReturn(roomsList);
    ResultActions response = mockMvc.perform(post(REST_PATH + "/processRooms").with(simpleUser())
            .contentType(MediaType.APPLICATION_JSON)
            .content(asJsonString(roomsList)));
    response.andExpect(status().isOk());
  }

  private RoomList createRoomsList(int numberOfRooms) {
    List<RoomEntity> rooms = new ArrayList<>();
    for(int i = 0; i < numberOfRooms; i++) {
      rooms.add(createRoomEntity(i));
    }
    RoomList roomList = new RoomList();
    roomList.setTotalUnreadMessages(20);
    roomList.setRooms(rooms);
    return roomList;
  }

  private RoomEntity createRoomEntity(int index) {
    RoomEntity room = new RoomEntity();
    room.setId(String.valueOf(index));
    room.setAvatarUrl("/avatar/" + index);
    room.setName("Chat number " + index);
    Member root = new Member("1", "root", "/user/avatar" + 1, System.currentTimeMillis());
    Member user = new Member("2", "root", "/user/avatar" + 2, System.currentTimeMillis());
    room.setMembers(Arrays.asList(user, root));
    room.setUnreadMessages(index);
    room.setPresence("online");
    room.setTopic("No topic");
    room.setUpdated(System.currentTimeMillis());
    Message message = new Message("This is a new message", "@user:server.matrix.com");
    room.setLastMessage(message);
    return room;
  }

  private RequestPostProcessor simpleUser() {
    return user(SIMPLE_USER).password(TEST_PASSWORD)
            .authorities(new SimpleGrantedAuthority("users"));
  }


  @SneakyThrows
  public static String asJsonString(final Object obj) {
    return OBJECT_MAPPER.writeValueAsString(obj);
  }
}
