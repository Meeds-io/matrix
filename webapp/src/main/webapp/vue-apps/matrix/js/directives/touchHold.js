/*
 This file is part of the Meeds project (https://meeds.io/).

 Copyright (C) 2020 - 2025 Meeds Association contact@meeds.io

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

export default {
  bind(el, binding) {
    function preventIfIsMobile(event) {
      if (window.innerWidth <= 600) {
        event.preventDefault();
        event.stopPropagation();
      }
    }
    el.addEventListener('contextmenu', preventIfIsMobile);
    el.addEventListener('dragstart', preventIfIsMobile);
    el.addEventListener('selectstart', preventIfIsMobile);

    el._touchHoldExtraListeners = {
      contextmenu: preventIfIsMobile,
      dragstart: preventIfIsMobile,
      selectstart: preventIfIsMobile
    };

    let timeout = null;
    let touchMoved = false;
    let touchStartY = 0;
    let ignoreClickUntil = 0;

    const holdDuration = binding.arg || 500;
    const handler = binding.value;

    const onTouchStart = (e) => {
        touchMoved = false;
        touchStartY = e.touches[0].clientY;

        timeout = setTimeout(() => {
            if (!touchMoved) {
                handler?.(e);
                e.preventDefault();
                ignoreClickUntil = Date.now() + 600;
            }
        }, holdDuration);
    };

    const onTouchMove = (e) => {
        const deltaY = Math.abs(e.touches[0].clientY - touchStartY);
        if (deltaY > 10) {
            touchMoved = true;
            clearTimeout(timeout);
        }
    };

    const cancel = () => clearTimeout(timeout);

    const onClickOutside = (e) => {
        if (Date.now() < ignoreClickUntil) {
            e.stopImmediatePropagation?.();
            e.preventDefault?.();
        }
    };

    el._touchHold = {
        onTouchStart,
        onTouchMove,
        cancel,
        onClickOutside
    };

    el.addEventListener('touchstart', onTouchStart, { passive: false });
    el.addEventListener('touchmove', onTouchMove, { passive: false });
    el.addEventListener('touchend', cancel, { passive: false });
    el.addEventListener('touchcancel', cancel, { passive: false });
    document.addEventListener('click', onClickOutside, true);
    document.addEventListener('touchstart', onClickOutside, true);
    },
  unbind(el) {
    if (el._touchHoldExtraListeners) {
      el.removeEventListener('contextmenu', el._touchHoldExtraListeners.contextmenu);
      el.removeEventListener('dragstart', el._touchHoldExtraListeners.dragstart);
      el.removeEventListener('selectstart', el._touchHoldExtraListeners.selectstart);
      delete el._touchHoldExtraListeners;
    }
    const { onTouchStart, onTouchMove, cancel, onClickOutside } = el._touchHold || {};
    el.removeEventListener('touchstart', onTouchStart);
    el.removeEventListener('touchmove', onTouchMove);
    el.removeEventListener('touchend', cancel);
    el.removeEventListener('touchcancel', cancel);
    document.removeEventListener('click', onClickOutside, true);
    document.removeEventListener('touchstart', onClickOutside, true);
    delete el._touchHold;
  }
};
