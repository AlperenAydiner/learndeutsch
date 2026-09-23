/* =====================================================================
   DEMO şeridi (SPEC 12.2).

   Demo hesabında ekranın üstünde görünür bir etiket durur ve simüle
   tarih ("zamanı ileri sar") buradan seçilir. Gerçek hesapta bu şerit
   hiç çizilmez; örnek veri gerçek kullanıcı verisine karışmaz.
   ===================================================================== */

import { api } from "../core/api.js";

const ANAHTAR = "demoTarihi";

/** İstek başlığına konacak simüle tarih; demo değilse null. */
export function demoTarihi() {
    try {
        return sessionStorage.getItem(ANAHTAR);
    } catch {
        return null;
    }
}

/**
 * Hesap demo ise şeridi çizer. Çağıran sayfanın bir şey bilmesine
 * gerek yok: demo değilse sessizce çıkar.
 */
export async function demoSeridiniKur() {
    let ben;
    try {
        ben = await api.get("/me");
    } catch {
        return;
    }
    if (!ben?.demo) {
        try {
            sessionStorage.removeItem(ANAHTAR);
        } catch { /* depolama kapalı olabilir */ }
        return;
    }

    const secili = demoTarihi() ?? "";
    const serit = document.createElement("div");
    serit.className = "demobar";
    serit.innerHTML = `
        <span class="demobar__tag">DEMO</span>
        <span class="demobar__text">Bu hesap örnek veri içindir; gerçek ilerlemeni göstermez.</span>
        <label class="demobar__field">
            Simüle tarih
            <input type="date" id="demoTarih" value="${secili}">
        </label>
        <button class="btn btn--ghost btn--sm" type="button" id="demoSifirla">Bugüne dön</button>`;
    document.body.prepend(serit);

    document.getElementById("demoTarih").addEventListener("change", (e) => {
        yaz(e.target.value);
        location.reload();
    });
    document.getElementById("demoSifirla").addEventListener("click", () => {
        yaz("");
        location.reload();
    });
}

function yaz(deger) {
    try {
        if (deger) {
            sessionStorage.setItem(ANAHTAR, deger);
        } else {
            sessionStorage.removeItem(ANAHTAR);
        }
    } catch { /* depolama kapalı olabilir */ }
}
