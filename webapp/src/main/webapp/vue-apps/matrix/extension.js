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
      if(identity.userName || identity.username) {
        const userMatrixId = identity.properties?.some(property => property.propertyName === 'matrixId') && identity.properties?.find(property => property.propertyName === 'matrixId').value;
        return identity?.enabled && identity.username !== eXo.env.portal.userName
               && localStorage.getItem("matrix_user_id")
               && userMatrixId;
      } else {
        const room = await matrixService.getSpaceRoom(identity.id);
        return room.status === 'ENABLED';
      }
    },
    click: (profile) => {
      const userName = profile.userName || profile.username;
      if(userName) {
        const matrixProperty = profile.properties.filter(property => property.propertyName === 'matrixId');
        if(matrixProperty && matrixProperty.length > 0) {
          const invitedUserMatrixId = matrixProperty[0].value;
          matrixService.openDMRoom(eXo.env.portal.userName, userName, matrixServerName, matrixUserId, invitedUserMatrixId);
        }
      } else {
        matrixService.openSpaceRoom(profile.id);
      }
    },
  };

  if (extensionRegistry) {
    extensionRegistry.registerExtension('profile-extension', 'action', profileExtensionAction);

    extensionRegistry.registerComponent('SpaceSettings', 'space-settings-components', {
      id: 'meeds-chat-space-settings',
      vueComponent: Vue.options.components['meeds-chat-space-settings'],
      rank: 10,
    });

    document.dispatchEvent(new CustomEvent('profile-extension-updated', { detail: profileExtensionAction}));

    extensionRegistry.registerComponent('SpacePopover', 'space-popover-action', {
      id: 'matrix-chat-space-popover',
      isEnabled: async function (params) {
        const room = await matrixService.getSpaceRoom(params.identityId);
        return room.status === 'ENABLED';
      },
      vueComponent: Vue.options.components['meeds-popover-chat-button'],
      rank: 40,
    });

    extensionRegistry.registerComponent('UserPopover', 'user-popover-action', {
      id: 'matrix-chat-user-popover',
      vueComponent: Vue.options.components['meeds-popover-chat-button'],
      rank: 40,
    });
  }
}