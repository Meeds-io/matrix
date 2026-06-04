package io.meeds.chat.rest;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.meeds.chat.rest.model.ChatSettings;
import io.meeds.chat.rest.model.SpaceTemplateSetting;
import io.meeds.chat.service.MatrixService;
import io.meeds.social.space.template.model.SpaceTemplate;
import io.meeds.social.space.template.service.SpaceTemplateService;
import io.meeds.spring.web.security.PortalAuthenticationManager;
import io.meeds.spring.web.security.WebSecurityConfiguration;
import jakarta.servlet.Filter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.exoplatform.commons.utils.PropertyManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.List;

import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_SERVER_NAME;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = { ChatAdministrationRest.class, PortalAuthenticationManager.class })
@ContextConfiguration(classes = { WebSecurityConfiguration.class })
@AutoConfigureWebMvc
@AutoConfigureMockMvc(addFilters = false)
@ExtendWith(MockitoExtension.class)
class ChatAdministrationRestTest {

  private static final String   SIMPLE_USER   = "user";

  private static final String   ADMIN_USER    = "admin";

  private static final String   TEST_PASSWORD = "testPassword";

  private static final String   REST_PATH     = "/chatAdministration";

  static final ObjectMapper     OBJECT_MAPPER;

  private MockMvc               mockMvc;

  @Autowired
  private SecurityFilterChain   filterChain;

  @Autowired
  private WebApplicationContext context;

  @MockitoBean
  private MatrixService         matrixService;

  @MockitoBean
  private SpaceTemplateService  spaceTemplateService;

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

  @Test
  void loadChatSettings() throws Exception {
    ResultActions response = mockMvc.perform(get(REST_PATH + "/settings").with(simpleUser())
                                                                         .contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isForbidden());

    response = mockMvc.perform(get(REST_PATH + "/settings").with(adminUser()).contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isServiceUnavailable());

    when(matrixService.isServiceAvailable()).thenReturn(true);

    response = mockMvc.perform(get(REST_PATH + "/settings").with(adminUser()).contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isOk());
    response.andExpect(content().contentType(MediaType.APPLICATION_JSON));
    ChatSettings chatSettings = fromJsonString(response.andReturn().getResponse().getContentAsString(), ChatSettings.class);
    assertNotNull(chatSettings);
    assertTrue(chatSettings.isChatEnabled());
    assertTrue(chatSettings.isPrivateRoomsEnabled());
    assertTrue(chatSettings.isSpaceRoomsEnabled());

    when(matrixService.loadChatSettings()).thenReturn(new ChatSettings(true, false, true, new ArrayList<>()));
    response = mockMvc.perform(get(REST_PATH + "/settings").with(adminUser()).contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isOk());
    response.andExpect(content().contentType(MediaType.APPLICATION_JSON));
    chatSettings = fromJsonString(response.andReturn().getResponse().getContentAsString(), ChatSettings.class);
    assertNotNull(chatSettings);
    assertTrue(chatSettings.isChatEnabled());
    assertFalse(chatSettings.isPrivateRoomsEnabled());
    assertTrue(chatSettings.isSpaceRoomsEnabled());

    SpaceTemplate spaceTemplate1 = new SpaceTemplate();
    spaceTemplate1.setId(1l);
    spaceTemplate1.setName("Circle");
    spaceTemplate1.setLayout("circle");
    spaceTemplate1.setIcon("circle-icon");
    SpaceTemplate spaceTemplate2 = new SpaceTemplate();
    spaceTemplate2.setId(2l);
    spaceTemplate2.setName("Team");
    spaceTemplate2.setLayout("team");
    spaceTemplate2.setIcon("team-icon");
    List<SpaceTemplate> spaceTemplates = List.of(spaceTemplate1, spaceTemplate2);

    when(spaceTemplateService.getSpaceTemplates(any(), any(), anyBoolean())).thenReturn(spaceTemplates);

    SpaceTemplateSetting spaceTemplateSetting = new SpaceTemplateSetting(1l, "circle", "icon", true, false);
    when(matrixService.loadChatSettings()).thenReturn(new ChatSettings(true,
                                                                       true,
                                                                       true,
                                                                       new ArrayList<>(List.of(spaceTemplateSetting))));

    response = mockMvc.perform(get(REST_PATH + "/settings").with(adminUser()).contentType(MediaType.APPLICATION_JSON));
    response.andExpect(status().isOk());
    response.andExpect(content().contentType(MediaType.APPLICATION_JSON));
    chatSettings = fromJsonString(response.andReturn().getResponse().getContentAsString(), ChatSettings.class);
    assertNotNull(chatSettings);
    assertTrue(chatSettings.isChatEnabled());
    assertTrue(chatSettings.isPrivateRoomsEnabled());
    assertTrue(chatSettings.isSpaceRoomsEnabled());

  }

  @Test
  void updateChatSettings() throws Exception {
    ResultActions response = mockMvc.perform(post(REST_PATH + "/settings").with(simpleUser())
                                                                          .contentType(MediaType.APPLICATION_JSON)
                                                                          .content(asJsonString(new ChatSettings())));
    response.andExpect(status().isForbidden());

    response = mockMvc.perform(post(REST_PATH + "/settings").with(adminUser())
                                                            .contentType(MediaType.APPLICATION_JSON)
                                                            .content(asJsonString(new ChatSettings())));
    response.andExpect(status().isServiceUnavailable());

    when(matrixService.isServiceAvailable()).thenReturn(true);
    when(matrixService.loadChatSettings()).thenReturn(new ChatSettings(true, true, true, new ArrayList<>()));

    response = mockMvc.perform(post(REST_PATH + "/settings").with(adminUser())
                                                            .contentType(MediaType.APPLICATION_JSON)
                                                            .content(asJsonString(new ChatSettings())));
    response.andExpect(status().isOk());
    response.andExpect(content().contentType(MediaType.APPLICATION_JSON));
    ChatSettings chatSettings = fromJsonString(response.andReturn().getResponse().getContentAsString(), ChatSettings.class);
    assertNotNull(chatSettings);
    assertTrue(chatSettings.isChatEnabled());
    assertTrue(chatSettings.isPrivateRoomsEnabled());
    assertTrue(chatSettings.isSpaceRoomsEnabled());
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

  @SneakyThrows
  public static final <T> T fromJsonString(String value, Class<T> resultClass) {
    if (StringUtils.isBlank(value)) {
      return null;
    }
    return OBJECT_MAPPER.readValue(value, resultClass);
  }
}
