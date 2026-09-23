/* =====================================================================
   Dört becerinin site içi çalışması: Lesen, Hören, Schreiben, Sprechen.

   Hangi çalışmanın seviye kanıtı ürettiğini sunucu söyler (SPEC 4.2);
   burada uydurma yapılmaz, yanıttaki gerekçe olduğu gibi gösterilir.

   Hören, tarayıcının Web Speech API'siyle (de-DE) çalışır. Cihazda
   Almanca ses yoksa bu açıkça yazılır — olmayan yetenek varmış gibi
   gösterilmez (K4).
   ===================================================================== */

import { api } from "../core/api.js";
import { esc } from "../core/etiket.js";

const UMLAUTLAR = ["ä", "ö", "ü", "ß", "Ä", "Ö", "Ü"];

const MODULLER = {
    lesen: { yol: "reading", ad: "Lesen", baslik: "Okuma" },
    hoeren: { yol: "listening", ad: "Hören", baslik: "Dinleme" },
    schreiben: { yol: "writing", ad: "Schreiben", baslik: "Yazma" },
    sprechen: { yol: "speaking", ad: "Sprechen", baslik: "Konuşma" },
};

let durum = null;   // { tur, id, sessionId, veri, etkilesimler }
let kok = null;
let geriCagri = null;

/**
 * Bileşeni sayfaya bağlar.
 *
 * @param {HTMLElement} hedef  ekranların çizileceği alan
 * @param {Function}    geri   "Öğren'e dön" davranışı
 */
export function beceriKur(hedef, geri) {
    kok = hedef;
    geriCagri = geri;
}

