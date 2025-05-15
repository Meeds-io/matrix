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

extensionRegistry.registerExtension('AnalyticsSamples', 'SampleItem', {
    type: 'matrixSampleMessageTypeItem',
    options: {
        rank: 80,
        vueComponent: Vue.options.components['matrix-message-type-sample-item'],
        match: (fieldName) => fieldName === 'messageType'
    },
});

extensionRegistry.registerExtension('AnalyticsSamples', 'SampleItem', {
    type: 'matrixSampleRoomTypeItem',
    options: {
        rank: 90,
        vueComponent: Vue.options.components['matrix-room-type-sample-item'],
        match: (fieldName) => fieldName === 'roomType'
    },
});

extensionRegistry.registerExtension('AnalyticsChart', 'FieldValueName', {
    type: 'matrixSampleMessageTypeItem',
    match: (fieldName) => fieldName === 'messageType',
    getLabel: (fieldName, fieldValue) => {
        return exoi18n.i18n.t(`analytics.chat.${fieldValue}.label`);
    }
});

extensionRegistry.registerExtension('AnalyticsChart', 'FieldValueName', {
    type: 'matrixSampleRoomTypeItem',
    match: (fieldName) => fieldName === 'roomType',
    getLabel: (fieldName, fieldValue) => {
        return exoi18n.i18n.t(`analytics.chat.roomType.${fieldValue}.label`);
    }
});
