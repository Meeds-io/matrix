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

function retrieveDatabase(dbSettings) {
  const request = window.indexedDB.open(dbSettings.dbName, dbSettings.version);
  return new Promise((resolve, reject) => {
    request.onerror = reject;
    request.onsuccess = e => resolve(e.target.result);
    request.onupgradeneeded = e => {
      try {
        e.target.result.createObjectStore(dbSettings.dbStore);
      } catch (e) {
        console.debug('Error upgrading database version', e);
        reject(e);
      }
    };
  });
}


async function getDatabase(dbSettings) {
  if (await isDatabaseExists(dbSettings.dbName)) {
    return retrieveDatabase(dbSettings);
  } else {
    return null;
  }
}

export async function deleteDatabase(dbSettings) {
  if (await isDatabaseExists(dbSettings.dbName)) {
    const db = await retrieveDatabase(dbSettings);
    if (db) {
      return new Promise((resolve, reject) => {
        const request = window.indexedDB.deleteDatabase(dbSettings.dbName);
        request.onerror = e => {
          console.error(e);
          reject(e);
        };
        request.onsuccess = resolve;
      });
    }
  }
}

export async function createDatabase(dbSettings) {
  await deleteDatabase(dbSettings);
  return new Promise((resolve, reject) => {
    window.setTimeout(async () => {
      try {
        const db = await retrieveDatabase(dbSettings);
        await setValue('userName', eXo.env.portal.userName);
        resolve(db);
      } catch (e) {
        reject(e);
      }
    }, 200);
  });
}

export async function isDatabaseExists(dbName) {
  const dbs = await window.indexedDB.databases();
  return !!dbs?.find?.(db => db.name === dbName);
}

export async function getValue(dbSettings, paramName) {
  const database = await getDatabase(dbSettings);
  if (!database) {
    return null;
  }
  return new Promise(resolve => {
    const transaction = database.transaction([dbSettings.dbStore], 'readonly');
    const request = transaction.objectStore(dbSettings.dbStore).get(paramName);
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => resolve(null);
  });
}

export async function setValue(dbSettings, paramName, paramValue) {
  const database = await getDatabase(dbSettings);
  if (!database) {
    return null;
  }
  return new Promise(resolve => {
    const transaction = database.transaction([dbSettings.dbStore], 'readwrite');
    transaction.oncomplete = () => {
      transaction.db.close();
      resolve();
    };
    transaction.objectStore(dbSettings.dbStore).put(paramValue, paramName);
  });
}