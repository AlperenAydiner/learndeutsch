/* =====================================================================
   API istemcisi.

   Her istege Supabase access token'ini ekler. Sunucu kullanici kimligini
   HER ZAMAN bu token'dan okur; istemci hicbir yerde kendi kullanici
   kimligini gondermez.
   ===================================================================== */

import { CONFIG } from "./config.js";
import { getAccessToken } from "./auth.js";

/** Sunucunun dondurdugu hata. `status` ve alan hatalarini tasir. */
export class ApiError extends Error {
    constructor(message, status, fields) {
        super(message);
        this.name = "ApiError";
        this.status = status;
        this.fields = fields ?? null;
    }
}

async function request(path, { method = "GET", body, auth = true } = {}) {
    const headers = {};
    if (body !== undefined) headers["Content-Type"] = "application/json";

    if (auth) {
        const token = await getAccessToken();
        if (!token) throw new ApiError("Oturum bulunamadı, tekrar giriş yap", 401);
        headers["Authorization"] = `Bearer ${token}`;
    }

    let response;
    try {
        response = await fetch(`${CONFIG.API_BASE}${path}`, {
            method,
            headers,
            body: body === undefined ? undefined : JSON.stringify(body),
        });
    } catch {
        throw new ApiError("Sunucuya ulaşılamadı. Backend çalışıyor mu?", 0);
    }

    if (response.status === 204) return null;

    const text = await response.text();
    const payload = text ? safeJson(text) : null;

    if (!response.ok) {
        throw new ApiError(
            payload?.message ?? "Beklenmeyen bir hata oluştu",
            response.status,
            payload?.fields,
        );
    }
    return payload;
}

function safeJson(text) {
    try {
        return JSON.parse(text);
    } catch {
        return null;
    }
}

export const api = {
    get: (path) => request(path),
    post: (path, body) => request(path, { method: "POST", body }),
    patch: (path, body) => request(path, { method: "PATCH", body }),
    delete: (path) => request(path, { method: "DELETE" }),
};
