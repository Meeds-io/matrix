import MatrixComponent from './components/component.vue';
import MatrixChatButton from './components/chatButton.vue';
import MatrixChatDrawer from './components/MatrixChatDrawer.vue';
import MatrixChatRooms from './components/MatrixChatRooms.vue';
import MatrixChatRoom from './components/MatrixChatRoom.vue';
import MatrixChatMessages from './components/MatrixChatMessages.vue';
import PopoverChatButton from './components/PopoverChatButton.vue';
import * as matrixService from './js/MatrixService.js';
import {registerChatExtensions} from './extension.js';
import * as chatConstants from './js/Constants.js';

const components = {
  'matrix-component': MatrixComponent,
  'matrix-chat-button': MatrixChatButton,
  'matrix-chat-drawer': MatrixChatDrawer,
  'matrix-chat-rooms': MatrixChatRooms,
  'matrix-chat-room': MatrixChatRoom,
  'matrix-chat-messages': MatrixChatMessages,
  'popover-chat-button': PopoverChatButton,
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

const appId = 'matrixChatButton';
const lang = window?.eXo?.env?.portal?.language || 'fr';
const i18NUrl = `${eXo.env.portal.context}/${eXo.env.portal.rest}/i18n/bundle/locale.portlet.matrix-${lang}.json`;

export function init(serverName) {
  exoi18n.loadLanguageAsync(lang, i18NUrl).then(i18n => {
    registerChatExtensions(i18n.messages[lang]['meeds.chat.open']);
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
