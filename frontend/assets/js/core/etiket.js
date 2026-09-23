/* =====================================================================
   Sunucudan gelen kodlarin Turkce karsiliklari, tek yerde.

   Seviye metinleri K5'e uyar: hicbir sonuc resmi CEFR/Goethe
   degerlendirmesi gibi sunulmaz; "platform ici degerlendirmeye gore"
   ve belirsizlik acikca yazilir.
   ===================================================================== */

export const BECERI = {
    LESEN: "Lesen", HOEREN: "Hören", SCHREIBEN: "Schreiben", SPRECHEN: "Sprechen",
};

export const TUR = {
    HOEREN: "Hören", LESEN: "Lesen", SCHREIBEN: "Schreiben", SPRECHEN: "Sprechen",
    GRAMER: "Gramer", KELIME: "Kelime", ARTIKEL: "Artikel", SEVIYE_TESTI: "Seviye testi",
};

/** "Bugun ne yaptin?" formunda secilebilen turler (SPEC 6.2). */
export const SECILEBILIR_TURLER = ["HOEREN", "LESEN", "SCHREIBEN", "SPRECHEN", "GRAMER", "KELIME"];

export const KAYNAK = {
    YOUTUBE: "YouTube", BOOK: "Kitap", PODCAST: "Podcast", COURSE: "Kurs",
    TEACHER: "Öğretmen", AI: "Yapay zekâ", OTHER_APP: "Başka uygulama", SELF: "Kendi çalışmam",
};

export const SONUC_KAYNAGI = {
    OFFICIAL_EXAM: "Resmi sınav",
    MODELLTEST: "Modelltest",
    TEACHER: "Öğretmen değerlendirmesi",
    APP_TEST: "Uygulama / kurs testi",
    SELF_ASSESSMENT: "Kendi değerlendirmem",
    SITE_TEST: "Site içi test",
};

export const ORTAK = {
    TEACHER: "Öğretmen", FRIEND: "Arkadaş", AI: "Yapay zekâ", ALONE: "Kendi başıma",
};

export const AMAC = {
    EXAM: "Sınav", UNIVERSITY: "Üniversite", WORK: "İş", DAILY_LIFE: "Günlük hayat",
    ABROAD: "Yurtdışı", PERSONAL: "Kişisel gelişim", OTHER: "Diğer",
};

export const KADEME = { HIGH: "yüksek", MEDIUM: "orta", LOW: "düşük" };

export const SEVIYELER = ["A1", "A2", "B1", "B2", "C1"];

/** A0 bir seviye iddiasi degildir; yerlestirmede "A1'in altinda" demektir. */
export function seviyeAdi(level) {
    return level === "A0" ? "A1'in altında" : level;
}

/**
 * Beceri tahmini metni (SPEC 4.3-4.4, K5).
 * @param s LevelController.SkillView
 */
export function beceriMetni(s) {
    switch (s.kind) {
        case "NONE": return "Veri yok";
        case "BELOW": return `${s.level}'in altında (alt seviyelerde veri yok)`;
        case "AT_LEAST": return `En az ${s.level}`;
        default: return `${s.level} civarı`;
    }
}

export function guvenMetni(confidence) {
    return { NO_DATA: "veri yok", LOW: "düşük güven", RELIABLE: "güvenilir" }[confidence] ?? confidence;
}

/** Genel tahmin (SPEC 4.5). @param o OverallEstimate */
export function genelMetni(o) {
    if (!o || !o.sufficient) {
        return "Henüz yeterli veri yok";
    }
    const aralik = o.rangeMin === o.rangeMax ? "" : ` (beceriler ${seviyeAdi(o.rangeMin)}–${seviyeAdi(o.rangeMax)} arası; en zayıf: ${BECERI[o.weakest]})`;
    return `Platform içi değerlendirmeye göre ${seviyeAdi(o.level)} civarında${aralik}`;
}

export function eksikBecerilerMetni(o) {
    if (!o || !o.missing || o.missing.length === 0) {
        return "";
    }
    const adlar = o.missing.map((s) => BECERI[s]);
    const liste = adlar.length === 1 ? adlar[0]
        : adlar.slice(0, -1).join(", ") + " ve " + adlar[adlar.length - 1];
    return `${liste} için veri olmadığından tahmin eksik.`;
}

export function esc(s) {
    const d = document.createElement("div");
    d.textContent = s ?? "";
    return d.innerHTML;
}
