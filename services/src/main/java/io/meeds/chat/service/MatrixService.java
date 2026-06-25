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
package io.meeds.chat.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.meeds.chat.entity.RoomStatus;
import io.meeds.chat.model.ChatConversation;
import io.meeds.chat.model.ChatMessage;
import io.meeds.chat.model.ChatUnread;
import io.meeds.chat.model.MatrixMessage;
import io.meeds.chat.model.MatrixUnreadRoom;
import io.meeds.chat.model.MatrixRoomPermissions;
import io.meeds.chat.model.Room;
import io.meeds.chat.rest.model.MediaInfo;
import io.meeds.chat.service.utils.MatrixHttpClient;
import io.meeds.chat.service.utils.MatrixUnauthorizedException;
import io.meeds.chat.storage.MatrixRoomStorage;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.exoplatform.commons.ObjectAlreadyExistsException;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.file.model.FileItem;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.commons.utils.PropertyManager;

import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.cache.CacheService;
import org.exoplatform.services.cache.ExoCache;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.Membership;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.Query;
import org.exoplatform.services.organization.User;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.core.storage.api.IdentityStorage;
import org.exoplatform.ws.frameworks.json.impl.JsonException;
import org.exoplatform.ws.frameworks.json.impl.JsonGeneratorImpl;
import org.exoplatform.ws.frameworks.json.value.JsonValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

import static io.meeds.chat.service.utils.MatrixConstants.*;
import static org.apache.commons.lang3.StringUtils.isNumeric;

@Service
public class MatrixService {

  private static final Log               LOG                       = ExoLogger.getLogger(MatrixService.class);

  @Autowired
  private MatrixRoomStorage              matrixRoomStorage;

  @Autowired
  private IdentityManager                identityManager;

  @Autowired
  private IdentityStorage                identityStorage;

  @Autowired
  private OrganizationService            organizationService;

  @Autowired
  private SpaceService                   spaceService;

  @Autowired
  private MatrixHttpClient               matrixHttpClient;

  /**
   * -- GETTER -- Checks if the Matrix service is available
   */
  @Getter
  private boolean                        serviceAvailable;

  private String                         matrixAccessToken;

  private final ExoCache<String, String> userMatrixIdsCache;

  private final ExoCache<String, String> userAccessTokensCache;

  public static final String             USER_MATRIX_ID_CACHE_NAME = "chat.UserMatrixId";

  public static final String             USER_ACCESS_TOKEN_CACHE_NAME = "chat.UserAccessToken";

  public MatrixService(MatrixRoomStorage matrixRoomStorage,
                       IdentityManager identityManager,
                       IdentityStorage identityStorage,
                       OrganizationService organizationService,
                       MatrixHttpClient matrixHttpClient,
                       CacheService cacheService) {
    this.matrixRoomStorage = matrixRoomStorage;
    this.identityManager = identityManager;
    this.identityStorage = identityStorage;
    this.organizationService = organizationService;
    this.matrixHttpClient = matrixHttpClient;
    this.userMatrixIdsCache = cacheService.getCacheInstance(USER_MATRIX_ID_CACHE_NAME);
    this.userAccessTokensCache = cacheService.getCacheInstance(USER_ACCESS_TOKEN_CACHE_NAME);
  }

  @PostConstruct
  public void init() {
    try {
      this.getMatrixAccessToken();

      String userFullMatrixID = getUserFullMatrixID(PropertyManager.getProperty(MATRIX_ADMIN_USERNAME));
      this.overrideAdminRateLimit(userFullMatrixID);
      String displayName = System.getProperty(MATRIX_ADMIN_DISPLAY_NAME, "Chat Bot");
      if (StringUtils.isNotBlank(displayName)) {
        this.updateUserDisplayName(userFullMatrixID, displayName);
      }
      this.serviceAvailable = true;
    } catch (Exception e) {
      LOG.error("Could not initialize Matrix service, the service is unavailable", e.getMessage());
      this.serviceAvailable = false;
    }
  }

  /**
   * Convert the user id into the full Matrx ID format
   * 
   * @param userName
   * @return formatted username
   */
  public String getUserFullMatrixID(String userName) {
    if (StringUtils.isNotBlank(userName) && userName.startsWith("@") && userName.indexOf(":") > 0) { // NOSONAR
      return userName;
    }
    return "@" + userName + ":" + PropertyManager.getProperty(MATRIX_SERVER_NAME);
  }

  private String getMatrixAccessToken() throws JsonException, IOException, InterruptedException {
    if (StringUtils.isBlank(this.matrixAccessToken)) {
      try {
        String jwtAccessToken = this.getJWTSessionToken(PropertyManager.getProperty(MATRIX_ADMIN_USERNAME));
        this.matrixAccessToken = matrixHttpClient.getAccessToken(jwtAccessToken);
      } catch (JsonException | IOException e) {
        LOG.error("Could not get Matrix Access token for the administrator account !", e);
        throw e;
      }
    }
    return this.matrixAccessToken;
  }

  /**
   * Retrieves an access token for a user using a JWT token
   * 
   * @param jwtToken
   * @return String the access token
   */
  public String getAccessToken(String jwtToken) throws JsonException, IOException, InterruptedException {
    return matrixHttpClient.getAccessToken(jwtToken);
  }

  /**
   * Invalidate a specific access token
   * 
   * @param accessToken
   * @return true if success
   */
  public boolean invalidateAccessToken(String accessToken) {
    try {
      return matrixHttpClient.invalidateAccessToken(accessToken);
    } catch (IOException e) {
      LOG.error("Could not invalidate an access token !", e);
      return false;
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
      LOG.error("Could not invalidate an access token !", interruptedException);
      return false;
    }
  }

