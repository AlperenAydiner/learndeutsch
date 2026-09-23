/* =====================================================================
   API istemcisi.

   Her istege Supabase access token'ini ekler. Sunucu kullanici kimligini
   HER ZAMAN bu token'dan okur; istemci hicbir yerde kendi kullanici
   kimligini gondermez.
   ===================================================================== */

import { CONFIG } from "./config.js";
import { getAccessToken } from "./auth.js";
import { demoTarihi } from "../components/demoSeridi.js";

/**
 * Sunucu bu sureden uzun sessiz kalirsa kullaniciya haber verilir.
 *
 * Render'in ucretsiz katmani servisi 15 dakika hareketsizlikten sonra
 * uykuya alir; ilk istek konteyneri ve JVM'i ayaga kaldirdigi icin
 * 30-50 saniye surebilir. Bu sure boyunca "Yukleniyor..." yazip susmak
 * siteyi bozuk gosterir. Ne oldugunu soylemek daha durust.
 */
const UYANDIRMA_ESIGI_MS = 5000;

/** Uyanma beklerken tetiklenen olay: sayfalar dinleyip mesaj gosterebilir. */
export const API_EVENTS = new EventTarget();

/** Sunucunun cevap vermesi icin beklenecek en uzun sure. */
const ZAMAN_ASIMI_MS = 90000;

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

    // SPEC 12.2: simule tarih. Sunucu bunu YALNIZ demo hesabinda dikkate alir.
    const demo = demoTarihi();
    if (demo) headers["X-Demo-Date"] = demo;

    const uyandirma = setTimeout(
        () => API_EVENTS.dispatchEvent(new Event("uyaniyor")), UYANDIRMA_ESIGI_MS);
    const kontrol = new AbortController();
    const zamanAsimi = setTimeout(() => kontrol.abort(), ZAMAN_ASIMI_MS);

    let response;
    try {
        response = await fetch(`${CONFIG.API_BASE}${path}`, {
            method,
            headers,
            body: body === undefined ? undefined : JSON.stringify(body),
            signal: kontrol.signal,
        });
    } catch (err) {
        if (err.name === "AbortError") {
            throw new ApiError(
                "Sunucu cevap vermedi. Biraz bekleyip sayfayı yenile.", 0);
        }
        throw new ApiError(
            CONFIG.YEREL
                ? "Sunucuya ulaşılamadı. Backend çalışıyor mu?"
                : "Sunucuya ulaşılamadı. İnternet bağlantını kontrol et.", 0);
    } finally {
        clearTimeout(uyandirma);
        clearTimeout(zamanAsimi);
        API_EVENTS.dispatchEvent(new Event("uyandi"));
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
    put: (path, body) => request(path, { method: "PUT", body }),
    patch: (path, body) => request(path, { method: "PATCH", body }),
    delete: (path) => request(path, { method: "DELETE" }),
};
