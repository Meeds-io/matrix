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
  const request = window.indexedDB.open(dbSettings.DB_NAME, dbSettings.DB_VERSION);

  return new Promise((resolve, reject) => {
    request.onerror = reject;

    request.onsuccess = e => resolve(e.target.result);

    request.onupgradeneeded = e => {
      try {
        const db = e.target.result;
        const stores = dbSettings.DB_STORES;

        for (const storeName of Object.values(stores)) {
          if (!db.objectStoreNames.contains(storeName)) {
            db.createObjectStore(storeName);
          }
        }
      } catch (e) {
        console.debug('Error during DB upgrade:', e);
        reject(e);
      }
    };
  });
}


async function getDatabase(dbSettings) {
  if (await isDatabaseExists(dbSettings.DB_NAME)) {
    return retrieveDatabase(dbSettings);
  } else {
    return null;
  }
}

export async function deleteDatabase(dbSettings) {
  if (await isDatabaseExists(dbSettings.DB_NAME)) {
    const db = await retrieveDatabase(dbSettings);
    if (db) {
      return new Promise((resolve, reject) => {
        const request = window.indexedDB.deleteDatabase(dbSettings.DB_NAME);
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

export async function getValue(dbSettings, store, paramName) {
  const database = await getDatabase(dbSettings);
  if (!database) {
    return null;
  }
  return new Promise(resolve => {
    const transaction = database.transaction([store], 'readonly');
    const request = transaction.objectStore(store).get(paramName);
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => resolve(null);
  });
}

export async function setValue(dbSettings, store, paramName, paramValue) {
  const database = await getDatabase(dbSettings);
  if (!database) {
    return null;
  }
  return new Promise(resolve => {
    const transaction = database.transaction([store], 'readwrite');
    transaction.oncomplete = () => {
      transaction.db.close();
      resolve();
    };
    transaction.objectStore(store).put(paramValue, paramName);
  });
}

export async function getAllKeys(dbSettings, storeName) {
  const database = await getDatabase(dbSettings);
  if (!database) {
    return [];
  }

  return new Promise((resolve, reject) => {
    const transaction = database.transaction([storeName], 'readonly');
    const objectStore = transaction.objectStore(storeName);
    const request = objectStore.getAllKeys();

    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}