  public void updateUserDisplayName(String matrixFullID, String newDisplayName) {
    try {
      String currentUserDisplayName = matrixHttpClient.getUserDisplayName(matrixFullID, getMatrixAccessToken());
      if (StringUtils.isNotBlank(currentUserDisplayName) && !currentUserDisplayName.equals(newDisplayName)) {
        matrixHttpClient.updateUserDisplayName(matrixFullID, newDisplayName, getMatrixAccessToken());
      }
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      LOG.error("Couldn't update the display name of the user {}", matrixFullID, ie);
    } catch (Exception e) {
      LOG.error("Couldn't update the display name of the user {}", matrixFullID, e);
    }
  }

  /**
   * Returns the ID of the room linked to a space
   * 
   * @param space the space
   * @return the roomId linked to the space
   */
  public Room getRoomBySpace(Space space) {
    return getRoomBySpaceId(Long.valueOf(space.getId()));
  }

  /**
   * Returns the ID of the room linked to a space
   *
   * @param space the space
   * @return the roomId linked to the space
   */
  public Room getRoomBySpace(Space space, boolean includeDisabled) {
    return getRoomBySpaceId(Long.valueOf(space.getId()), includeDisabled);
  }

  /**
   * Returns the ID of the room linked to a space
   *
   * @param spaceId the space Id
   * @return the roomId linked to the space
   */
  public Room getRoomBySpaceId(Long spaceId) {
    return this.getRoomBySpaceId(spaceId, false);
  }

  /**
   * Returns the ID of the room linked to a space
   *
   * @param spaceId the space Id
   * @return the roomId linked to the space
   */
  public Room getRoomBySpaceId(Long spaceId, boolean includeDisabled) {
    return matrixRoomStorage.getMatrixRoomBySpaceId(spaceId, includeDisabled);
  }

  /**
   * Get a room by its technical ID
   * 
   * @param roomId the room technical ID
   * @return Room
   */
  public Room getById(String roomId) {
    return this.getById(roomId, false);
  }

  /**
   * Get a room by its technical ID
   *
   * @param roomId the room technical ID
   * @param includeDisabled return room even if it is disabled
   * @return Room
   */
  public Room getById(String roomId, boolean includeDisabled) {
    if (StringUtils.isNotBlank(roomId)) {
      roomId = extractRoomId(roomId);
      return matrixRoomStorage.getById(roomId, includeDisabled);
    }
    return null;
  }

  /**
   * records the matrix ID of the room linked to the space
   *
   * @param space the Space
   * @param roomId the ID of the matrix room
   * @return the room ID
   */
  public Room linkSpaceToMatrixRoom(Space space, String roomId) {
    return matrixRoomStorage.saveRoomForSpace(Long.valueOf(space.getId()), roomId);
  }

  /**
   * Creates a room on Matrix for the space
   * 
   * @param space the space
   * @return String representing the room id
   * @throws Exception when creating the room fails on Matrix
   */
  public String createRoomForSpaceOnMatrix(Space space) throws Exception {
    String teamDisplayName = space.getDisplayName();
    String description = space.getDescription() != null ? space.getDescription() : "";
    return matrixHttpClient.createRoom(teamDisplayName, description, getMatrixAccessToken());
  }

  /**
   * Get the matrix ID of a defined user
   * 
   * @param userName of the user
   * @return the matrix ID
   */
  public String getMatrixIdForUser(String userName) {
    Identity newMember = identityManager.getOrCreateUserIdentity(userName);
    Profile newMemberProfile = newMember.getProfile();
    if (StringUtils.isNotBlank((String) newMemberProfile.getProperty(USER_MATRIX_ID))) {
      return newMemberProfile.getProperty(USER_MATRIX_ID).toString();
    }
    return null;
  }

  /**
   * Returns the JWT for user authentication
   * 
   * @param userNameOnMatrix the username of the current user
   * @return String
   */
  public String getJWTSessionToken(String userNameOnMatrix) {
    Date expirtaionDate = Date.from(Instant.now().plusSeconds(7 * 24 * 60 * 60L)); // adds one week to the current instant
    userNameOnMatrix = userNameOnMatrix.replaceAll("[^a-zA-Z0-9=_\\-\\.\\/+]+", "-");
    return Jwts.builder()
               .setSubject(userNameOnMatrix)
               .signWith(Keys.hmacShaKeyFor(PropertyManager.getProperty(MATRIX_JWT_SECRET).getBytes()))
               .setExpiration(expirtaionDate)
               .compact();

  }

  /**
   * Saves a new user on Matrix
   * 
   * @param user the user identity
   * @param isNew if the user has been just created
   * @return the matrix user ID
   * @throws JsonException
   * @throws IOException
   * @throws InterruptedException
   */
  public String saveUserAccount(Identity user, boolean isNew) throws JsonException, IOException, InterruptedException {
    return saveUserAccount(user, isNew, false, true);
  }

