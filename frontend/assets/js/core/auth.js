/* =====================================================================
   Kimlik dogrulama — Supabase Auth sarmalayicisi.

   Sifreler, e-posta dogrulamasi ve oturum yenileme Supabase tarafindadir;
   buradan gecmez. Bizim isimiz elimizdeki access token'i API'ye tasimak.
   ===================================================================== */

import { createClient } from "https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2.116.0/+esm";
import { CONFIG } from "./config.js";

export const supabase = createClient(
    CONFIG.SUPABASE_URL,
    CONFIG.SUPABASE_PUBLISHABLE_KEY,
    {
        auth: {
            persistSession: true,
            autoRefreshToken: true,
            detectSessionInUrl: true,
        },
    },
);

/**
 * Kayit. E-posta dogrulamasi acik oldugu icin donen oturum {@code null}
 * olur: kullanici once postasindaki baglantiya tiklamalidir.
 *
 * @returns {Promise<{ needsConfirmation: boolean }>}
 */
export async function signUp({ email, password, displayName }) {
    const { data, error } = await supabase.auth.signUp({
        email,
        password,
        options: {
            data: { display_name: displayName },
            emailRedirectTo: `${window.location.origin}/giris.html?dogrulandi=1`,
        },
    });
    if (error) throw toTurkishError(error);
    return { needsConfirmation: data.session === null };
}

export async function signIn({ email, password }) {
    const { error } = await supabase.auth.signInWithPassword({ email, password });
    if (error) throw toTurkishError(error);
}

export async function signOut() {
    await supabase.auth.signOut();
}

/** Gecerli oturum yoksa null. Token'i gerekirse otomatik yeniler. */
export async function getSession() {
    const { data } = await supabase.auth.getSession();
    return data.session ?? null;
}

export async function getAccessToken() {
    const session = await getSession();
    return session?.access_token ?? null;
}

export async function isLoggedIn() {
    return (await getSession()) !== null;
}

export async function resendConfirmation(email) {
    const { error } = await supabase.auth.resend({ type: "signup", email });
    if (error) throw toTurkishError(error);
}

/**
 * Supabase hata mesajlari Ingilizce gelir. Kullaniciya gosterilecek
 * metni Turkcelestirir; tanimadigimiz bir hata icin genel mesaj doner.
 */
function toTurkishError(error) {
    const raw = (error?.message ?? "").toLowerCase();

    const table = [
        ["invalid login credentials", "E-posta veya şifre hatalı."],
        ["email not confirmed", "E-postanı henüz doğrulamamışsın. Posta kutunu kontrol et."],
        ["user already registered", "Bu e-posta zaten kayıtlı. Giriş yapmayı dene."],
        ["already been registered", "Bu e-posta zaten kayıtlı. Giriş yapmayı dene."],
        ["password should be at least", "Şifre en az 8 karakter olmalı."],
        ["unable to validate email address", "E-posta adresi geçerli görünmüyor."],
        ["invalid email", "E-posta adresi geçerli görünmüyor."],
        ["for security purposes", "Çok sık denedin. Bir dakika bekleyip tekrar dene."],
        ["email rate limit exceeded", "Çok fazla e-posta gönderildi. Biraz bekle."],
        ["over_email_send_rate_limit", "Çok fazla e-posta gönderildi. Biraz bekle."],
        ["failed to fetch", "Sunucuya ulaşılamadı. İnternet bağlantını kontrol et."],
    ];

    for (const [needle, message] of table) {
        if (raw.includes(needle)) return new Error(message);
    }
    return new Error("Bir sorun oluştu, lütfen tekrar dene.");
}
