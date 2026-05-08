'use strict';

// ── Base64URL helpers ────────────────────────────────────────────────────────

function base64urlToBuffer(base64url) {
  const base64 = base64url.replace(/-/g, '+').replace(/_/g, '/');
  const padded = base64.padEnd(base64.length + (4 - base64.length % 4) % 4, '=');
  const binary = atob(padded);
  const buffer = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) buffer[i] = binary.charCodeAt(i);
  return buffer.buffer;
}

function bufferToBase64url(buffer) {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
}

// ── UI helpers ────────────────────────────────────────────────────────────────

function showMsg(id, text, type) {
  const el = document.getElementById(id);
  el.textContent = text;
  el.className = type; // 'error' | 'success'
}

function showView(name) {
  document.getElementById('view-login').classList.toggle('hidden', name !== 'login');
  document.getElementById('view-home').classList.toggle('hidden', name !== 'home');
}

// ── Bootstrap: check current session ─────────────────────────────────────────

async function init() {
  try {
    const res = await fetch('/api/me', { credentials: 'same-origin' });
    if (res.ok) {
      const { username } = await res.json();
      showHome(username);
    } else {
      showView('login');
    }
  } catch {
    showView('login');
  }
}

function showHome(username) {
  document.getElementById('user-badge').textContent = `Signed in as: ${username}`;
  document.getElementById('msg-home').className = '';
  document.getElementById('msg-home').textContent = '';
  showView('home');
  loadPasskeys();
}

// ── Password login ────────────────────────────────────────────────────────────

async function login() {
  const username = document.getElementById('username').value.trim();
  const password = document.getElementById('password').value;
  if (!username || !password) return showMsg('msg', 'Enter username and password.', 'error');

  const res = await fetch('/api/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'same-origin',
    body: JSON.stringify({ username, password }),
  });

  if (res.ok) {
    const { username: u } = await res.json();
    showHome(u);
  } else {
    showMsg('msg', 'Invalid credentials.', 'error');
  }
}

// ── Passkey registration ──────────────────────────────────────────────────────

async function registerPasskey() {
  try {
    // 1. Get creation options
    const optRes = await fetch('/webauthn/register/options', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'same-origin',
    });
    if (!optRes.ok) throw new Error('Failed to get registration options');
    const options = await optRes.json();

    // 2. Convert byte fields from Base64URL to ArrayBuffer
    options.challenge = base64urlToBuffer(options.challenge);
    options.user.id = base64urlToBuffer(options.user.id);
    if (options.excludeCredentials) {
      options.excludeCredentials = options.excludeCredentials.map(c => ({
        ...c, id: base64urlToBuffer(c.id),
      }));
    }

    // 3. Create credential via browser
    const credential = await navigator.credentials.create({ publicKey: options });

    // 4. Encode credential for the server
    const label = `Passkey – ${new Date().toLocaleDateString()}`;
    const body = {
      publicKey: {
        credential: {
          id: credential.id,
          rawId: bufferToBase64url(credential.rawId),
          response: {
            attestationObject: bufferToBase64url(credential.response.attestationObject),
            clientDataJSON: bufferToBase64url(credential.response.clientDataJSON),
            transports: credential.response.getTransports?.() ?? [],
          },
          clientExtensionResults: credential.getClientExtensionResults?.() ?? {},
          type: credential.type,
        },
        label,
      },
    };

    // 5. Register
    const regRes = await fetch('/webauthn/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'same-origin',
      body: JSON.stringify(body),
    });

    if (regRes.ok) {
      showMsg('msg-home', 'Passkey registered successfully!', 'success');
      loadPasskeys();
    } else {
      const err = await regRes.json().catch(() => ({}));
      showMsg('msg-home', err.message ?? 'Registration failed.', 'error');
    }
  } catch (err) {
    if (err.name !== 'NotAllowedError') {
      showMsg('msg-home', `Error: ${err.message}`, 'error');
    }
  }
}