  /**
   * Saves a new user on Matrix
   * 
   * @param user the user to create on Matrix
   * @param isNew boolean if the user is new, then true
   * @return String the matrix user ID
   */
  public String saveUserAccount(Identity user,
                                boolean isNew,
                                boolean isEnableUserOperation,
                                boolean isUserEnabled) throws JsonException, IOException, InterruptedException {

    String matrixUserId = user.getRemoteId();
    if (isNumeric(user.getRemoteId())) {
      String prefix =
                    StringUtils.isNotBlank(PropertyManager.getProperty(MATRIX_USERNAME_PREFIX)) ? PropertyManager.getProperty(MATRIX_USERNAME_PREFIX)
                                                                                                : "u";
      matrixUserId = prefix + user.getRemoteId();
    }
    matrixUserId = cleanMatrixUsername(matrixUserId);
    String matrixId = matrixHttpClient.saveUserAccount(user,
                                                       matrixUserId,
                                                       isNew,
                                                       this.getMatrixAccessToken(),
                                                       isEnableUserOperation,
                                                       isUserEnabled);
    Identity userIdentity = identityManager.getOrCreateUserIdentity(user.getRemoteId());
    Profile userProfile = userIdentity.getProfile();
    if (StringUtils.isNotBlank(matrixId) && (userProfile.getProperty(USER_MATRIX_ID) == null
        || StringUtils.isBlank(userProfile.getProperty(USER_MATRIX_ID).toString()))) {
      userProfile.getProperties().put(USER_MATRIX_ID, matrixId);
      identityManager.updateProfile(userProfile);
    }
    return matrixId;
  }

  public String uploadFileOnMatrix(String fileName, String mimeType, byte[] fileBytes) throws JsonException,
                                                                                       IOException,
                                                                                       InterruptedException {
    return matrixHttpClient.uploadFile(fileName, mimeType, fileBytes, this.getMatrixAccessToken());
  }

  public void updateUserAvatar(Profile profile, String userMatrixID) throws JsonException, IOException, InterruptedException {
    try {
      if (StringUtils.isNotBlank(userMatrixID)) {
        FileItem avatarFileItem = identityStorage.getAvatarFile(profile.getIdentity());
        String mimeType = "image/jpg";
        if (avatarFileItem != null && avatarFileItem.getFileInfo() != null
            && !"DEFAULT_AVATAR".equals(avatarFileItem.getFileInfo().getName())) {
          if (!"application/octet-stream".equals(avatarFileItem.getFileInfo().getMimetype())) {
            mimeType = avatarFileItem.getFileInfo().getMimetype();
          }
          String userAvatarUrl = this.uploadFileOnMatrix("avatar-of-" + profile.getIdentity().getRemoteId() + ".jpg",
                                                         mimeType,
                                                         avatarFileItem.getAsByte());
          if (StringUtils.isNotBlank(userMatrixID) && StringUtils.isNotBlank(userAvatarUrl)) {
            matrixHttpClient.updateUserAvatar(userMatrixID, userAvatarUrl, this.getMatrixAccessToken());
          }
        }
      }
    } catch (Exception e) {
      LOG.error("Could not save the avatar of {} on Matrix", profile.getFullName(), e);
    }
  }

  public void disableAccount(String matrixUsername) throws JsonException, IOException, InterruptedException {
    matrixHttpClient.disableAccount(matrixUsername, false, this.getMatrixAccessToken());
  }

  public boolean updateRoomDescription(String roomId,
                                       String description) throws JsonException, IOException, InterruptedException {
    return matrixHttpClient.updateRoomDescription(roomId, description, this.getMatrixAccessToken());
  }

