package io.meeds.chat.rest;

import io.meeds.chat.service.model.ChatSettingsEntity;
import io.meeds.chat.service.model.ChatSettings;
import io.meeds.chat.service.MatrixService;
import io.meeds.chat.service.model.SpaceTemplateSetting;
import io.meeds.social.space.template.model.SpaceTemplate;
import io.meeds.social.space.template.model.SpaceTemplateFilter;
import io.meeds.social.space.template.service.SpaceTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.meeds.chat.service.utils.MatrixConstants.SPACE_CHAT_AUTHORIZED;
import static io.meeds.chat.service.utils.MatrixConstants.SPACE_CHAT_ENABLED_BY_DEFAULT;

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
  public ChatSettingsEntity loadChatSettings(HttpServletRequest request) {
    if (!matrixService.isServiceAvailable()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chat service is unavailable");
    }
    return matrixService.loadChatSettings(request.getRemoteUser(), request.getLocale());
  }

  @PostMapping(value = "settings", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  @Secured("administrators")
  @Operation(summary = "Enable the Chat feature", method = "GET", description = "Enable the Chat feature")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "503", description = "Service unavailable"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public ChatSettingsEntity updateChatSettings(HttpServletRequest request,
                                               @Parameter(description = "Settings of the Chat")
                                               @RequestBody
                                               ChatSettingsEntity chatSettingsEntity) {
    if (!matrixService.isServiceAvailable()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chat service is unavailable");
    }
    this.matrixService.saveChatSettings(new ChatSettings(chatSettingsEntity.isChatEnabled(),
                                                         chatSettingsEntity.isPrivateRoomsEnabled(),
                                                         chatSettingsEntity.isSpaceRoomsEnabled()));
    // Save changes to the space templates
    SpaceTemplateFilter spaceTemplateFilter = new SpaceTemplateFilter(request.getRemoteUser(), request.getLocale(), false);
    List<SpaceTemplate> spaceTemplates = spaceTemplateService.getSpaceTemplates(spaceTemplateFilter, Pageable.unpaged(), true);
    for (SpaceTemplate spaceTemplate : spaceTemplates) {
      SpaceTemplateSetting spaceTemplateSetting = chatSettingsEntity.getSpaceTemplateSetting()
                                                                    .stream()
                                                                    .filter(setting -> setting.getId() == spaceTemplate.getId())
                                                                    .findAny()
                                                                    .orElse(null);
      if (spaceTemplateSetting != null) {
        Map<String, String> extendedProperties = new HashMap<>();
        extendedProperties.put(SPACE_CHAT_AUTHORIZED, String.valueOf(spaceTemplateSetting.isAuthorized()));
        extendedProperties.put(SPACE_CHAT_ENABLED_BY_DEFAULT, String.valueOf(spaceTemplateSetting.isChatEnabledByDefault()));

        spaceTemplate.setExtendedProperties(extendedProperties);
        try {
          spaceTemplateService.updateSpaceTemplate(spaceTemplate);
        } catch (ObjectNotFoundException e) {
          throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not find and update the space template: " + spaceTemplate.getName());
        }
      }
    }

    return this.matrixService.loadChatSettings(request.getRemoteUser(), request.getLocale());
  }
}
