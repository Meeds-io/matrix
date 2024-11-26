import * as matrixService from './js/MatrixService.js';

export function registerChatExtensions(chatTitle) {
  const profileExtensionAction = {
    id: 'profile-chat',
    title: chatTitle,
    icon: 'fas fa-comments',
    class: 'fas fa-comments',
    additionalClass: 'mt-1',
    order: 10,
    enabled: (user) => user?.enabled && user.username !== eXo.env.portal.userName && localStorage.getItem("matrix_user_id")
      && user.properties.some(property => property.propertyName === 'matrixId') && user.properties.find(property => property.propertyName === 'matrixId').value,
    click: (profile) => {
      const matrixProperty = profile.properties.filter(property => property.propertyName === 'matrixId');
      if(matrixProperty && matrixProperty.length) {
        matrixService.openDMRoom(eXo.env.portal.userName, profile.userName, matrixServerName);
      }
      console.log(profile);
    },
  };


  if (extensionRegistry) {
    extensionRegistry.registerExtension('profile-extension', 'action', profileExtensionAction);
  }

  document.dispatchEvent(new CustomEvent('profile-extension-updated', { detail: profileExtensionAction}));

  extensionRegistry.registerComponent('SpacePopover', 'space-popover-action', {
    id: 'chat',
    vueComponent: Vue.options.components['popover-chat-button'],
    rank: 40,
  });

  extensionRegistry.registerComponent('UserPopover', 'user-popover-action', {
    id: 'chat',
    vueComponent: Vue.options.components['popover-chat-button'],
    rank: 40,
  });
}