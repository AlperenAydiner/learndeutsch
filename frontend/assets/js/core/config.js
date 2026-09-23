/* =====================================================================
   Frontend yapilandirmasi.

   Buradaki publishable anahtar GIZLI DEGILDIR: tarayiciya gomulmek
   icin tasarlanmistir ve Supabase tarafindan "safely shared publicly"
   olarak isaretlenir. Veritabanindaki tum tablolarda RLS deny-all
   oldugu icin bu anahtarla hicbir satira erisilemez; tek kapi API'dir.

   Gizli olan (service_role / secret key) ASLA buraya konmaz.

   Supabase projeleri: canli (Alperen Proje) ve gelistirme
   (ichsprechedeutsch-dev), ikisi de eu-central-1 (Frankfurt).
   ===================================================================== */

/** Sayfa localhost'tan mi servis ediliyor? */
const YEREL = ["localhost", "127.0.0.1"].includes(window.location.hostname);

/**
 * Backend adresi. Yerelde kendi makinendeki Spring Boot, canlida Render.
 * Deploy sonrasi RENDER_API degerini gercek adresle degistir.
 */
const RENDER_API = "https://ichsprechedeutsch-api.onrender.com/api";

/**
 * Iki Supabase projesi var. Yerelde GELISTIRME projesi kullanilir:
 * yerel backend (tools/backend.cmd, backend/.env.dev) onun veritabanina
 * baglanir ve onun JWT'lerini dogrular. Boylece yerelde acilan hesaplar
 * ve denemeler canliya dokunmaz.
 */
const SUPABASE = YEREL
    ? { // ichsprechedeutsch-dev — eu-central-1
        url: "https://lokxrmomepycydvxcsfq.supabase.co",
        key: "sb_publishable_w-E3gxaJg8x_hm5INB9Umg_9jp2psFP",
    }
    : { // Canli da ayni proje (K-031): eski canli proje silindi (K-009).
        url: "https://lokxrmomepycydvxcsfq.supabase.co",
        key: "sb_publishable_w-E3gxaJg8x_hm5INB9Umg_9jp2psFP",
    };

export const CONFIG = {
    SUPABASE_URL: SUPABASE.url,
    SUPABASE_PUBLISHABLE_KEY: SUPABASE.key,
    API_BASE: YEREL ? "http://localhost:8080/api" : RENDER_API,
    YEREL,
};
