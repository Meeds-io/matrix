/*
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
import io.meeds.chat.model.DirectMessagingRoom;
import io.meeds.chat.model.MatrixRoomPermissions;
import io.meeds.chat.model.Room;
import io.meeds.chat.model.SpaceRoom;
import io.meeds.chat.rest.model.RoomEntity;
import io.meeds.chat.rest.model.RoomList;
import io.meeds.chat.rest.model.Message;
import io.meeds.chat.service.utils.MatrixHttpClient;
import io.meeds.chat.storage.MatrixRoomStorage;
import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.exoplatform.commons.ObjectAlreadyExistsException;
import org.exoplatform.commons.file.model.FileItem;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.commons.utils.PropertyManager;

import org.exoplatform.portal.localization.LocaleContextInfoUtils;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.Membership;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.resources.ResourceBundleService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.profile.ProfileFilter;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.core.storage.api.IdentityStorage;
import org.exoplatform.ws.frameworks.json.impl.JsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static io.meeds.chat.service.utils.MatrixConstants.*;

@Service
public class MatrixService {

  private static final Log      LOG = ExoLogger.getLogger(MatrixService.class);

  @Autowired
  private MatrixRoomStorage     matrixRoomStorage;

  @Autowired
  private IdentityManager       identityManager;

  @Autowired
  private IdentityStorage       identityStorage;

  @Autowired
  private OrganizationService   organizationService;

  @Autowired
  private SpaceService          spaceService;

  @Autowired
  private ResourceBundleService resourceBundleService;

  @Autowired
  private MatrixHttpClient      matrixHttpClient;

  private String                matrixAccessToken;

  public MatrixService(MatrixRoomStorage matrixRoomStorage,
                       IdentityManager identityManager,
                       IdentityStorage identityStorage,
                       OrganizationService organizationService,
                       MatrixHttpClient matrixHttpClient) {
    this.matrixRoomStorage = matrixRoomStorage;
    this.identityManager = identityManager;
    this.identityStorage = identityStorage;
    this.organizationService = organizationService;
    this.matrixHttpClient = matrixHttpClient;
  }

  @PostConstruct
  public void init() throws JsonException, IOException, InterruptedException {
    this.getMatrixAccessToken();

    String userFullMatrixID = "@" + PropertyManager.getProperty(MATRIX_ADMIN_USERNAME) + ":"
        + PropertyManager.getProperty(MATRIX_SERVER_NAME);
    String displayName = System.getProperty(MATRIX_ADMIN_DISPLAY_NAME, "Chat Bot");
    if (StringUtils.isNotBlank(displayName)) {
      this.updateUserDisplayName(userFullMatrixID, displayName);
    }
  }

  private String getMatrixAccessToken() throws JsonException, IOException, InterruptedException {
    if (StringUtils.isBlank(this.matrixAccessToken)) {
      try {
        String jwtAccessToken = this.getJWTSessionToken(PropertyManager.getProperty(MATRIX_ADMIN_USERNAME));
        this.matrixAccessToken = matrixHttpClient.getAdminAccessToken(jwtAccessToken);
      } catch (JsonException | IOException | InterruptedException e) {
        LOG.error("Could not get Matrix Access token for the administrator account !");
        throw e;
      }
    }
    return this.matrixAccessToken;
  }

  public void updateUserDisplayName(String matrixFullID, String newDisplayName) {
    try {
      String currentUserDisplayName = matrixHttpClient.getUserDisplayName(matrixFullID, getMatrixAccessToken());
      if (StringUtils.isNotBlank(currentUserDisplayName) && !currentUserDisplayName.equals(newDisplayName)) {
        matrixHttpClient.updateUserDisplayName(matrixFullID, newDisplayName, getMatrixAccessToken());
      }
    } catch (Exception e) {
      LOG.error("Couldn't update the display name of the user {}", matrixFullID, e);
    }
  }

  /**
   * Returns the ID of the room linked to a space
   * 
   * @param space
   * @return the roomId linked to the space
   */
  public String getRoomBySpace(Space space) {
    return matrixRoomStorage.getMatrixRoomBySpaceId(space.getId());
  }

  /**
   * Returns the Space linked to the room
   * 
   * @param roomId the Matrix room ID
   * @return the space
   */
  public Space getSpaceByRoomId(String roomId) {
    return matrixRoomStorage.getSpaceIdByMatrixRoomId(roomId);
  }

  /**
   * Returns the DM room by room ID
   *
   * @param roomId the Matrix room ID
   * @return the Direct messaging room
   */
  public DirectMessagingRoom getDMRoomByRoomId(String roomId) {
    return matrixRoomStorage.getDMRoomByRoomId(roomId);
  }

  public Room getById(String roomId) {
    return matrixRoomStorage.getById(roomId);
  }

  /**
   * records the matrix ID of the room linked to the space
   *
   * @param space the Space
   * @param roomId the ID of the matrix room
   * @return the room ID
   */
  public SpaceRoom linkSpaceToMatrixRoom(Space space, String roomId) {
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
    Date expirtaionDate = Date.from(Instant.now().plusSeconds(7 * 24 * 60 * 60)); // adds one week to the current instant
    return Jwts.builder()
               .setSubject(userNameOnMatrix)
               .signWith(Keys.hmacShaKeyFor(PropertyManager.getProperty(MATRIX_JWT_SECRET).getBytes()))
               .setExpiration(expirtaionDate)
               .compact();

  }

  /**
   * Saves a new user on Matrix
   * 
   * @param user the user to create on Matrix
   * @param isNew boolean if the user is new, then true
   * @return String the matrix user ID
   */
  public String saveUserAccount(User user, boolean isNew, boolean isEnableUserOperation) throws JsonException,
                                                                                         IOException,
                                                                                         InterruptedException {
    String matrixId = matrixHttpClient.saveUserAccount(user,
                                                       user.getUserName(),
                                                       isNew,
                                                       this.getMatrixAccessToken(),
                                                       isEnableUserOperation);
    Identity userIdentity = identityManager.getOrCreateUserIdentity(user.getUserName());
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

  public long getAllLinkedRooms() {
    return matrixRoomStorage.getSpaceRoomCount();
  }

  public DirectMessagingRoom getDirectMessagingRoom(String firstParticipant, String secondParticipant) {
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

  public DirectMessagingRoom createDirectMessagingRoom(DirectMessagingRoom directMessagingRoom) throws ObjectAlreadyExistsException {
    String firstParticipant = directMessagingRoom.getFirstParticipant();
    String secondParticipant = directMessagingRoom.getSecondParticipant();
    if (StringUtils.isBlank(firstParticipant) || StringUtils.isBlank(secondParticipant)) {
      throw new IllegalArgumentException("The ids of the room participants should not be null");
    }
    if (identityManager.getOrCreateUserIdentity(directMessagingRoom.getFirstParticipant()) == null
        || identityManager.getOrCreateUserIdentity(directMessagingRoom.getSecondParticipant()) == null) {
      throw new IllegalArgumentException("The ids of the room participants should be valid user identity ids");
    }
    DirectMessagingRoom matrixRoom = matrixRoomStorage.getDirectMessagingRoom(firstParticipant, secondParticipant);
    if (matrixRoom == null) {
      return matrixRoomStorage.saveDirectMessagingRoom(directMessagingRoom.getFirstParticipant(),
                                                       directMessagingRoom.getSecondParticipant(),
                                                       directMessagingRoom.getRoomId());
    } else {
      throw new ObjectAlreadyExistsException("A direct messaging room is already created for the users %s and %s".formatted(firstParticipant,
                                                                                                                            secondParticipant));
    }
  }

  public List<DirectMessagingRoom> getMatrixDMRoomsOfUser(String user) {
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
  public List<SpaceRoom> getSpaceRooms() {
    return matrixRoomStorage.getSpaceRooms();
  }

  /**
   * Searches for a user having the provided Matrix ID
   * 
   * @param userIdOnMatrix the ID of the user on Matrix
   * @return The identity of the user
   */
  public Identity findUserByMatrixId(String userIdOnMatrix) {
    ProfileFilter profileFilter = new ProfileFilter();
    Map<String, String> matrixProperty = new HashMap<>();
    matrixProperty.put(USER_MATRIX_ID, userIdOnMatrix);
    profileFilter.setProfileSettings(matrixProperty);
    ListAccess<Identity> userIdentities = identityManager.getIdentitiesByProfileFilter(OrganizationIdentityProvider.NAME,
                                                                                       profileFilter,
                                                                                       true);
    try {
      if (userIdentities != null && userIdentities.getSize() >= 1) {
        return userIdentities.load(0, 1)[0];
      }
    } catch (Exception e) {
      LOG.error("Couldn't find a user having the Matrix ID : {}", userIdOnMatrix, e);
    }
    return null;
  }

  /**
   * Process the Matrix rooms and adds the missing information of users and spaces
   * 
   * @param roomList the room list received from Matrix par sync API
   * @param currentUserName the current user
   * @return the roo List after processing
   */
  public RoomList processRooms(RoomList roomList, String currentUserName) {
    if(roomList == null || roomList.getRooms() == null) {
      throw new IllegalArgumentException("The room list Object is empty");
    }
    if(StringUtils.isBlank(currentUserName)) {
      throw new IllegalArgumentException("The username of the current user is mandatory");
    }

    for (RoomEntity room : roomList.getRooms()) {
      // Update room information
      String roomId = room.getId().substring(0, room.getId().indexOf(":"));// remove server part
      Room matrixRoom = this.getById(roomId);
      if(matrixRoom != null) {
        if(StringUtils.isNotBlank(matrixRoom.getSpaceId())) {
          Space space = spaceService.getSpaceById(matrixRoom.getSpaceId());
          if(space != null) {
            room.setName(space.getDisplayName());
            room.setAvatarUrl(space.getAvatarUrl());
            room.setSpaceId(matrixRoom.getSpaceId());
          } else {
            continue;
          }
        } else {
          Identity identity = null;
          if(matrixRoom.getFirstParticipant().equals(currentUserName)) {
            identity = identityManager.getOrCreateUserIdentity(matrixRoom.getSecondParticipant());
          } else if(matrixRoom.getSecondParticipant().equals(currentUserName)) {
            identity = identityManager.getOrCreateUserIdentity(matrixRoom.getFirstParticipant());
          }
          if(identity != null) {
            room.setName(identity.getProfile().getFullName());
            room.setAvatarUrl(identity.getProfile().getAvatarUrl());
            room.setUserId(identity.getRemoteId());
          } else {
            continue;
          }
        }
      }
      
      // Get last message
      Message message = room.getLastMessage();
      if(message != null && StringUtils.isNotBlank(message.getSender())) {
        Identity identity = this.findUserByMatrixId(extractUserId(message.getSender()));
        if (identity != null) {
          String updatedContent;
          if (!identity.getRemoteId().equals(currentUserName)) {
            updatedContent = identity.getProfile().getFullName() + ":" + message.getContent();
            message.setContent(updatedContent);
          } else {
            Locale locale = LocaleContextInfoUtils.getUserLocale(currentUserName);
            String you = resourceBundleService.getSharedString(YOU_STRING, locale);
            updatedContent = you + message.getContent();
            message.setContent(updatedContent);
          }
          room.setLastMessage(new Message(updatedContent, identity.getProfile().getFullName()));
        }
      }
    }
    return roomList;
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
}
