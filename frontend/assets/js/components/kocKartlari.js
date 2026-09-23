/* =====================================================================
   Koc onerileri ve gunluk plan kartlari. Ana Sayfa ve Kocluk ayni
   gosterimi kullanir.

   Her oneri neden onerildigini yazar (K3). "Dogru cevap" gibi uydurma
   gerekce yok: metin sunucudan, dayandigi veri basis alanindan gelir.
   ===================================================================== */

import { esc } from "../core/etiket.js";

const TUR_ETIKET = {
    KELIME: "Kelime", GRAMER: "Gramer", HOEREN: "Hören",
    LESEN: "Lesen", SPRECHEN: "Sprechen", SCHREIBEN: "Schreiben",
};

const KURAL_ETIKET = {
    ASSESSMENT: "Seviye ölçümü",
    COMEBACK: "Geri dönüş",
    DUE_REVIEWS: "Tekrar zamanı",
    RECURRING_ERROR: "Tekrarlayan hata",
    WEAK_GRAMMAR: "Zayıf konu",
    MISSING_EVIDENCE: "Eksik kanıt",
    IDLE_SKILL: "İhmal edilen beceri",
    WEAK_ARTICLE: "Artikel",
    NEAR_GOAL: "Hedefe yakın",
    START: "Başlangıç planı",
};

export function turAdi(tur) {
    return TUR_ETIKET[tur] ?? tur;
}

export function oneriListesi(oneriler) {
    if (!oneriler || oneriler.length === 0) {
        return `<p class="loading">Şu an öne çıkan bir öneri yok. Planındaki işlere devam edebilirsin.</p>`;
    }
    return `<ul class="recs">${oneriler.map(oneriKarti).join("")}</ul>`;
}

function oneriKarti(r) {
    const eylem = r.action.kind === "SITE"
        ? `<a class="btn btn--primary" href="${esc(r.action.href)}">Başla</a>`
        : `<span class="rec__hint">${esc(r.action.hint)}</span>
           ${r.action.logResult ? `<span class="rec__log">Bitince sonucunu “Bugün ne yaptın?”a gir.</span>` : ""}`;

    return `
        <li class="rec">
            <div class="rec__head">
                <span class="badge badge--primary">${esc(KURAL_ETIKET[r.rule] ?? r.rule)}</span>
                <span class="rec__minutes">${r.minutes} dk</span>
            </div>
            <h3 class="rec__title">${esc(r.title)}</h3>
            <p class="rec__reason">${esc(r.reason)}</p>
            <div class="rec__action">${eylem}</div>
        </li>`;
}

/** Bugunun onerilen plani. Zorunlu degildir; "gorevi baslat" yok. */
export function planListesi(daily) {
    if (!daily || daily.items.length === 0) {
        return "";
    }
    const yapilan = daily.doneToday ?? {};
    const satirlar = daily.items.map((it) => {
        const done = yapilan[it.type] ?? 0;
        return `
            <li class="planline">
                <span class="planline__min">${it.minutes} dk</span>
                <span class="planline__type">${esc(turAdi(it.type))}</span>
                <span class="planline__hint">${esc(it.hint ?? "")}</span>
                ${done ? `<span class="planline__done">bugün ${done} dk yapıldı</span>` : ""}
            </li>`;
    }).join("");
    return `<ul class="plan">${satirlar}</ul>`;
}

export function haftalikPlan(weekly) {
    const satirlar = weekly.items.map((w) => {
        const yuzde = w.targetMinutes === 0 ? 0 : Math.min(100, Math.round((w.doneMinutes / w.targetMinutes) * 100));
        return `
            <li class="weekline">
                <span class="weekline__type">${esc(turAdi(w.type))}</span>
                <span class="weekline__bar"><span style="width:${yuzde}%"></span></span>
                <span class="weekline__num">${w.doneMinutes} / ${w.targetMinutes} dk</span>
            </li>`;
    }).join("");
    return `<ul class="week">${satirlar}</ul>`;
}