  public void updateRoomAvatar(Space space, String roomId) throws Exception {
    String mimeType = "";
    String avatarURL;
    String fileExtension = "";
    String fileName = "";
    try {
      Identity identity = identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space.getPrettyName());
      FileItem spaceAvatarFileItem = identityManager.getAvatarFile(identity);
      byte[] imageBytes = new byte[0];
      if (space.getAvatarAttachment() != null && space.getAvatarAttachment().getImageBytes() != null) {
        imageBytes = space.getAvatarAttachment().getImageBytes();
        mimeType = space.getAvatarAttachment().getMimeType();
        fileName = space.getAvatarAttachment().getFileName();
        fileExtension = StringUtils.isNotBlank(fileName) && fileName.contains(".") ? fileName.substring(fileName.lastIndexOf("."))
                                                                                   : ".jpg";
      } else if ((spaceAvatarFileItem != null && spaceAvatarFileItem.getAsByte() != null)) {
        imageBytes = spaceAvatarFileItem.getAsByte();
        mimeType = spaceAvatarFileItem.getFileInfo().getMimetype();
        fileName = spaceAvatarFileItem.getFileInfo().getName();
        fileExtension = StringUtils.isNotBlank(fileName) && fileName.contains(".") ? fileName.substring(fileName.lastIndexOf("."))
                                                                                   : ".jpg";
      }
      if ("application/octet-stream".equals(mimeType)) {
        mimeType = "image/jpg";
      }
      fileName = "avatar-space-%s%s".formatted(space.getPrettyName(), fileExtension);
      if (StringUtils.isNotBlank(roomId) && imageBytes != null) {
        avatarURL = this.uploadFileOnMatrix(fileName, mimeType, imageBytes);
        matrixHttpClient.updateRoomAvatar(roomId, avatarURL, this.getMatrixAccessToken());
      }
    } catch (Exception e) {
      throw new Exception("Could not save the avatar of the space %s".formatted(space.getDisplayName()), e);
    }
  }

  public MatrixRoomPermissions getRoomSettings(String roomId) throws JsonException, IOException, InterruptedException {
    return matrixHttpClient.getRoomSettings(roomId, this.getMatrixAccessToken());
  }

  public boolean updateRoomSettings(String roomId, MatrixRoomPermissions matrixRoomPermissions) throws JsonException,
                                                                                                IOException,
                                                                                                InterruptedException {
    return matrixHttpClient.updateRoomSettings(roomId, matrixRoomPermissions, this.getMatrixAccessToken()) != null;
  }

  public void kickUserFromRoom(String roomId, String matrixIdOfUser, String message) throws JsonException,
                                                                                     IOException,
                                                                                     InterruptedException {
    matrixHttpClient.kickUserFromRoom(roomId, matrixIdOfUser, message, this.getMatrixAccessToken());
  }

  public void joinUserToRoom(String roomId, String matrixIdOfUser) throws JsonException, IOException, InterruptedException {
    matrixHttpClient.joinUserToRoom(roomId, matrixIdOfUser, this.getMatrixAccessToken());
  }

  public void renameRoom(String roomId, String spaceDisplayName) throws JsonException, IOException, InterruptedException {
    matrixHttpClient.renameRoom(roomId, spaceDisplayName, this.getMatrixAccessToken());
  }

  public void makeUserAdminInRoom(String matrixRoomId,
                                  String matrixIdOfUser) throws JsonException, IOException, InterruptedException {
    matrixHttpClient.makeUserAdminInRoom(matrixRoomId, matrixIdOfUser, this.getMatrixAccessToken());
  }

  /**
   * This function do : - Create a room on Matrix - Links the room to the space on
   * Meeds - Update room permissions
   * 
   * @param space The space
   * @return The room ID
   * @throws Exception
   */
  public String createRoom(Space space) throws Exception {
    String teamDisplayName = space.getDisplayName();
    String description = space.getDescription() != null ? space.getDescription() : "";
    String matrixRoomId = matrixHttpClient.createRoom(teamDisplayName, description, this.getMatrixAccessToken());
    if (StringUtils.isNotBlank(matrixRoomId)) {
      // link the room on Meeds server
      this.linkSpaceToMatrixRoom(space, matrixRoomId);
      // Disable inviting user but for Moderators
      MatrixRoomPermissions matrixRoomPermissions = this.getRoomSettings(matrixRoomId);
      matrixRoomPermissions.setInvite(ADMIN_ROLE);
      this.updateRoomSettings(matrixRoomId, matrixRoomPermissions);
    }
    return matrixRoomId;
  }

  public Room getDirectMessagingRoom(String firstParticipant, String secondParticipant) {
    return matrixRoomStorage.getDirectMessagingRoom(firstParticipant, secondParticipant);
  }

  /**
   * Delete a Matrix room
   *
   * @param roomId the room identifier
   */
  public void deleteRoom(String roomId) throws JsonException, IOException, InterruptedException {
    boolean success = matrixHttpClient.deleteRoom(roomId, getMatrixAccessToken());
    if (success) {
      matrixRoomStorage.removeMatrixRoom(roomId);
    }
  }

  public Room createDirectMessagingRoom(Room directMessagingRoom) throws ObjectAlreadyExistsException {
    String firstParticipant = directMessagingRoom.getFirstParticipant();
    String secondParticipant = directMessagingRoom.getSecondParticipant();
    if (StringUtils.isBlank(firstParticipant) || StringUtils.isBlank(secondParticipant)) {
      throw new IllegalArgumentException("The ids of the room participants should not be null");
    }
    if (identityManager.getOrCreateUserIdentity(directMessagingRoom.getFirstParticipant()) == null
        || identityManager.getOrCreateUserIdentity(directMessagingRoom.getSecondParticipant()) == null) {
      throw new IllegalArgumentException("The ids of the room participants should be valid user identity ids");
    }
    Room matrixRoom = matrixRoomStorage.getDirectMessagingRoom(firstParticipant, secondParticipant);
    if (matrixRoom == null) {
      return matrixRoomStorage.saveDirectMessagingRoom(directMessagingRoom.getFirstParticipant(),
                                                       directMessagingRoom.getSecondParticipant(),
                                                       directMessagingRoom.getRoomId());
    } else {
      throw new ObjectAlreadyExistsException("A direct messaging room is already created for the users %s and %s".formatted(firstParticipant,
                                                                                                                            secondParticipant));
    }
  }

  public List<Room> getMatrixDMRoomsOfUser(String user) {
    return matrixRoomStorage.getMatrixDMRoomsOfUser(user);
  }

  /**
   * Checks if the user is a member of a group defined by its name
   * 
   * @param userName the user name
   * @param groupId the user group ID
   * @return true if the user is a member of the group
   * @throws Exception
   */
  private boolean isUserMemberOfGroup(String userName, String groupId) throws Exception {
    RequestLifeCycle.begin(ExoContainerContext.getCurrentContainer());
    try {
      Collection<Membership> userMemberships = this.organizationService.getMembershipHandler()
                                                                       .findMembershipsByUserAndGroup(userName, groupId);
      return !userMemberships.isEmpty();
    } finally {
      RequestLifeCycle.end();
    }
  }

  /**
   * Checks if the user is a member of a group defined by its name
   * 
   * @param userName the userName
   * @param groups the list of groups
   * @return true if the user is a member of the group
   * @throws Exception
   */
  public boolean isUserMemberOfGroups(String userName, String... groups) throws Exception {
    for (String group : groups) {
      if (this.isUserMemberOfGroup(userName, group)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns a list of all space rooms
   * 
   * @return List of Space rooms
   */
  public List<Room> getSpaceRooms() {
    return matrixRoomStorage.getSpaceRooms();
  }

  /**
   * Extracts the user ID from the full User Id on Matrix
   * 
   * @param fullMatrixUserId the full Matrix user Id
   * @return the user Identifier
   */
  public String extractUserId(String fullMatrixUserId) {
    String serverName = PropertyManager.getProperty(MATRIX_SERVER_NAME);
    if (fullMatrixUserId.startsWith("@") && fullMatrixUserId.endsWith(serverName)) {
      return fullMatrixUserId.substring(1, fullMatrixUserId.indexOf(":"));
    }
    return fullMatrixUserId;
  }

  /**
   * Extracts the room ID from the full room Id on Matrix
   *
   * @param fullMatrixUserId the full Matrix user Id
   * @return the user Identifier
   */
  public String extractRoomId(String fullMatrixUserId) {
    String serverName = PropertyManager.getProperty(MATRIX_SERVER_NAME);
    if (fullMatrixUserId.startsWith("!") && fullMatrixUserId.endsWith(serverName)) {
      return fullMatrixUserId.substring(0, fullMatrixUserId.indexOf(":"));
    }
    return fullMatrixUserId;
  }

  /**
   * update the user presence status on Matrix
   * 
   * @param userIdOnMatrix the user Id on Matrix
   * @param presence the presence value: online , unavailable, offline
   * @param statusMessage a personalized status message
   */
  public String updateUserPresence(String userIdOnMatrix, String presence, String statusMessage) {
    try {
      return matrixHttpClient.setUserPresence(userIdOnMatrix, presence, statusMessage, getMatrixAccessToken());
    } catch (Exception e) {
      LOG.error("Could not update the presence onf the user {} on Matrix", userIdOnMatrix, e);
    }
    return null;
  }

  /**
   * Checks if the user able to access the room
   * 
   * @param room the room
   * @param userName the username of the user
   * @return true if he has access, false otherwise
   */
  public boolean canAccess(Room room, String userName) {
    if (room.getSpaceId() == null) {
      return userName.equals(room.getFirstParticipant()) || userName.equals(room.getSecondParticipant());
    } else {
      Space space = spaceService.getSpaceById(room.getSpaceId());
      return spaceService.canViewSpace(space, userName);
    }
  }

  /**
   * Enable the chat for the space
   *
   * @param space the space where the chat will be disabled/enabled
   * @param enable true to enable the room, false to disable it
   * @return Room the updated room
   */
  public Room enableSpaceChat(Space space, boolean enable) throws ObjectNotFoundException {
    if (space == null) {
      throw new IllegalArgumentException("The space should not be null");
    }
    Room spaceRoom = this.getRoomBySpace(space, true);
    if (spaceRoom == null) {
      throw new ObjectNotFoundException("Could not find a chat room for the space " + space.getDisplayName());
    }
    String matrixAdminUsername = PropertyManager.getProperty(MATRIX_ADMIN_USERNAME);

    RoomStatus currentRoomStatus = RoomStatus.valueOf(spaceRoom.getStatus());
    this.changeRoomStatus(spaceRoom.getRoomId(), enable ? RoomStatus.ENABLE_IN_PROGRESS : RoomStatus.DISABLED_IN_PROGRESS);
    try {
      for (String member : space.getMembers()) {
        String matrixIdOfMember = getMatrixIdForUser(member);
        if (!matrixAdminUsername.equals(matrixIdOfMember)) {
          try {
            if (enable) {
              joinUserToRoom(spaceRoom.getRoomId(), matrixIdOfMember);
            } else {
              kickUserFromRoom(spaceRoom.getRoomId(),
                               matrixIdOfMember,
                               "the Chat was disabled for the space %s, thus the user %s is removed from the chat members".formatted(space.getDisplayName(),
                                                                                                                                     member));
            }
          } catch (Exception e) {
            if (e instanceof InterruptedException) {
              Thread.currentThread().interrupt();
            }
            LOG.error("couldn't invite / remove the user {} from the room {}", matrixAdminUsername, spaceRoom.getRoomId(), e);
          }
        }
      }
      spaceRoom = this.changeRoomStatus(spaceRoom.getRoomId(), enable ? RoomStatus.ENABLED : RoomStatus.DISABLED);
    } catch (Exception e) {
      // reset room to original status
      spaceRoom = this.changeRoomStatus(spaceRoom.getRoomId(), currentRoomStatus);
      LOG.error("An error occurred when enabling/disabling the room {}", matrixAdminUsername, spaceRoom.getRoomId(), e);
    }
    return spaceRoom;
  }

  /**
   * Enable or disable a room
   * 
   * @param roomId the ID of the Chat room
   * @param status the status to set: ENABLED, DISABLED, ENABLE_IN_PROGRESS,
   *          DISABLED_IN_PROGRESS
   * @return Room the updated room
   */
  private Room changeRoomStatus(String roomId, RoomStatus status) {
    return matrixRoomStorage.setRoomEnabled(roomId, status);
  }

  /**
   * Overrides the rate limits for the current administrator to unlimited
   * 
   * @param adminUserId the admin username on Matrix
   */
  public void overrideAdminRateLimit(String adminUserId) {
    try {
      String currentRateLimits = matrixHttpClient.getOverriddenRateLimitForUser(adminUserId, getMatrixAccessToken());
      JsonValue currentLimits = new JsonGeneratorImpl().createJsonObjectFromString(currentRateLimits);
      JsonValue messagePerSecond = currentLimits.getElement("messages_per_second");
      JsonValue burstCount = currentLimits.getElement("burst_count");
      if (messagePerSecond == null || burstCount == null || messagePerSecond.getIntValue() > 0 || burstCount.getIntValue() > 0) {
        // set the messages per second to zero -> unlimited
        // set the burst count to 0 : No burst count
        matrixHttpClient.overrideRateLimitForUser(adminUserId, 0, 0, getMatrixAccessToken());
      }
    } catch (Exception e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      LOG.error("Could not update the rate limits for the Matrix admin user", e);
    }
  }

  /**
   * Retrieves the message related to the event ID
   * 
   * @param eventId the event ID
   * @param roomId the room ID
   * @return MatrixMessage the received message
   */
  public MatrixMessage getRoomEvent(String eventId, String roomId, String token) {
    try {
      String accessToken = StringUtils.isNotBlank(token) ? token : getMatrixAccessToken();
      MatrixMessage message = matrixHttpClient.getEventById(eventId, roomId, accessToken);
      if (message != null && "m.room.message".equals(message.getType())) {
        return message;
      }
    } catch (IOException | InterruptedException | JsonException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      LOG.error("Could not retrieve the Room event {}", eventId, e);
    }
    return null;
  }

  /**
   * Retrieves the message related to the event ID
   *
   * @param eventId the event ID
   * @param roomId the room ID
   * @return MatrixMessage the received message
   */
  public MatrixMessage getRoomEvent(String eventId, String roomId) {
    try {
      return getRoomEvent(eventId, roomId, getMatrixAccessToken());
    } catch (IOException | JsonException e) {
      LOG.error("Could not retrieve the Room event {}", eventId, e);
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
      LOG.error("Could not retrieve the Room event {}", eventId, interruptedException);
    }
    return null;
  }

  /**
   * Finds the identity of a user based on its Matrix ID and a space where he is a
   * member
   * 
   * @param matrixId
   * @param space
   * @return
   */
  public Identity findSpaceMemberByMatrixId(String matrixId, Space space) {
    matrixId = extractUserId(matrixId);
    if (userMatrixIdsCache.get(matrixId) != null) {
      return identityManager.getOrCreateUserIdentity(userMatrixIdsCache.get(matrixId));
    }
    for (String member : space.getMembers()) {
      Identity memberIdentity = identityManager.getOrCreateUserIdentity(member);
      if (memberIdentity != null && memberIdentity.getProfile().getProperties().containsKey(USER_MATRIX_ID)
          && memberIdentity.getProfile().getProperties().get(USER_MATRIX_ID).equals(matrixId)) {
        userMatrixIdsCache.put(matrixId, member);
        return memberIdentity;
      }
    }
    return null;
  }

  /**
   * find the user on Meeds based on his id on Matrix
   * 
   * @param userMatrixId the ID of the user on Matrix
   * @return the user Identity
   */
  public String findUserByMatrixId(String userMatrixId) {
    if (StringUtils.isBlank(userMatrixId)) {
      throw new IllegalArgumentException("the user Matrix ID is required");
    }
    String simplifiedMatrixId = extractUserId(userMatrixId);
    if (userMatrixIdsCache.get(simplifiedMatrixId) != null) {
      return userMatrixIdsCache.get(simplifiedMatrixId);
    }
    try {
      String userAsJson = matrixHttpClient.getUser(userMatrixId, getMatrixAccessToken());
      Iterator<JsonValue> jsonIterator = new JsonGeneratorImpl().createJsonObjectFromString(userAsJson)
                                                                .getElement("threepids")
                                                                .getElements();
      while (jsonIterator.hasNext()) {
        JsonValue threePids = jsonIterator.next();
        if ("email".equals(threePids.getElement("medium").getStringValue())) {
          User user = getUserByEmail(threePids.getElement("address").getStringValue());
          if (user != null) {
            userMatrixIdsCache.put(userMatrixId, user.getUserName());
            return user.getUserName();
          }
        }
      }
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
      return userMatrixId;
    } catch (Exception e) {
      return userMatrixId;
    }
    return userMatrixId;
  }

  private User getUserByEmail(String email) {
    if (email == null) {
      return null;
    }
    RequestLifeCycle.begin(ExoContainerContext.getCurrentContainer());
    try {
      Query query = new Query();
      query.setEmail(email);
      User[] users = organizationService.getUserHandler().findUsersByQuery(query).load(0, 10);
      if (users.length > 0) {
        return users[0];
      } else {
        return null;
      }
    } catch (Exception e) {
      return null;
    } finally {
      RequestLifeCycle.end();
    }
  }

  /**
   * Cleans the username to be compatible with the usernames on Matrix server
   *
   * @param userName the user identifier
   * @return String the cleaned username
   */
  public String cleanMatrixUsername(String userName) {
    return userName.replaceAll("[^a-zA-Z0-9=_\\-\\.\\/+]+", "-").toLowerCase();
  }

  /**
   * Returns the restricted group of users if it is configured
   * 
   * @return Array of group names
   */
  public String[] getRestrictedGroups() {
    String groupNames = PropertyManager.getProperty(MATRIX_RESTRICTED_USERS_GROUP);
    if (StringUtils.isBlank(groupNames)) {
      return new String[]{};
    }
    return Arrays.stream(groupNames.split(",")).map(String::trim).toArray(String[]::new);
  }

  /**
   * Returns synapse media info by its ID
   * 
   * @param mediaId synapse media id
   * @return {@link MediaInfo} wrapped in {@link Optional}, or {@link Optional#empty()} if mediaId is null or blank
   * @throws JsonException if there is an error parsing the JSON response from the server
   * @throws IOException if a network or I/O error occurs during the request
   * @throws InterruptedException if the thread executing the request is interrupted
   */
  public Optional<MediaInfo> getMediaInfo(String mediaId) throws JsonException, IOException, InterruptedException {
    if (mediaId == null || mediaId.isBlank()) {
      return Optional.empty();
    }
    return matrixHttpClient.getMediaInfo(mediaId, getMatrixAccessToken());
  }

  /**
   * Deletes synapse media by its ID
   * 
   * @param mediaId synapse media id
   * @throws IllegalArgumentException if mediaId is null or blank
   * @throws JsonException if there is an error parsing the JSON response from the server
   * @throws IOException if a network or I/O error occurs during the request
   * @throws InterruptedException if the thread executing the request is interrupted
   */
  public void deleteMedia(String mediaId) throws JsonException, IOException, InterruptedException {
    if (mediaId == null || mediaId.isBlank()) {
      throw new IllegalArgumentException("mediaId must not be null or empty");
    }
    matrixHttpClient.deleteMedia(mediaId, getMatrixAccessToken());
  }

  /**
   * Lists the conversations the given user participates in: their direct
   * messaging rooms and the rooms of the spaces they are a member of. Used by the
   * {@code list_chat_conversations} MCP tool to give AI agents the user's chat
   * context.
   *
   * @param userName the Meeds username
   * @return the user's conversations, never {@code null}
   */
  public List<ChatConversation> getUserConversations(String userName) {
    if (StringUtils.isBlank(userName)) {
      return Collections.emptyList();
    }
    List<ChatConversation> conversations = new ArrayList<>();

    // Direct messaging rooms of the user
    for (Room room : getMatrixDMRoomsOfUser(userName)) {
      if (StringUtils.isBlank(room.getRoomId())) {
        continue;
      }
      String other = userName.equals(room.getFirstParticipant()) ? room.getSecondParticipant()
                                                                 : room.getFirstParticipant();
      conversations.add(new ChatConversation(room.getRoomId(), "dm", getUserDisplayName(other), null));
    }

    // Space rooms the user is a member of
    try {
      ListAccess<Space> memberSpaces = spaceService.getMemberSpaces(userName);
      int size = memberSpaces.getSize();
      if (size > 0) {
        for (Space space : memberSpaces.load(0, size)) {
          Room room = getRoomBySpaceId(Long.valueOf(space.getId()));
          if (room != null && StringUtils.isNotBlank(room.getRoomId())) {
            conversations.add(new ChatConversation(room.getRoomId(),
                                                   "space",
                                                   space.getDisplayName(),
                                                   Long.valueOf(space.getId())));
          }
        }
      }
    } catch (Exception e) {
      LOG.warn("Could not list space conversations for user {}", userName, e);
    }
    return conversations;
  }

  private String getUserDisplayName(String userName) {
    if (StringUtils.isBlank(userName)) {
      return userName;
    }
    Identity identity = identityManager.getOrCreateUserIdentity(userName);
    if (identity != null && identity.getProfile() != null && StringUtils.isNotBlank(identity.getProfile().getFullName())) {
      return identity.getProfile().getFullName();
    }
    return userName;
  }

  /**
   * Returns a Matrix access token for the given Meeds user, minted with the same
   * JWT login the browser uses so server-side reads/writes happen with the user's
   * own identity and permissions. Tokens are cached per user to avoid creating a
   * new Synapse device on every call; pass {@code forceRefresh} to mint a fresh one
   * (e.g. after a 401). Returns {@code null} when the user has no Matrix account.
   *
   * @param userName the Meeds username
   * @param forceRefresh whether to bypass the cache and mint a new token
   * @return the user's Matrix access token, or {@code null}
   */
  private String getUserAccessToken(String userName, boolean forceRefresh) throws JsonException, IOException, InterruptedException {
    if (!forceRefresh) {
      String cachedToken = userAccessTokensCache.get(userName);
      if (StringUtils.isNotBlank(cachedToken)) {
        return cachedToken;
      }
    }
    String matrixId = getMatrixIdForUser(userName);
    if (StringUtils.isBlank(matrixId)) {
      return null;
    }
    String matrixLocalPart = extractUserId(matrixId);
    String jwtToken = getJWTSessionToken(matrixLocalPart);
    String accessToken = matrixHttpClient.getAccessToken(jwtToken);
    if (StringUtils.isNotBlank(accessToken)) {
      userAccessTokensCache.put(userName, accessToken);
    }
    return accessToken;
  }

  /**
   * Executes a Matrix operation on behalf of the given user with their cached
   * access token. If the token has been rejected (HTTP 401), the token is
   * refreshed once and the operation retried. Any other failure, or a missing
   * Matrix account, yields the provided fallback value.
   *
   * @param userName the Meeds username acting
   * @param fallback the value to return when the operation cannot be performed
   * @param operation the operation to run with a valid access token
   * @return the operation result, or {@code fallback}
   */
  private <T> T callAsUser(String userName, T fallback, UserMatrixCall<T> operation) {
    try {
      String accessToken = getUserAccessToken(userName, false);
      if (StringUtils.isBlank(accessToken)) {
        LOG.warn("User {} has no Matrix account, skipping chat operation", userName);
        return fallback;
      }
      try {
        return operation.call(accessToken);
      } catch (MatrixUnauthorizedException unauthorized) {
        LOG.info("Matrix access token for user {} is no longer valid, refreshing and retrying", userName);
        String refreshedToken = getUserAccessToken(userName, true);
        if (StringUtils.isBlank(refreshedToken)) {
          return fallback;
        }
        return operation.call(refreshedToken);
      }
    } catch (Exception e) {
      LOG.error("Matrix chat operation failed for user {}", userName, e);
      return fallback;
    }
  }

  @FunctionalInterface
  private interface UserMatrixCall<T> {
    T call(String accessToken) throws Exception; // NOSONAR
  }

  /**
   * Returns the most recent messages of a conversation, in chronological order,
   * read <strong>as the given user</strong> so that Synapse enforces exactly what
   * that user is allowed to see (membership, history visibility, redactions). A
   * cheap participant pre-check avoids reading conversations the user is obviously
   * not part of. Used by the {@code get_chat_messages} MCP tool.
   *
   * @param userName the Meeds username acting
   * @param roomId the Matrix room id of the conversation
   * @param limit the maximum number of messages to return (clamped to [1, 200])
   * @return the conversation messages, oldest first, never {@code null}
   */
  public List<ChatMessage> getRoomMessages(String userName, String roomId, int limit) {
    if (StringUtils.isBlank(userName) || StringUtils.isBlank(roomId)) {
      return Collections.emptyList();
    }
    String normalizedRoomId = extractRoomId(roomId);
    boolean isParticipant = getUserConversations(userName).stream()
                                                          .anyMatch(conversation -> normalizedRoomId.equals(conversation.getRoomId()));
    if (!isParticipant) {
      LOG.warn("User {} is not a participant of conversation {}, refusing to read its messages", userName, normalizedRoomId);
      return Collections.emptyList();
    }
    int effectiveLimit = Math.min(Math.max(limit, 1), 200);
    return callAsUser(userName, Collections.<ChatMessage> emptyList(), accessToken -> {
      List<MatrixMessage> matrixMessages = matrixHttpClient.getRoomMessages(normalizedRoomId, effectiveLimit, accessToken);
      List<ChatMessage> messages = new ArrayList<>();
      for (MatrixMessage matrixMessage : matrixMessages) {
        messages.add(new ChatMessage(extractUserId(matrixMessage.getSender()),
                                     matrixMessage.getMessageContent(),
                                     matrixMessage.getTimeStamp()));
      }
      // The client API returns events newest-first (dir=b); flip to chronological.
      Collections.reverse(messages);
      return messages;
    });
  }

  /**
   * Returns the unread conversations of the given user, read <strong>as that
   * user</strong> via a Matrix {@code /sync}, so only the rooms the user has
   * joined are considered. Each entry carries the unread count and the recent
   * messages, with a human readable title resolved from the user's conversations.
   * Used by the {@code get_unread_chat_messages} MCP tool.
   *
   * @param userName the Meeds username acting
   * @return the user's unread conversations, never {@code null}
   */
  public List<ChatUnread> getUnreadConversations(String userName) {
    if (StringUtils.isBlank(userName)) {
      return Collections.emptyList();
    }
    return callAsUser(userName, Collections.<ChatUnread> emptyList(), accessToken -> {
      Map<String, String> titlesByRoomId = new HashMap<>();
      for (ChatConversation conversation : getUserConversations(userName)) {
        titlesByRoomId.put(conversation.getRoomId(), conversation.getTitle());
      }
      List<ChatUnread> unread = new ArrayList<>();
      for (MatrixUnreadRoom unreadRoom : matrixHttpClient.getUnreadRooms(accessToken, 20)) {
        List<ChatMessage> messages = new ArrayList<>();
        // The /sync timeline is already in chronological order (oldest first).
        for (MatrixMessage matrixMessage : unreadRoom.getMessages()) {
          messages.add(new ChatMessage(extractUserId(matrixMessage.getSender()),
                                       matrixMessage.getMessageContent(),
                                       matrixMessage.getTimeStamp()));
        }
        unread.add(new ChatUnread(unreadRoom.getRoomId(),
                                  titlesByRoomId.get(unreadRoom.getRoomId()),
                                  unreadRoom.getUnreadCount(),
                                  messages));
      }
      return unread;
    });
  }

  /**
   * Sends a textual message to a conversation <strong>as the given user</strong>,
   * after checking that the user participates in it. Synapse additionally enforces
   * that the user is allowed to post. Used by the {@code send_chat_message} MCP
   * tool, e.g. to let an AI agent post a real-time reply on the user's behalf.
   *
   * @param userName the Meeds username acting
   * @param roomId the Matrix room id of the conversation
   * @param text the plain text message to send
   * @return the created event id, or {@code null} when the message could not be sent
   */
  public String sendMessage(String userName, String roomId, String text) {
    if (StringUtils.isBlank(userName) || StringUtils.isBlank(roomId) || StringUtils.isBlank(text)) {
      return null;
    }
    String normalizedRoomId = extractRoomId(roomId);
    List<ChatConversation> conversations = getUserConversations(userName);
    boolean isParticipant = conversations.stream().anyMatch(conversation -> normalizedRoomId.equals(conversation.getRoomId()));
    if (!isParticipant) {
      LOG.warn("User {} is not a participant of conversation {}, refusing to send a message. Known conversations: {}",
               userName,
               normalizedRoomId,
               conversations.stream().map(ChatConversation::getRoomId).toList());
      return null;
    }
    return callAsUser(userName,
                      null,
                      accessToken -> matrixHttpClient.sendMessage(normalizedRoomId, text, UUID.randomUUID().toString(), accessToken));
  }
}
