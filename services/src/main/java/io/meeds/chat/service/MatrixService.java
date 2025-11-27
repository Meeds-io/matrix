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
import io.meeds.chat.model.MatrixMessage;
import io.meeds.chat.model.MatrixRoomPermissions;
import io.meeds.chat.model.Room;
import io.meeds.chat.rest.model.MediaInfo;
import io.meeds.chat.service.utils.MatrixHttpClient;
import io.meeds.chat.storage.MatrixRoomStorage;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.exoplatform.commons.ObjectAlreadyExistsException;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.file.model.FileItem;
import org.exoplatform.commons.utils.CommonsUtils;
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

  public static final String             USER_MATRIX_ID_CACHE_NAME = "chat.UserMatrixId";

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
    return getRoomBySpaceId(space.getId());
  }

  /**
   * Returns the ID of the room linked to a space
   *
   * @param space the space
   * @return the roomId linked to the space
   */
  public Room getRoomBySpace(Space space, boolean includeDisabled) {
    return getRoomBySpaceId(space.getId(), includeDisabled);
  }

  /**
   * Returns the ID of the room linked to a space
   *
   * @param spaceId the space Id
   * @return the roomId linked to the space
   */
  public Room getRoomBySpaceId(String spaceId) {
    return this.getRoomBySpaceId(spaceId, false);
  }

  /**
   * Returns the ID of the room linked to a space
   *
   * @param spaceId the space Id
   * @return the roomId linked to the space
   */
  public Room getRoomBySpaceId(String spaceId, boolean includeDisabled) {
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
    return matrixRoomStorage.saveRoomForSpace(space.getId(), roomId);
  }

  /**
   * Creates a room on Matrix for the space
   * 
   * @param space the space
   * @return String representing the room id
   * @throws JsonException
   * @throws IOException
   * @throws InterruptedException
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
      String prefix = StringUtils.isNotBlank(PropertyManager.getProperty(MATRIX_USERNAME_PREFIX)) ? PropertyManager.getProperty(MATRIX_USERNAME_PREFIX) : "u";
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

  public boolean isUserMemberOfGroup(String userName, String groupId) throws Exception {
    this.organizationService = CommonsUtils.getOrganizationService();
    Collection<Membership> userMemberships = this.organizationService.getMembershipHandler()
                                                                     .findMembershipsByUserAndGroup(userName, groupId);
    return !userMemberships.isEmpty();
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
    if (StringUtils.isBlank(room.getSpaceId())) {
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
}
