/* =====================================================================
   Frontend yapilandirmasi.

   Buradaki publishable anahtar GIZLI DEGILDIR: tarayiciya gomulmek
   icin tasarlanmistir ve Supabase tarafindan "safely shared publicly"
   olarak isaretlenir. Veritabanindaki tum tablolarda RLS deny-all
   oldugu icin bu anahtarla hicbir satira erisilemez; tek kapi API'dir.

   Gizli olan (service_role / secret key) ASLA buraya konmaz.
   ===================================================================== */

export const CONFIG = {
    SUPABASE_URL: "https://ungaeahquejfmwwacgqb.supabase.co",
    SUPABASE_PUBLISHABLE_KEY: "sb_publishable_iBSJCDO43KPEyeFUa_1BNA_ycRjKNvV",
    API_BASE: "http://localhost:8080/api",
};
