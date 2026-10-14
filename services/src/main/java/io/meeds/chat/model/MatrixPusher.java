/*
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2026 Meeds Association contact@meeds.io
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package io.meeds.chat.model;

/**
 * An HTTP pusher of a Matrix account, as the client API lists and sets it
 * ({@code /_matrix/client/v3/pushers}). Synapse keys a pusher on the pair
 * ({@code appId}, {@code pushkey}): setting the same pair again replaces it.
 *
 * @param appId the application id, {@code exo.matrix.agent} for a digital
 *          worker's pusher
 * @param pushkey the key Synapse sends back to the gateway with each push
 * @param kind {@code http}, or null to delete the pusher
 * @param appDisplayName the application name shown in the account's pushers
 * @param deviceDisplayName the device name shown in the account's pushers
 * @param profileTag the profile tag, the platform login for a worker's pusher
 * @param lang the language of the pushed notifications
 * @param url the push gateway url, whose path Synapse requires to be
 *          {@code /_matrix/push/v1/notify}
 */
public record MatrixPusher(String appId,
                           String pushkey,
                           String kind,
                           String appDisplayName,
                           String deviceDisplayName,
                           String profileTag,
                           String lang,
                           String url) {
}
