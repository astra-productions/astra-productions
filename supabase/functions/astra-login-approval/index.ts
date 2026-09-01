import { withSupabase } from "jsr:@supabase/server@1";

const encoder = new TextEncoder();
const pepper = Deno.env.get("ASTRA_CODE_PEPPER");

const json = (body: unknown, status = 200) => Response.json(body, { status });
const validUuid = (value: unknown): value is string =>
  typeof value === "string" &&
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);

function randomToken(bytes = 32) {
  const data = crypto.getRandomValues(new Uint8Array(bytes));
  let binary = "";
  for (const value of data) binary += String.fromCharCode(value);
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
}

function randomCode() {
  const limit = Math.floor(0x100000000 / 1000000) * 1000000;
  const data = new Uint32Array(1);
  do crypto.getRandomValues(data); while (data[0] >= limit);
  return String(data[0] % 1000000).padStart(6, "0");
}

async function digest(value: string) {
  if (!pepper) throw new Error("ASTRA_CODE_PEPPER is missing");
  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode(pepper),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signed = new Uint8Array(await crypto.subtle.sign("HMAC", key, encoder.encode(value)));
  return Array.from(signed, (byte) => byte.toString(16).padStart(2, "0")).join("");
}

function sameHash(a: string, b: string) {
  if (a.length !== b.length) return false;
  let different = 0;
  for (let i = 0; i < a.length; i++) different |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return different === 0;
}

