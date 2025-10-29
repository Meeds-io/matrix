/*
 This file is part of the Meeds project (https://meeds.io/).

 Copyright (C) 2025 Meeds Association contact@meeds.io

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public
 License as published by the Free Software Foundation; either
 version 3 of the License, or (at your option) any later version.
 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 Lesser General Public License for more details.

 You should have received a copy of the GNU Lesser General Public License
 along with this program; if not, write to the Free Software Foundation,
 Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

export function placeCaretAtEnd(el) {
  el.focus();
  if (typeof window.getSelection !== 'undefined' && typeof document.createRange !== 'undefined') {
    const range = document.createRange();
    range.selectNodeContents(el);
    range.collapse(false);
    const sel = window.getSelection();
    sel.removeAllRanges();
    sel.addRange(range);
  } else if (typeof document.body.createTextRange !== 'undefined') {
    const textRange = document.body.createTextRange();
    textRange.moveToElementText(el);
    textRange.collapse(false);
    textRange.select();
  }
}

export function getMessageViewportInfo(eventId) {
  const container = document.getElementById('chatMessagesContainer');
  const messageEl = document.getElementById(`message-content-${eventId}`);

  if (!container || !messageEl) {
    return {
      visibleTop: false,
      above: false,
      below: false
    };
  }

  const containerRect = container.getBoundingClientRect();
  const messageRect = messageEl.getBoundingClientRect();

  const visibleTop = messageRect.top >= containerRect.top
      && messageRect.top <= containerRect.bottom;
  const above = messageRect.bottom < containerRect.top;
  const below = messageRect.top > containerRect.bottom;

  return {visibleTop, above, below};
}

export function restoreMentions(html) {
  if (!html) {
    return '';
  }

  const regex = /<a[^>]+href="\/portal\/dw\/profile\/([^"]+)"[^>]*>([^<]+)<\/a>/gu;

  return html.replace(regex, (_, userId, userName) => {
    const atQuery = userName.split(' ')[0];
    const fullName = userName.split('@')[1];
    return `<span class="atwho-inserted" data-atwho-at-query="${atQuery}" data-atwho-at-value="${userId}" contenteditable="false">
      <span class="exo-mention" data-user-id="${userId}" data-user-name="${fullName}">
        <i aria-hidden="true" class="v-icon fa" style="font-size: 14px;"></i>
        ${fullName}
        <a href="#" class="remove"><i class="uiIconClose uiIconLightGray"></i></a>
      </span>
    </span>`;
  });
}

export function scrollToBottomWhenStable(container, isActive, options = {}) {
  if (!container) {
    return;
  }
  const { maxStableFrames = 5, maxFrames = 60 } = options;
  let lastHeight = -1;
  let stableFrames = 0;
  let frameCount = 0;

  const tick = () => {
    if (!isActive()) {
      return;
    }
    const height = container.scrollHeight;
    container.scrollTop = height;

    stableFrames = height === lastHeight ? stableFrames + 1 : 0;
    lastHeight = height;

    if (stableFrames >= maxStableFrames || ++frameCount >= maxFrames) {
      return;
    }
    requestAnimationFrame(tick);
  };
  requestAnimationFrame(tick);
}
