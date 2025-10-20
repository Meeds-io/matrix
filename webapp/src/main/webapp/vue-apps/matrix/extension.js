import * as matrixService from './js/MatrixService.js';

export function registerChatExtensions(chatTitle) {
  const profileExtensionAction = {
    id: 'profile-matrix-chat',
    title: chatTitle,
    icon: 'fas fa-comments',
    class: 'fas fa-comments',
    iconOnly: true,
    order: 10,
    enabled: async function(identity) {
      const userIdentifier = identity.userName || identity.username;
      if (userIdentifier) {
        const matrixUserId = await matrixService.getMatrixIdOfUser(userIdentifier);
        return identity?.enabled && identity.username !== eXo.env.portal.userName
               && localStorage.getItem("matrix_user_id")
               && matrixUserId;
      } else {
        const room = await matrixService.getSpaceRoom(identity.id);
        return room.status === 'ENABLED';
      }
    },
    click: (profile) => {
      const userName = profile.userName || profile.username;
      if(userName) {
        matrixService.getMatrixIdOfUser(userName).then(invitedUserMatrixId => {
          if (invitedUserMatrixId) {
            matrixService.openDMRoom(eXo.env.portal.userName, userName, matrixServerName, matrixUserId, invitedUserMatrixId);
          }
        });
      } else {
        matrixService.openSpaceRoom(profile.id);
      }
    },
  };

  if (extensionRegistry) {
    extensionRegistry.registerExtension('profile-extension', 'action', profileExtensionAction);

    extensionRegistry.registerComponent('SpaceSettings', 'space-settings-components', {
      id: 'meeds-chat-space-settings',
      vueComponent: Vue.options.components['matrix-chat-space-settings'],
      rank: 10,
    });

    document.dispatchEvent(new CustomEvent('profile-extension-updated', { detail: profileExtensionAction}));

    extensionRegistry.registerComponent('SpacePopover', 'space-popover-action', {
      id: 'matrix-chat-space-popover',
      isEnabled: async function (params) {
        const room = await matrixService.getSpaceRoom(params.identityId);
        return room.status === 'ENABLED';
      },
      vueComponent: Vue.options.components['matrix-popover-chat-button'],
      rank: 40,
    });

    extensionRegistry.registerComponent('UserPopover', 'user-popover-action', {
      id: 'matrix-chat-user-popover',
      vueComponent: Vue.options.components['matrix-popover-chat-button'],
      rank: 40,
    });
  }
}