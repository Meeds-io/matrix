import MatrixComponent from './components/component.vue';
import MatrixChatButton from './components/chatButton.vue';
import MatrixChatDrawer from './components/MatrixChatDrawer.vue';
import MatrixChatRooms from './components/MatrixChatRooms.vue';
import MatrixChatRoom from './components/MatrixChatRoom.vue';
import MeedsChatMessage from './components/chatMessage.vue';
import MeedsChatMessageContent from './components/chatMessageContent.vue';
import MeedsChatQuickCreateDiscussionDrawer from './components/MeedsChatQuickCreateDiscussionDrawer.vue';
import MeedsChatDiscussionDrawer from './components/MeedsChatDiscussionDrawer.vue';
import PopoverChatButton from './components/PopoverChatButton.vue';
import AudioMessage from './components/message/AudioMessage.vue';
import MessageReplyQuote from "./components/message/MessageReplyQuote.vue";
import MessageUploadFileInput from './components/message/MessageUploadFileInput.vue';
import MessageUser from './components/message/MessageUser.vue';
import MessageActionList from './components/message/action/MessageActionList.vue'
import MessageReactionItem from './components/message/MessageReactionItem.vue';

import * as matrixService from './js/MatrixService.js';
import {registerChatExtensions} from './extension.js';
import {chatConstants} from './js/Constants.js';
import * as timeUtils from './js/timeUtils.js';
import './icons-extensions.js'

const components = {
  'matrix-component': MatrixComponent,
  'matrix-chat-button': MatrixChatButton,
  'matrix-chat-drawer': MatrixChatDrawer,
  'matrix-chat-rooms': MatrixChatRooms,
  'matrix-chat-room': MatrixChatRoom,
  'meeds-chat-message': MeedsChatMessage,
  'meeds-chat-message-content': MeedsChatMessageContent,
  'meeds-popover-chat-button': PopoverChatButton,
  'meeds-chat-quick-create-discussion-drawer': MeedsChatQuickCreateDiscussionDrawer,
  'meeds-chat-discussion-drawer': MeedsChatDiscussionDrawer,
  'audio-message': AudioMessage,
  'message-reply-quote': MessageReplyQuote,
  'message-upload-file-input': MessageUploadFileInput,
  'message-user': MessageUser,
  'message-action-list': MessageActionList,
  'message-reaction-item': MessageReactionItem
};

for (const key in components) {
  Vue.component(key, components[key]);
}

window.Object.defineProperty(Vue.prototype, '$matrixService', {
  value: matrixService,
});
window.Object.defineProperty(Vue.prototype, '$chatConstants', {
  value: chatConstants,
});
window.Object.defineProperty(Vue.prototype, '$timeUtils', {
  value: timeUtils,
});

Vue.prototype.$filesIconsExtension = extensionRegistry.loadExtensions('chat', 'files-icons-extension');

const appId = 'matrixChatButton';
const lang = window?.eXo?.env?.portal?.language || 'fr';
const i18NUrl = `${eXo.env.portal.context}/${eXo.env.portal.rest}/i18n/bundle/locale.portlet.matrix-${lang}.json`;

export function init(serverName) {
  exoi18n.loadLanguageAsync(lang, i18NUrl).then(i18n => {
    registerChatExtensions(i18n.messages[lang]['matrix.chat.open']);
    Vue.createApp({
      template: `<matrix-chat-button id="matrixChatButton" serverName="${serverName}"/>`,
      vuetify: Vue.prototype.vuetifyOptions,
      data: function() {
        return {
          serverName: serverName
        };
      },
      i18n
    },
    `#${appId}`, 'Matrix');
  });
}