export default {
  fetch: withSupabase({ auth: "user" }, async (req, ctx) => {
    if (req.method !== "POST") return json({ error: "POST required" }, 405);

    try {
      const body = await req.json();
      const action = String(body?.action ?? "");
      const userId = String(
        (ctx as any).jwtClaims?.sub ?? (ctx as any).userClaims?.id ?? "",
      );
      const admin = ctx.supabaseAdmin;
      if (!userId) return json({ error: "Not authenticated" }, 401);

      if (action === "enroll-device") {
        const deviceName = String(body?.deviceName ?? "Astra Pulse Android").slice(0, 80);
        const deviceSecret = String(body?.deviceSecret ?? "");
        if (deviceSecret.length < 43) return json({ error: "Invalid device secret" }, 400);

        const { count, error: countError } = await admin
          .from("trusted_devices")
          .select("id", { count: "exact", head: true })
          .eq("user_id", userId)
          .is("revoked_at", null);
        if (countError) throw countError;
        if ((count ?? 0) > 0) return json({ error: "A trusted device already exists" }, 409);

        const credentialHash = await digest(`device:${userId}:${deviceSecret}`);
        const { data, error } = await admin
          .from("trusted_devices")
          .insert({
            user_id: userId,
            device_name: deviceName,
            public_key: `credential-v1:${credentialHash}`,
            last_seen_at: new Date().toISOString(),
          })
          .select("id, device_name, created_at")
          .single();
        if (error) throw error;
        return json({ device: data }, 201);
      }

      const requireDevice = async (secretValue: unknown) => {
        const secret = String(secretValue ?? "");
        if (secret.length < 43) return null;
        const expected = await digest(`device:${userId}:${secret}`);
        const { data, error } = await admin
          .from("trusted_devices")
          .select("id, public_key")
          .eq("user_id", userId)
          .is("revoked_at", null);
        if (error) throw error;
        const device = (data ?? []).find((entry) =>
          sameHash(String(entry.public_key ?? "").replace("credential-v1:", ""), expected)
        );
        if (!device) return null;
        await admin.from("trusted_devices")
          .update({ last_seen_at: new Date().toISOString() })
          .eq("id", device.id);
        return device;
      };

      const requireCloudSession = async (tokenValue: unknown) => {
        const token = String(tokenValue ?? "");
        if (token.length < 40) return null;
        const tokenHash = await digest(`session:${token}`);
        const { data, error } = await admin
          .from("cloud_sessions")
          .select("id")
          .eq("user_id", userId)
          .eq("token_hash", tokenHash)
          .is("revoked_at", null)
          .gt("expires_at", new Date().toISOString())
          .maybeSingle();
        if (error) throw error;
        if (!data) return null;
        await admin.from("cloud_sessions")
          .update({ last_seen_at: new Date().toISOString() })
          .eq("id", data.id);
        return data;
      };

      if (action === "devices" || action === "revoke-device") {
        const allowed = await requireDevice(body?.deviceSecret) ||
          await requireCloudSession(body?.sessionToken);
        if (!allowed) return json({ error: "Verified session required" }, 403);

        if (action === "revoke-device") {
          if (!validUuid(body?.deviceId)) return json({ error: "Invalid device" }, 400);
          const { error } = await admin.from("trusted_devices")
            .update({ revoked_at: new Date().toISOString() })
            .eq("id", body.deviceId)
            .eq("user_id", userId)
            .is("revoked_at", null);
          if (error) throw error;
        }

        const { data, error } = await admin.from("trusted_devices")
          .select("id, device_name, created_at, last_seen_at")
          .eq("user_id", userId)
          .is("revoked_at", null)
          .order("created_at", { ascending: false });
        if (error) throw error;
        return json({ devices: data ?? [] });
      }

      if (action === "request") {
        if (!validUuid(body?.browserSessionId)) return json({ error: "Invalid browser session" }, 400);
        const now = Date.now();
        const { data: recent } = await admin
          .from("login_approvals")
          .select("created_at")
          .eq("user_id", userId)
          .gte("created_at", new Date(now - 60000).toISOString());
        if ((recent?.length ?? 0) >= 3) return json({ error: "Too many requests" }, 429);

        const { data, error } = await admin
          .from("login_approvals")
          .insert({
            user_id: userId,
            browser_session_id: body.browserSessionId,
            expires_at: new Date(now + 5 * 60 * 1000).toISOString(),
          })
          .select("id, expires_at")
          .single();
        if (error) throw error;
        return json({ approvalId: data.id, expiresAt: data.expires_at }, 201);
      }

      if (action === "pending") {
        if (!await requireDevice(body?.deviceSecret)) return json({ error: "Trusted device required" }, 403);
        const { data, error } = await admin
          .from("login_approvals")
          .select("id, browser_session_id, expires_at, created_at")
          .eq("user_id", userId)
          .is("consumed_at", null)
          .is("code_hash", null)
          .gt("expires_at", new Date().toISOString())
          .order("created_at", { ascending: false })
          .limit(10);
        if (error) throw error;
        return json({ approvals: data ?? [] });
      }

      if (action === "generate") {
        if (!await requireDevice(body?.deviceSecret)) return json({ error: "Trusted device required" }, 403);
        if (!validUuid(body?.approvalId)) return json({ error: "Invalid approval" }, 400);
        const { data: approval, error: approvalError } = await admin
          .from("login_approvals")
          .select("*")
          .eq("id", body.approvalId)
          .eq("user_id", userId)
          .is("consumed_at", null)
          .gt("expires_at", new Date().toISOString())
          .single();
        if (approvalError || !approval) return json({ error: "Approval expired or unavailable" }, 404);

        const code = randomCode();
        const expiresAt = new Date(Date.now() + 5 * 60 * 1000).toISOString();
        const codeHash = await digest(`code:${approval.id}:${code}`);
        const { error } = await admin.from("login_approvals")
          .update({ code_hash: codeHash, attempts: 0, expires_at: expiresAt })
          .eq("id", approval.id).eq("user_id", userId);
        if (error) throw error;
        return json({ code, approvalId: approval.id, expiresAt });
      }

      if (action === "verify") {
        if (!validUuid(body?.approvalId) || !validUuid(body?.browserSessionId)) {
          return json({ error: "Invalid approval request" }, 400);
        }
        const code = String(body?.code ?? "");
        if (!/^\d{6}$/.test(code)) return json({ error: "Invalid code" }, 400);

        const { data: approval, error: approvalError } = await admin
          .from("login_approvals")
          .select("*")
          .eq("id", body.approvalId)
          .eq("user_id", userId)
          .eq("browser_session_id", body.browserSessionId)
          .is("consumed_at", null)
          .single();
        if (approvalError || !approval) return json({ error: "Approval unavailable" }, 404);
        if (new Date(approval.expires_at).getTime() <= Date.now()) return json({ error: "Code expired" }, 410);
        if (Number(approval.attempts) >= 5) return json({ error: "Too many attempts" }, 429);
        if (!approval.code_hash) return json({ error: "Code has not been generated" }, 409);

        const attempts = Number(approval.attempts) + 1;
        const suppliedHash = await digest(`code:${approval.id}:${code}`);
        if (!sameHash(String(approval.code_hash), suppliedHash)) {
          await admin.from("login_approvals").update({ attempts })
            .eq("id", approval.id).is("consumed_at", null);
          return json({ error: "Incorrect code", attemptsRemaining: Math.max(0, 5 - attempts) }, 401);
        }

        const now = new Date().toISOString();
        const { data: consumed, error: consumeError } = await admin
          .from("login_approvals")
          .update({ attempts, verified_at: now, consumed_at: now })
          .eq("id", approval.id)
          .is("consumed_at", null)
          .select("id")
          .maybeSingle();
        if (consumeError) throw consumeError;
        if (!consumed) return json({ error: "Code already used" }, 409);

        const sessionToken = randomToken();
        const tokenHash = await digest(`session:${sessionToken}`);
        const expiresAt = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString();
        const { error: sessionError } = await admin.from("cloud_sessions").upsert({
          user_id: userId,
          approval_id: approval.id,
          browser_session_id: body.browserSessionId,
          token_hash: tokenHash,
          device_name: String(body?.deviceName ?? "Webbrowser").slice(0, 80),
          expires_at: expiresAt,
          last_seen_at: now,
          revoked_at: null,
        }, { onConflict: "user_id,browser_session_id" });
        if (sessionError) throw sessionError;
        return json({ verified: true, sessionToken, expiresAt });
      }

      return json({ error: "Unknown action" }, 400);
    } catch (error) {
      const failure = error as {
        name?: string;
        message?: string;
        code?: string;
        details?: string;
        hint?: string;
        stack?: string;
      };
      console.error(JSON.stringify({
        name: failure?.name ?? "UnknownError",
        message: failure?.message ?? String(error),
        code: failure?.code ?? null,
        details: failure?.details ?? null,
        hint: failure?.hint ?? null,
        stack: failure?.stack ?? null,
      }));
      return json({
        error: "Internal server error",
        reference: failure?.code ?? "EDGE_FAILURE",
      }, 500);
    }
  }),
};
