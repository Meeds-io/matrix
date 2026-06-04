package io.meeds.chat.rest;

import io.meeds.chat.rest.model.ChatSettings;
import io.meeds.chat.rest.model.RoomEntity;
import io.meeds.chat.rest.model.SpaceTemplateSetting;
import io.meeds.chat.service.MatrixService;
import io.meeds.social.space.template.model.SpaceTemplate;
import io.meeds.social.space.template.model.SpaceTemplateFilter;
import io.meeds.social.space.template.service.SpaceTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/chatAdministration")
@Tag(name = "/chatAdministration", description = "Manages Chat administration")
public class ChatAdministrationRest implements ResourceContainer {
  @Autowired
  private MatrixService        matrixService;

  @Autowired
  private SpaceTemplateService spaceTemplateService;

  @GetMapping(value = "settings", produces = MediaType.APPLICATION_JSON_VALUE)
  @Secured("administrators")
  @Operation(summary = "Check the Chat settings", method = "GET", description = "Check the Chat settings")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "503", description = "Service unavailable"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public ChatSettings loadChatSettings(HttpServletRequest request) {
    if (!matrixService.isServiceAvailable()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chat service is unavailable");
    }
    ChatSettings chatSettings = matrixService.loadChatSettings();
    if (chatSettings == null) {
      chatSettings = new ChatSettings(true, true, true, new ArrayList<>());
    }
    SpaceTemplateFilter spaceTemplateFilter = new SpaceTemplateFilter(request.getRemoteUser(), request.getLocale(), false);
    List<SpaceTemplate> spaceTemplates = spaceTemplateService.getSpaceTemplates(spaceTemplateFilter, Pageable.unpaged(), true);
    for (SpaceTemplate spaceTemplate : spaceTemplates) {
      SpaceTemplateSetting spaceTemplateSetting = chatSettings.getSpaceTemplateSetting()
                                                              .stream()
                                                              .filter(item -> spaceTemplate.getId() == item.getId())
                                                              .findAny()
                                                              .orElse(null);
      if (spaceTemplateSetting == null) {
        boolean isSpaceTemplateAuthorized = matrixService.getDefaultAuthorizedSpaceTemplates().contains(spaceTemplate.getLayout()); // we use layout because the space template name is Localized
        chatSettings.getSpaceTemplateSetting().add(new SpaceTemplateSetting(spaceTemplate.getId(), spaceTemplate.getName(), spaceTemplate.getIcon(),  isSpaceTemplateAuthorized, false));
      } else {
        spaceTemplateSetting.setName(spaceTemplate.getName());
        spaceTemplateSetting.setIcon(spaceTemplate.getIcon());
      }
    }
    return chatSettings;
  }

  @PostMapping(value = "settings", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  @Secured("administrators")
  @Operation(summary = "Enable the Chat feature", method = "GET", description = "Enable the Chat feature")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "503", description = "Service unavailable"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public ChatSettings updateChatSettings(@Parameter(description = "Settings of the Chat")
  @RequestBody
  ChatSettings chatSettings) {
    if (!matrixService.isServiceAvailable()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chat service is unavailable");
    }
    this.matrixService.setChatSettings(chatSettings);
    return this.matrixService.loadChatSettings();
  }
}