export async function beceriListesi(tur) {
    const m = MODULLER[tur];
    if (!m) return;
    const liste = await api.get(`/skills/${m.yol}`);
    durum = null;
    kok.hidden = false;
    kok.innerHTML = `
        <header class="page-head">
            <div>
                <h1>${m.ad}</h1>
                <p class="page-head__sub">${m.baslik} çalışmaları — çalışma seviyene kadar olanlar.</p>
            </div>
            <button class="btn btn--ghost btn--sm" type="button" data-geri>Öğren'e dön</button>
        </header>
        ${liste.length === 0
            ? `<p class="block__note">Bu seviyede hazır içerik yok. Dışarıda çalışıp sonucunu
                 Ana Sayfa'daki "Bugün ne yaptın?" ile girebilirsin.</p>`
            : `<ul class="topics">${liste.map((t) => `
                <li class="topic">
                    <div class="topic__main">
                        <span class="topic__title">${esc(t.titleTr)}</span>
                        <span class="topic__meta">${t.level} · ${esc(t.subtitleTr ?? "")} · ${t.estimatedMinutes} dk${t.verified ? "" : " · içerik kontrol edilmedi"}</span>
                    </div>
                    <div class="topic__right">
                        <button class="btn btn--ghost btn--sm" type="button" data-gorev="${esc(t.id)}">Başla</button>
                    </div>
                </li>`).join("")}</ul>`}`;

    kok.querySelector("[data-geri]").addEventListener("click", () => geriCagri());
    kok.querySelectorAll("[data-gorev]").forEach((b) =>
        b.addEventListener("click", () => gorevAc(tur, b.dataset.gorev)));
}

/** Belirli bir görevi doğrudan açar (Sınav sayfasından gelen bağlantı). */
export async function beceriGorevAc(tur, id) {
    if (!MODULLER[tur]) return;
    kok.hidden = false;
    await gorevAc(tur, id);
}

async function gorevAc(tur, id) {
    const m = MODULLER[tur];
    const veri = await api.post(`/skills/${m.yol}/${id}/session`);
    durum = { tur, id, sessionId: veri.sessionId, veri, etkilesimler: [Date.now()] };
    if (tur === "lesen") ciz_lesen(veri);
    else if (tur === "hoeren") ciz_hoeren(veri);
    else if (tur === "schreiben") ciz_schreiben(veri);
    else ciz_sprechen(veri);
}

function ust(veri, notMetni) {
    return `
        <div class="session__top">
            <span class="session__count">${veri.level} · ${esc(veri.titleTr)}</span>
            <button class="btn btn--ghost btn--sm" type="button" data-geri>Vazgeç</button>
        </div>
        ${notMetni ? `<p class="block__note">${notMetni}</p>` : ""}`;
}

function sorularHtml(sorular) {
    return sorular.map((q, i) => `
        <li class="ptest__item" data-q="${esc(q.id)}">
            <p class="ptest__prompt">${i + 1}. ${esc(q.prompt)}</p>
            ${q.options
                ? `<div class="ptest__options">${q.options.map((o) =>
                    `<button type="button" class="pill" data-a="${esc(o)}" aria-pressed="false">${esc(o)}</button>`).join("")}</div>`
                : `<input class="field__input" data-yazili type="text" autocomplete="off" spellcheck="false">`}
        </li>`).join("");
}

function secimleriBagla() {
    const cevaplar = {};
    kok.querySelectorAll(".ptest__item").forEach((li) => {
        li.querySelectorAll("[data-a]").forEach((btn) => btn.addEventListener("click", () => {
            cevaplar[li.dataset.q] = btn.dataset.a;
            durum.etkilesimler.push(Date.now());
            li.querySelectorAll("[data-a]").forEach((x) => x.setAttribute("aria-pressed", String(x === btn)));
        }));
        const alan = li.querySelector("[data-yazili]");
        if (alan) {
            alan.addEventListener("input", () => {
                cevaplar[li.dataset.q] = alan.value;
            });
        }
    });
    return cevaplar;
}

// ---------- Lesen ----------

function ciz_lesen(v) {
    kok.innerHTML = `
        ${ust(v, "Metni oku, sonra soruları cevapla. Sonuç Lesen kanıtı olarak kaydedilir.")}
        <article class="card card--pad reading">
            <div class="reading__text">${esc(v.text).replace(/\n/g, "<br>")}</div>
            ${v.glossary?.length ? `
                <details class="reading__gloss">
                    <summary>Kelime yardımı</summary>
                    <ul>${v.glossary.map((g) => `<li><b>${esc(g.de)}</b> — ${esc(g.tr)}</li>`).join("")}</ul>
                </details>` : ""}
        </article>
        <ol class="ptest">${sorularHtml(v.questions)}</ol>
        <button class="btn btn--primary btn--lg" type="button" data-gonder>Cevapları gönder</button>
        <div data-sonuc></div>`;

    const cevaplar = secimleriBagla();
    kok.querySelector("[data-geri]").addEventListener("click", () => beceriListesi("lesen"));
    kok.querySelector("[data-gonder]").addEventListener("click", async (e) => {
        e.target.disabled = true;
        durum.etkilesimler.push(Date.now());
        const r = await api.post(`/skills/reading/${durum.id}/submit`,
            { sessionId: durum.sessionId, answers: cevaplar, interactions: durum.etkilesimler });
        sonucGoster(r, "lesen");
    });
}

// ---------- Hören ----------

function almancaSes() {
    if (!("speechSynthesis" in window)) return null;
    return speechSynthesis.getVoices().find((s) => s.lang?.toLowerCase().startsWith("de")) ?? null;
}

function ciz_hoeren(v) {
    const dikte = v.kind === "DICTATION";
    kok.innerHTML = `
        ${ust(v, "Bu alıştırma tarayıcının sesiyle çalışır. Sentetik ses olduğu için sonucu "
            + "seviye kanıtı saymıyoruz; çalışma geçmişine yazılır.")}
        <article class="card card--pad">
            <div class="btn-row">
                <button class="btn btn--primary" type="button" data-oku>▶ Dinle</button>
                <button class="btn btn--ghost btn--sm" type="button" data-yavas>Yavaş dinle</button>
            </div>
            <p class="block__note" data-sesnot></p>
            ${dikte ? `
                <p class="wordcard__hint">Duyduğun cümleyi yaz.</p>
                <input class="field__input" data-dikte type="text" autocomplete="off" spellcheck="false">
                <div class="btn-row umlauts">
                    ${UMLAUTLAR.map((u) => `<button type="button" class="pill pill--quiet" data-umlaut="${u}">${u}</button>`).join("")}
                </div>`
            : `<ol class="ptest">${sorularHtml(v.questions)}</ol>`}
        </article>
        <button class="btn btn--primary btn--lg" type="button" data-gonder>Gönder</button>
        <div data-sonuc></div>`;

    const cevaplar = dikte ? null : secimleriBagla();
    const not = kok.querySelector("[data-sesnot]");
    const ses = almancaSes();
    if (!("speechSynthesis" in window)) {
        not.textContent = "Bu tarayıcı sesli okumayı desteklemiyor; metni aşağıdan okuyarak çalışabilirsin.";
    } else if (!ses) {
        not.textContent = "Cihazında Almanca (de-DE) ses bulunamadı. Sistem ayarlarından Almanca ses "
            + "paketi yükleyebilir ya da bu alıştırmayı dışarıda yapabilirsin.";
    } else {
        not.textContent = `Ses: ${ses.name}. İstediğin kadar tekrar dinleyebilirsin.`;
    }

    const oku = (hiz) => {
        if (!("speechSynthesis" in window)) return;
        speechSynthesis.cancel();
        const u = new SpeechSynthesisUtterance(v.text);
        u.lang = "de-DE";
        if (ses) u.voice = ses;
        u.rate = hiz;
        speechSynthesis.speak(u);
        durum.etkilesimler.push(Date.now());
    };
    kok.querySelector("[data-oku]").addEventListener("click", () => oku(1));
    kok.querySelector("[data-yavas]").addEventListener("click", () => oku(0.7));
    kok.querySelector("[data-geri]").addEventListener("click", () => {
        speechSynthesis?.cancel();
        beceriListesi("hoeren");
    });

    if (dikte) {
        const alan = kok.querySelector("[data-dikte]");
        kok.querySelectorAll("[data-umlaut]").forEach((b) => b.addEventListener("click", () => {
            alan.value += b.dataset.umlaut;
            alan.focus();
        }));
    }

    kok.querySelector("[data-gonder]").addEventListener("click", async (e) => {
        e.target.disabled = true;
        speechSynthesis?.cancel();
        durum.etkilesimler.push(Date.now());
        const r = await api.post(`/skills/listening/${durum.id}/submit`, {
            sessionId: durum.sessionId,
            dictation: dikte ? kok.querySelector("[data-dikte]").value : null,
            answers: cevaplar,
            interactions: durum.etkilesimler,
        });
        sonucGoster(r, "hoeren");
    });
}

// ---------- Schreiben ----------

function ciz_schreiben(v) {
    kok.innerHTML = `
        ${ust(v, "Metnini yaz, sonra kendi değerlendirmeni yap. Site metni okumaz: puanı sen verirsin, "
            + "bu yüzden sonuç düşük güvenli bir kanıt sayılır.")}
        <article class="card card--pad">
            <p class="wordcard__q">${esc(v.taskTr)}</p>
            <p class="block__note">${esc(v.taskDe)}</p>
            <details class="reading__gloss">
                <summary>Kullanabileceğin kalıplar</summary>
                <ul>${v.phrases.map((p) => `<li>${esc(p)}</li>`).join("")}</ul>
            </details>
            <textarea class="field__input writing__area" data-metin rows="10"
                placeholder="Metnini buraya yaz…"></textarea>
            <p class="block__note"><span data-sayac>0</span> / en az ${v.minWords} kelime</p>
            <div class="btn-row umlauts">
                ${UMLAUTLAR.map((u) => `<button type="button" class="pill pill--quiet" data-umlaut="${u}">${u}</button>`).join("")}
            </div>
        </article>

        <h2 class="block__title block">Kendi değerlendirmen</h2>
        <p class="block__note">Her ölçüt için 0–${v.maxPerCriterion} puan ver.</p>
        <div class="rubric">
            ${v.criteria.map((c) => `
                <div class="rubric__row" data-olcut="${esc(c)}">
                    <span class="rubric__name">${esc(c)}</span>
                    <div class="btn-row">
                        ${Array.from({ length: v.maxPerCriterion + 1 }, (_, n) =>
                            `<button type="button" class="pill" data-puan="${n}" aria-pressed="false">${n}</button>`).join("")}
                    </div>
                </div>`).join("")}
        </div>
        <button class="btn btn--primary btn--lg block" type="button" data-gonder>Değerlendirmeyi kaydet</button>
        <div data-sonuc></div>`;

    const metin = kok.querySelector("[data-metin]");
    const sayac = kok.querySelector("[data-sayac]");
    metin.addEventListener("input", () => {
        sayac.textContent = metin.value.trim() ? metin.value.trim().split(/\s+/).length : 0;
        durum.etkilesimler.push(Date.now());
    });
    kok.querySelectorAll("[data-umlaut]").forEach((b) => b.addEventListener("click", () => {
        metin.value += b.dataset.umlaut;
        metin.focus();
    }));

    const rubrik = {};
    kok.querySelectorAll(".rubric__row").forEach((satir) => {
        satir.querySelectorAll("[data-puan]").forEach((btn) => btn.addEventListener("click", () => {
            rubrik[satir.dataset.olcut] = Number(btn.dataset.puan);
            satir.querySelectorAll("[data-puan]").forEach((x) =>
                x.setAttribute("aria-pressed", String(x === btn)));
        }));
    });

    kok.querySelector("[data-geri]").addEventListener("click", () => beceriListesi("schreiben"));
    kok.querySelector("[data-gonder]").addEventListener("click", async (e) => {
        e.target.disabled = true;
        durum.etkilesimler.push(Date.now());
        try {
            const r = await api.post(`/skills/writing/${durum.id}/submit`, {
                sessionId: durum.sessionId, text: metin.value, rubric: rubrik,
                interactions: durum.etkilesimler,
            });
            sonucGoster(r, "schreiben");
        } catch (err) {
            e.target.disabled = false;
            kok.querySelector("[data-sonuc]").innerHTML =
                `<div class="notice notice--error">${esc(err.message)}</div>`;
        }
    });
}

// ---------- Sprechen ----------

function ciz_sprechen(v) {
    kok.innerHTML = `
        ${ust(v, "Site konuşmanı dinlemez ve puanlamaz. Görevi yap, kendini kaydedip dinle, "
            + "sonra aşağıdaki ifadeleri kendin işaretle.")}
        <article class="card card--pad">
            <p class="wordcard__q">${esc(v.taskTr)}</p>
            <h3 class="block__title">Yönlendirici sorular</h3>
            <ul class="lesson__examples">${v.promptsDe.map((p) => `<li><span>${esc(p)}</span></li>`).join("")}</ul>
            <details class="reading__gloss">
                <summary>Kullanabileceğin kalıplar</summary>
                <ul>${v.phrases.map((p) => `<li>${esc(p)}</li>`).join("")}</ul>
            </details>
        </article>

        <h2 class="block__title block">Kendini değerlendir</h2>
        <p class="block__note">Bu ifadeleri bugünkü konuşmana göre işaretle.</p>
        <div class="cando">
            ${v.canDos.map((c) => `
                <div class="cando__row" data-cando="${esc(c.id)}">
                    <span class="cando__text">${esc(c.textTr)}</span>
                    <div class="btn-row">
                        <button type="button" class="pill" data-cevap="YES" aria-pressed="false">evet</button>
                        <button type="button" class="pill" data-cevap="PARTIAL" aria-pressed="false">kısmen</button>
                        <button type="button" class="pill" data-cevap="NO" aria-pressed="false">hayır</button>
                    </div>
                </div>`).join("")}
        </div>
        <button class="btn btn--primary btn--lg block" type="button" data-gonder>Değerlendirmeyi kaydet</button>
        <div data-sonuc></div>`;

    const cevaplar = {};
    kok.querySelectorAll(".cando__row").forEach((satir) => {
        satir.querySelectorAll("[data-cevap]").forEach((btn) => btn.addEventListener("click", () => {
            cevaplar[satir.dataset.cando] = btn.dataset.cevap;
            durum.etkilesimler.push(Date.now());
            satir.querySelectorAll("[data-cevap]").forEach((x) =>
                x.setAttribute("aria-pressed", String(x === btn)));
        }));
    });

    kok.querySelector("[data-geri]").addEventListener("click", () => beceriListesi("sprechen"));
    kok.querySelector("[data-gonder]").addEventListener("click", async (e) => {
        e.target.disabled = true;
        durum.etkilesimler.push(Date.now());
        try {
            const r = await api.post(`/skills/speaking/${durum.id}/submit`, {
                sessionId: durum.sessionId, canDo: cevaplar, interactions: durum.etkilesimler,
            });
            sonucGoster(r, "sprechen");
        } catch (err) {
            e.target.disabled = false;
            kok.querySelector("[data-sonuc]").innerHTML =
                `<div class="notice notice--error">${esc(err.message)}</div>`;
        }
    });
}

// ---------- Sonuç ----------

function sonucGoster(r, tur) {
    const yuzde = Math.round(r.ratio * 100);
    const kutu = kok.querySelector("[data-sonuc]");
    kutu.innerHTML = `
        <div class="result block">
            <p class="result__score">${r.score} / ${r.maxScore} <span>· %${yuzde}</span></p>
            <p class="result__note ${r.evidence ? "result__note--ok" : ""}">
                ${r.evidence ? "Seviye kanıtı olarak kaydedildi." : "Seviye kanıtı sayılmadı."}
                ${esc(r.reasonTr ?? "")}
            </p>
            ${r.dictation ? dikteHtml(r.dictation) : ""}
            ${r.sampleAnswer ? `
                <details class="reading__gloss">
                    <summary>${tur === "hoeren" ? "Metnin Türkçesi" : "Örnek cevap"}</summary>
                    <p class="sample">${esc(r.sampleAnswer).replace(/\n/g, "<br>")}</p>
                </details>` : ""}
            ${r.review?.length ? `<div class="block">${r.review.map(incelemeHtml).join("")}</div>` : ""}
            <div class="btn-row block">
                <button class="btn btn--primary" type="button" data-liste>Listeye dön</button>
            </div>
        </div>`;
    kutu.querySelector("[data-liste]").addEventListener("click", () => beceriListesi(tur));
    kutu.scrollIntoView({ behavior: "smooth", block: "start" });
}

function dikteHtml(d) {
    return `
        <p class="block__note">Kelime kelime karşılaştırma (${d.correct} / ${d.total}):</p>
        <p class="dictation">${d.words.map((w) => {
            if (w.status === "EXTRA") return `<span class="dictation__w dictation__w--extra">${esc(w.given)}</span>`;
            if (w.status === "EXACT") return `<span class="dictation__w dictation__w--ok">${esc(w.expected)}</span>`;
            if (w.status === "CLOSE") return `<span class="dictation__w dictation__w--close">${esc(w.expected)}</span>`;
            return `<span class="dictation__w dictation__w--no">${esc(w.expected)}${w.given ? ` <s>${esc(w.given)}</s>` : ""}</span>`;
        }).join(" ")}</p>`;
}

function incelemeHtml(i) {
    return `
        <div class="qrev qrev--${i.correct ? "ok" : "wrong"}">
            <p class="qrev__q">${esc(i.prompt)}</p>
            ${i.correct ? "" : `<p class="qrev__line qrev__line--no">Senin cevabın:
                <b>${i.yourAnswer ? esc(i.yourAnswer) : "boş"}</b></p>`}
            <p class="qrev__line qrev__line--ok">Doğru cevap: <b>${esc(i.correctAnswer)}</b></p>
            ${i.explanationTr ? `<p class="qrev__exp">${esc(i.explanationTr)}</p>` : ""}
        </div>`;
}
