/* =====================================================================
   Sayfa koruma.

   Not: bu bir guvenlik onlemi DEGILDIR, sadece kullanici deneyimidir.
   Gercek koruma sunucudadir; token'siz istek 401 doner. Buradaki is,
   girmemis kullaniciyi bos bir panele bakarken birakmamak.
   ===================================================================== */

import { getSession, signOut } from "./auth.js";
import { mountNav } from "../components/nav.js";
import { mountWakeBanner } from "../components/wakeBanner.js";
import { demoSeridiniKur } from "../components/demoSeridi.js";

/**
 * Korumali sayfalarin basinda cagrilir. Oturum yoksa giris sayfasina
 * yonlendirir ve donus adresini saklar.
 *
 * @returns {Promise<object|null>} oturum; yonlendirme yapildiysa null
 */
export async function requireAuth() {
    const session = await getSession();

    if (!session) {
        const target = window.location.pathname + window.location.search;
        sessionStorage.setItem("donusAdresi", target);
        window.location.replace("/giris.html?oturum=gerekli");
        return null;
    }

    mountNav({ loggedIn: true });
    bindLogout();
    // Demo hesabinda gorunur DEMO etiketi (SPEC 12.2); gercek hesapta hicbir sey cizilmez.
    demoSeridiniKur();
    // Sunucu uykudan uyanirken kullaniciyi bos ekranla birakmayalim.
    mountWakeBanner();
    return session;
}

/**
 * Herkese acik sayfalarda kullanilir: giris yapmis kullaniciyi
 * panele gonderir (ornegin giris sayfasina tekrar gelirse).
 */
export async function redirectIfLoggedIn(target = "/app/index.html") {
    const session = await getSession();
    if (session) {
        window.location.replace(target);
        return true;
    }
    return false;
}

/** Giris sonrasi nereye donulecegini soyler. */
export function popReturnUrl(fallback = "/app/index.html") {
    const saved = sessionStorage.getItem("donusAdresi");
    sessionStorage.removeItem("donusAdresi");
    return saved && saved.startsWith("/app/") ? saved : fallback;
}

function bindLogout() {
    document.addEventListener("click", async (e) => {
        const button = e.target.closest("[data-logout]");
        if (!button) return;
        e.preventDefault();
        button.disabled = true;
        await signOut();
        window.location.replace("/index.html");
    });
}
