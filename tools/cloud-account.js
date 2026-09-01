(() => {
  "use strict";

  const SUPABASE_URL = "https://nbivaczwqsnbncwtpdzd.supabase.co";
  const PUBLISHABLE_KEY = "sb_publishable_XC8yCeObOu2WRAV7pRdqAA_PNG-ssc4";
  const FUNCTION_URL = `${SUPABASE_URL}/functions/v1/astra-login-approval`;
  const isAndroid = Boolean(window.AstraCloud?.isAndroidApp?.());
  let accessToken = sessionStorage.getItem("astra-cloud-access-token") || "";
  let approvalId = sessionStorage.getItem("astra-cloud-approval-id") || "";
  let browserSessionId = sessionStorage.getItem("astra-cloud-browser-id") || "";
  let expiresAt = sessionStorage.getItem("astra-cloud-code-expires") || "";
  let countdownId = 0;

  const style = document.createElement("style");
  style.textContent = `
    .cloud-profile { border-color: rgba(255,102,209,.62) !important; box-shadow: 0 0 24px rgba(255,72,205,.12); }
    .cloud-grid { display:grid; grid-template-columns:1fr 1fr; gap:10px; }
    .cloud-grid label { color:#d4bfd3; font-size:.75rem; font-weight:800; text-transform:uppercase; }
    .cloud-grid input { width:100%; margin-top:6px; }
    .cloud-actions { display:flex; flex-wrap:wrap; gap:9px; margin-top:12px; }
    .cloud-actions button { flex:1 1 150px; }
    .cloud-status { min-height:1.4em; margin:10px 0 0; color:#d7c8d8; line-height:1.45; }
    .cloud-status[data-error="true"] { color:#ff90aa; }
    .cloud-code { font-size:2rem; letter-spacing:.18em; text-align:center; font-weight:900; color:#fff; text-shadow:0 0 16px #ff58d3; margin:12px 0 4px; }
    .cloud-request { border:1px solid rgba(255,102,209,.25); padding:10px; margin-top:10px; }
    .cloud-request strong { display:block; margin-bottom:7px; }
    .cloud-hidden { display:none !important; }
    @media (max-width:600px) { .cloud-grid { grid-template-columns:1fr; } }
  `;
  document.head.append(style);

  function mount() {
    const localProfile = document.getElementById("localProfile");
    if (!localProfile || document.getElementById("cloudProfile")) return;
    const card = document.createElement("section");
    card.id = "cloudProfile";
    card.className = "cloud-profile local-profile card";
    card.innerHTML = `
      <div class="archive-head"><h2>Astra Cloud</h2><div class="version">${isAndroid ? "Android-Gerät" : "Webzugriff"}</div></div>
      <p class="local-only-note">Cloudzugriff ist freiwillig. Passwort und Bestätigungscode werden nicht in der App gespeichert.</p>
      <div class="cloud-grid" id="cloudLoginFields">
        <label>E-Mail<input id="cloudEmail" type="email" autocomplete="username" placeholder="name@beispiel.de"></label>
        <label>Cloud-Passwort<input id="cloudPassword" type="password" autocomplete="current-password"></label>
      </div>
      <div class="cloud-actions">
        <button class="primary" id="cloudLogin" type="button">CLOUD ANMELDEN</button>
        <button id="cloudLogout" type="button" class="cloud-hidden">ABMELDEN</button>
      </div>
      <div id="cloudAndroid" class="cloud-hidden">
        <div class="cloud-actions">
          <button id="cloudEnroll" type="button">DIESES GERÄT VERTRAUEN</button>
          <button id="cloudPending" type="button">ANFRAGEN LADEN</button>
        </div>
        <div id="cloudRequests"></div>
      </div>
      <div id="cloudWeb" class="cloud-hidden">
        <div class="cloud-actions"><button id="cloudRequest" type="button">CODE IN APP ANFORDERN</button></div>
        <div id="cloudVerifyBox" class="cloud-hidden">
          <div class="cloud-grid"><label>6-stelliger App-Code<input id="cloudCode" inputmode="numeric" maxlength="6" autocomplete="one-time-code"></label></div>
          <div class="cloud-actions"><button class="primary" id="cloudVerify" type="button">CODE BESTÄTIGEN</button></div>
          <p class="cloud-status" id="cloudCountdown"></p>
        </div>
      </div>
      <p class="cloud-status" id="cloudStatus"></p>
    `;
    localProfile.insertAdjacentElement("afterend", card);
    bind(card);
    updateSignedInState(card);
  }

  function status(card, message, error = false) {
    const output = card.querySelector("#cloudStatus");
    output.textContent = message;
    output.dataset.error = String(error);
  }

  async function signIn(email, password) {
    const response = await fetch(`${SUPABASE_URL}/auth/v1/token?grant_type=password`, {
      method: "POST",
      headers: { apikey: PUBLISHABLE_KEY, "Content-Type": "application/json" },
      body: JSON.stringify({ email, password })
    });
    const payload = await response.json();
    if (!response.ok || !payload.access_token) throw new Error(payload.error_description || payload.msg || "Anmeldung fehlgeschlagen.");
    return payload.access_token;
  }

  async function invoke(action, body = {}) {
    if (!accessToken) throw new Error("Bitte zuerst bei Astra Cloud anmelden.");
    const response = await fetch(FUNCTION_URL, {
      method: "POST",
      headers: {
        apikey: PUBLISHABLE_KEY,
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ action, ...body })
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(payload.error || `Cloudfehler ${response.status}`);
    return payload;
  }

  function deviceSecret() {
    return String(window.AstraCloud?.getOrCreateDeviceSecret?.() || "");
  }

  function ensureBrowserId() {
    if (!browserSessionId) {
      browserSessionId = crypto.randomUUID();
      sessionStorage.setItem("astra-cloud-browser-id", browserSessionId);
    }
    return browserSessionId;
  }

  function updateSignedInState(card) {
    const signedIn = Boolean(accessToken);
    card.querySelector("#cloudLoginFields").classList.toggle("cloud-hidden", signedIn);
    card.querySelector("#cloudLogin").classList.toggle("cloud-hidden", signedIn);
    card.querySelector("#cloudLogout").classList.toggle("cloud-hidden", !signedIn);
    card.querySelector("#cloudAndroid").classList.toggle("cloud-hidden", !signedIn || !isAndroid);
    card.querySelector("#cloudWeb").classList.toggle("cloud-hidden", !signedIn || isAndroid);
    card.querySelector("#cloudVerifyBox").classList.toggle("cloud-hidden", !approvalId || isAndroid);
    if (signedIn) status(card, isAndroid ? "Cloud angemeldet. Gerät verbinden oder offene Anfrage laden." : "Passwort bestätigt. Fordere jetzt den App-Code an.");
    updateCountdown(card);
  }

  function updateCountdown(card) {
    clearInterval(countdownId);
    const output = card.querySelector("#cloudCountdown");
    const tick = () => {
      const remaining = Math.max(0, Math.ceil((new Date(expiresAt).getTime() - Date.now()) / 1000));
      output.textContent = remaining ? `Code bleibt ${Math.floor(remaining / 60)}:${String(remaining % 60).padStart(2, "0")} Minuten gültig.` : "Der Code ist abgelaufen.";
      if (!remaining) clearInterval(countdownId);
    };
    if (approvalId && expiresAt) {
      tick();
      countdownId = window.setInterval(tick, 1000);
    } else output.textContent = "";
  }

  function renderRequests(card, approvals) {
    const host = card.querySelector("#cloudRequests");
    host.innerHTML = "";
    if (!approvals.length) {
      host.innerHTML = '<p class="cloud-status">Keine offene Web-Anfrage gefunden.</p>';
      return;
    }
    approvals.forEach((approval) => {
      const row = document.createElement("div");
      row.className = "cloud-request";
      row.innerHTML = `<strong>Web-Anmeldung von ${new Date(approval.created_at).toLocaleTimeString("de-DE")}</strong><button type="button">CODE ERZEUGEN</button><div class="cloud-code"></div><p class="cloud-status"></p>`;
      row.querySelector("button").addEventListener("click", async () => {
        try {
          const result = await invoke("generate", { approvalId: approval.id, deviceSecret: deviceSecret() });
          row.querySelector(".cloud-code").textContent = result.code;
          row.querySelector(".cloud-status").textContent = "Dieser Code gilt fünf Minuten und kann nur einmal verwendet werden.";
        } catch (error) {
          row.querySelector(".cloud-status").textContent = error.message;
        }
      });
      host.append(row);
    });
  }

  function bind(card) {
    card.querySelector("#cloudLogin").addEventListener("click", async () => {
      const email = card.querySelector("#cloudEmail").value.trim();
      const password = card.querySelector("#cloudPassword").value;
      status(card, "Anmeldung wird geprüft …");
      try {
        accessToken = await signIn(email, password);
        sessionStorage.setItem("astra-cloud-access-token", accessToken);
        card.querySelector("#cloudPassword").value = "";
        updateSignedInState(card);
      } catch (error) { status(card, error.message, true); }
    });

    card.querySelector("#cloudLogout").addEventListener("click", () => {
      accessToken = approvalId = expiresAt = "";
      ["astra-cloud-access-token", "astra-cloud-approval-id", "astra-cloud-code-expires"].forEach((key) => sessionStorage.removeItem(key));
      updateSignedInState(card);
      status(card, "Cloud abgemeldet.");
    });

    card.querySelector("#cloudEnroll").addEventListener("click", async () => {
      status(card, "Gerät wird gesichert registriert …");
      try {
        await invoke("enroll-device", { deviceName: navigator.userAgent.slice(0, 80), deviceSecret: deviceSecret() });
        status(card, "Dieses Android-Gerät ist jetzt als vertrauenswürdig registriert.");
      } catch (error) { status(card, error.message === "A trusted device already exists" ? "Es ist bereits ein vertrauenswürdiges Gerät registriert." : error.message, true); }
    });

    card.querySelector("#cloudPending").addEventListener("click", async () => {
      status(card, "Offene Anfragen werden geladen …");
      try {
        const result = await invoke("pending", { deviceSecret: deviceSecret() });
        renderRequests(card, result.approvals || []);
        status(card, "Anfragen aktualisiert.");
      } catch (error) { status(card, error.message, true); }
    });

    card.querySelector("#cloudRequest").addEventListener("click", async () => {
      status(card, "Anfrage wird erstellt …");
      try {
        const result = await invoke("request", { browserSessionId: ensureBrowserId() });
        approvalId = result.approvalId;
        expiresAt = result.expiresAt;
        sessionStorage.setItem("astra-cloud-approval-id", approvalId);
        sessionStorage.setItem("astra-cloud-code-expires", expiresAt);
        updateSignedInState(card);
        status(card, "Öffne jetzt Astra Pulse auf dem registrierten Android-Gerät und erzeuge dort den Code.");
      } catch (error) { status(card, error.message, true); }
    });

    card.querySelector("#cloudVerify").addEventListener("click", async () => {
      status(card, "Code wird geprüft …");
      try {
        const result = await invoke("verify", {
          approvalId,
          browserSessionId: ensureBrowserId(),
          code: card.querySelector("#cloudCode").value.trim(),
          deviceName: navigator.userAgent.slice(0, 80)
        });
        sessionStorage.setItem("astra-cloud-session-token", result.sessionToken);
        approvalId = expiresAt = "";
        sessionStorage.removeItem("astra-cloud-approval-id");
        sessionStorage.removeItem("astra-cloud-code-expires");
        updateSignedInState(card);
        status(card, "Cloudzugriff bestätigt. Dieser Browser ist jetzt für diese Sitzung freigeschaltet.");
      } catch (error) { status(card, error.message, true); }
    });
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", mount);
  else mount();
})();
