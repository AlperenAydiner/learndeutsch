/* =====================================================================
   Takvim tarihleri.

   Sunucu "2026-09-21" gibi saat icermeyen takvim tarihleri gonderiyor.
   Bunlari dogrudan Date'e vermek iki sessiz hataya yol aciyordu:

   1. new Date("2026-09-21") degeri UTC gece yarisi olarak okur. UTC+3'te
      bu yerel saatle 21 Eylul 03:00'tur; sorun yok. Ama UTC'nin
      gerisindeki bir makinede 20 Eylul aksami olur ve ekranda bir
      onceki gun yazar.
   2. new Date().toISOString() UTC tarihini verir. Turkiye UTC+3 oldugu
      icin gece 00:00-03:00 arasi hala bir onceki gunu dondurur; program
      takviminde "bugun" yanlis gune isaretlenirdi.

   Cozum: takvim tarihlerini UTC'de bicimle (kaydirma olmaz), "bugun"u
   ise kullanicinin yerel takviminden uret.
   ===================================================================== */

/** Kullanicinin yerel takvimine gore bugun: "YYYY-AA-GG". */
export function bugunYerel() {
    const d = new Date();
    const ay = String(d.getMonth() + 1).padStart(2, "0");
    const gun = String(d.getDate()).padStart(2, "0");
    return `${d.getFullYear()}-${ay}-${gun}`;
}

/**
 * Takvim tarihini Turkce yazar. Saat dilimi kaydirmaz.
 *
 * @param {string} iso "YYYY-AA-GG"
 * @param {Intl.DateTimeFormatOptions} secenekler
 */
export function tarihYaz(iso, secenekler = { day: "numeric", month: "long", year: "numeric" }) {
    if (!iso) return "";
    return new Date(iso + "T00:00:00Z")
        .toLocaleDateString("tr-TR", { ...secenekler, timeZone: "UTC" });
}

/** Iki takvim tarihi arasindaki tam gun farki (b - a). */
export function gunFarki(a, b) {
    return Math.round(
        (Date.parse(b + "T00:00:00Z") - Date.parse(a + "T00:00:00Z")) / 86400000);
}
