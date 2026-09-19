/* =====================================================================
   Frontend yapilandirmasi.

   Buradaki publishable anahtar GIZLI DEGILDIR: tarayiciya gomulmek
   icin tasarlanmistir ve Supabase tarafindan "safely shared publicly"
   olarak isaretlenir. Veritabanindaki tum tablolarda RLS deny-all
   oldugu icin bu anahtarla hicbir satira erisilemez; tek kapi API'dir.

   Gizli olan (service_role / secret key) ASLA buraya konmaz.

   Supabase projesi: Alperen Proje — eu-central-1 (Frankfurt)
   ===================================================================== */

/** Sayfa localhost'tan mi servis ediliyor? */
const YEREL = ["localhost", "127.0.0.1"].includes(window.location.hostname);

/**
 * Backend adresi. Yerelde kendi makinendeki Spring Boot, canlida Render.
 * Deploy sonrasi RENDER_API degerini gercek adresle degistir.
 */
const RENDER_API = "https://ichsprechedeutsch-api.onrender.com/api";

export const CONFIG = {
    SUPABASE_URL: "https://ladsutkfjsybylbnnmok.supabase.co",
    SUPABASE_PUBLISHABLE_KEY: "sb_publishable_B2ftQK5Avb0NePBMZzSuJw_BdyVDGTC",
    API_BASE: YEREL ? "http://localhost:8080/api" : RENDER_API,
    YEREL,
};
