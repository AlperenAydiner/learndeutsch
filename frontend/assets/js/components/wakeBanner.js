/* =====================================================================
   "Sunucu uyaniyor" bandi.

   Render'in ucretsiz katmani servisi 15 dakika hareketsizlikten sonra
   uykuya alir. Ilk istek onu ayaga kaldirir ve 30-50 saniye surebilir.
   Bu sure boyunca ekranda "Yukleniyor..." yazip susmak, siteyi bozuk
   gosterir: kullanici bir seyin takildigini sanir ve sayfayi kapatir.

   Ne oldugunu ve ne kadar surecegini soylemek hem durust hem de
   beklemeyi katlanilir kiliyor.
   ===================================================================== */

import { API_EVENTS } from "../core/api.js";

const ID = "uyanmaBandi";

export function mountWakeBanner() {
    API_EVENTS.addEventListener("uyaniyor", goster);
    API_EVENTS.addEventListener("uyandi", gizle);
}

function goster() {
    if (document.getElementById(ID)) return;

    const bant = document.createElement("div");
    bant.id = ID;
    bant.setAttribute("role", "status");
    bant.innerHTML = `
        <span class="uyanma__nokta" aria-hidden="true"></span>
        <span>Sunucu uyanıyor — ilk açılış yarım dakika kadar sürebilir.</span>`;

    bant.style.cssText = `
        position: fixed; left: 50%; bottom: 20px; transform: translateX(-50%);
        display: flex; align-items: center; gap: 10px;
        padding: 10px 18px; z-index: 100;
        background: var(--surface); color: var(--text);
        border: 1px solid var(--border-strong); border-radius: 999px;
        box-shadow: var(--shadow-lg); font-size: 0.875rem;
        max-width: calc(100vw - 32px);`;

    if (!document.getElementById(ID + "Stil")) {
        const stil = document.createElement("style");
        stil.id = ID + "Stil";
        stil.textContent = `
            .uyanma__nokta {
                width: 8px; height: 8px; border-radius: 50%;
                background: var(--partial); flex: none;
                animation: uyanmaNabiz 1.2s ease-in-out infinite;
            }
            @keyframes uyanmaNabiz {
                0%, 100% { opacity: 1; }
                50%      { opacity: 0.25; }
            }
            @media (prefers-reduced-motion: reduce) {
                .uyanma__nokta { animation: none; }
            }`;
        document.head.appendChild(stil);
    }

    document.body.appendChild(bant);
}

function gizle() {
    document.getElementById(ID)?.remove();
}
