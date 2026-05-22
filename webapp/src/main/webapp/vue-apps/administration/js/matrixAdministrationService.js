
export function isChatEnabled() {
  return fetch('/matrix/rest/chatAdministration/isChatEnabled', {
    method: 'GET',
  }).then(resp => {
    if (!resp?.ok) {
      throw new Error('Response code indicates a server error', resp);
    } else {
      return resp.json();
    }
  });
}

export function enableChatFeature(enableChat) {
  const formData = new FormData();
  formData.append('enabled', enableChat);

  return fetch('/matrix/rest/chatAdministration/enableChat', {
    credentials: 'include',
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: new URLSearchParams(formData).toString(),
  }).then(resp => {
    if (!resp?.ok) {
      throw new Error('Response code indicates a server error', resp);
    } else {
      return resp.text();
    }
  });
}