// ── Passkey authentication ────────────────────────────────────────────────────

async function loginWithPasskey() {
  try {
    // 1. Get request options
    const optRes = await fetch('/webauthn/authenticate/options', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'same-origin',
    });
    if (!optRes.ok) throw new Error('Failed to get authentication options');
    const options = await optRes.json();

    // 2. Convert byte fields
    options.challenge = base64urlToBuffer(options.challenge);
    if (options.allowCredentials) {
      options.allowCredentials = options.allowCredentials.map(c => ({
        ...c, id: base64urlToBuffer(c.id),
      }));
    }

    // 3. Get assertion via browser
    const assertion = await navigator.credentials.get({ publicKey: options });

    // 4. Encode assertion for the server
    const body = {
      id: assertion.id,
      rawId: bufferToBase64url(assertion.rawId),
      response: {
        authenticatorData: bufferToBase64url(assertion.response.authenticatorData),
        clientDataJSON: bufferToBase64url(assertion.response.clientDataJSON),
        signature: bufferToBase64url(assertion.response.signature),
        userHandle: assertion.response.userHandle
          ? bufferToBase64url(assertion.response.userHandle)
          : null,
      },
      clientExtensionResults: assertion.getClientExtensionResults?.() ?? {},
      type: assertion.type,
    };

    // 5. Authenticate — Spring Security creates the session and redirects to /
    const authRes = await fetch('/login/webauthn', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'same-origin',
      redirect: 'follow',
      body: JSON.stringify(body),
    });

    if (authRes.ok || authRes.redirected) {
      // Re-check session after authentication
      const meRes = await fetch('/api/me', { credentials: 'same-origin' });
      if (meRes.ok) {
        const { username } = await meRes.json();
        showHome(username);
      } else {
        showMsg('msg', 'Authentication succeeded but session check failed.', 'error');
      }
    } else {
      showMsg('msg', 'Passkey authentication failed.', 'error');
    }
  } catch (err) {
    if (err.name !== 'NotAllowedError') {
      showMsg('msg', `Error: ${err.message}`, 'error');
    }
  }
}

// ── Passkey management ────────────────────────────────────────────────────────

async function loadPasskeys() {
  const container = document.getElementById('passkeys-list');
  try {
    const res = await fetch('/api/passkeys', { credentials: 'same-origin' });
    if (!res.ok) throw new Error('Failed to load passkeys');
    const passkeys = await res.json();

    if (passkeys.length === 0) {
      container.innerHTML = '<p class="passkeys-empty">No passkeys registered.</p>';
      return;
    }

    container.innerHTML = passkeys.map(pk => `
      <div class="passkey-item" data-id="${pk.id}">
        <div class="passkey-info">
          <div class="passkey-label">${escapeHtml(pk.label)}</div>
          <div class="passkey-meta">
            Added ${formatDate(pk.created)} &middot; Last used ${formatDate(pk.lastUsed)}
          </div>
        </div>
        <button class="btn-delete" onclick="deletePasskey('${pk.id}')">Remove</button>
      </div>
    `).join('');
  } catch {
    container.innerHTML = '<p class="passkeys-empty">Could not load passkeys.</p>';
  }
}

async function deletePasskey(credentialId) {
  if (!confirm('Remove this passkey?')) return;
  const res = await fetch(`/api/passkeys/${encodeURIComponent(credentialId)}`, {
    method: 'DELETE',
    credentials: 'same-origin',
  });
  if (res.ok || res.status === 204) {
    loadPasskeys();
  } else {
    showMsg('msg-home', 'Failed to remove passkey.', 'error');
  }
}

function escapeHtml(str) {
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function formatDate(iso) {
  return new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}

// ── Logout ────────────────────────────────────────────────────────────────────

async function logout() {
  await fetch('/api/logout', { method: 'POST', credentials: 'same-origin' });
  showView('login');
}

// ── Start ─────────────────────────────────────────────────────────────────────

init();
