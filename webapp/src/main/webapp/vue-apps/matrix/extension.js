import * as matrixService from './js/MatrixService.js';

export function registerChatExtensions(chatTitle) {
  const profileExtensionAction = {
    id: 'profile-matrix-chat',
    title: chatTitle,
    icon: 'fas fa-comments',
    class: 'fas fa-comments',
    iconOnly: true,
    order: 10,
    enabled: (identity) => {
      if(identity.userName || identity.username) {
        const userMatrixId = identity.properties?.some(property => property.propertyName === 'matrixId') && identity.properties?.find(property => property.propertyName === 'matrixId').value;
        return identity?.enabled && identity.username !== eXo.env.portal.userName
               && localStorage.getItem("matrix_user_id")
               && userMatrixId;
      } else {
        return true;
      }
    },
    click: (profile) => {
      const userName = profile.userName || profile.username;
      if(userName) {
        const matrixProperty = profile.properties.filter(property => property.propertyName === 'matrixId');
        if(matrixProperty && matrixProperty.length) {
          matrixService.openDMRoom(eXo.env.portal.userName, userName, matrixServerName);
        }
      } else {
        matrixService.openSpaceRoom(profile.id);
      }
    },
  };

  if (extensionRegistry) {
    extensionRegistry.registerExtension('profile-extension', 'action', profileExtensionAction);
  }

  document.dispatchEvent(new CustomEvent('profile-extension-updated', { detail: profileExtensionAction}));

  extensionRegistry.registerComponent('SpacePopover', 'space-popover-action', {
    id: 'matrix-chat',
    vueComponent: Vue.options.components['meeds-popover-chat-button'],
    rank: 40,
  });

  extensionRegistry.registerComponent('UserPopover', 'user-popover-action', {
    id: 'matrix-chat',
    vueComponent: Vue.options.components['meeds-popover-chat-button'],
    rank: 40,
  });
}