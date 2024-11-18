
export function registerChatExtensions(chatTitle) {
  const profileExtensionAction = {
    id: 'profile-chat',
    title: chatTitle,
    icon: 'uiIconBannerChat',
    class: 'fas fa-comments',
    additionalClass: 'mt-1',
    order: 10,
    enabled: (user) => user?.enabled && user.username !== eXo.env.portal.userName,
    click: (profile) => {
      const chatType = profile.groupId ? 'space-id' : 'username';
      const chatRoomName = profile.prettyName ? profile.id : profile.username;

      document.dispatchEvent(
        new CustomEvent(chatConstants.ACTION_ROOM_OPEN_CHAT, { detail: {
          name: chatRoomName,
          type: chatType,
        }}));
    },
  };
  
//  if (extensionRegistry) {
//    extensionRegistry.registerExtension('profile-extension', 'action', profileExtensionAction);
//  }
//
//  document.dispatchEvent(new CustomEvent('profile-extension-updated', { detail: profileExtensionAction}));
//
//  extensionRegistry.registerComponent('SpacePopover', 'space-popover-action', {
//    id: 'chat',
//    vueComponent: Vue.options.components['popover-chat-button'],
//    rank: 40,
//  });

  extensionRegistry.registerComponent('UserPopover', 'user-popover-action', {
    id: 'chat',
    vueComponent: Vue.options.components['popover-chat-button'],
    rank: 40,
  });
}