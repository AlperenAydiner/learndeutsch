# İçerik inceleme listesi

İçerik üretimi ile incelemesi ayrı oturumlarda yapılır (SPEC 10.2). Bu dosya
incelenmesi gereken kayıtları ve inceleme bulgularını tutar. Tüm içerik
`verified: false`; `verified: true`'yu yalnız kullanıcı işaretler.

Otomatik doğrulayıcı: `backend/src/test/java/.../content/ContentValidatorTest.java`
(her build'de çalışır). Kesin kurallar testi kırar; sezgisel kontroller yalnız
raporlanır.

---

## Faz 1 (2026-09-22) — üretim oturumu notları

### Yeni üretilen: yerleştirme soruları B1, B2, C1 (36 soru) — öncelikli inceleme

`content/b1/placement.json`, `content/b2/placement.json`, `content/c1/placement.json`.
Her soruda tek doğru cevap olmasına dikkat edildi. Bilerek kaçınılan tuzaklar:
Konjunktiv I (göstergeyle de kabul edilir), "leiden an/unter" (ikisi de doğru),
"während + Genitiv" ile "trotz" karışması, "umso/desto".

Özellikle ikinci göz isteyenler:

| id | Neden |
|---|---|
| p-b1-07 | "Nachdem wir gegessen hatten, sind wir …": günlük dilde "haben" de duyulur; şıklarda "haben" yok, sorun olmamalı. |
| p-b2-02 | "eine Entscheidung treffen" standart; "fällen" da doğru ama şıklarda yok. |
| p-b2-05 | "Trotz des Regens …, obwohl wir nass wurden" — cümle biraz yapay; anlam tek cevabı zorluyor. |
| p-c1-05 | "unter Beteiligung" kalıbı; "mit Beteiligung" da kullanılır ama şıklarda yok. |
| p-c1-11, p-c1-12 | Okuma soruları; çeldiricilerin gerçekten yanlış olduğu kontrol edilmeli. |

### Dönüştürülen: 271 kelime, 46 öğrenme sorusu, 39 yerleştirme sorusu

CSV'den JSON'a birebir taşındı (`tools/csv_to_json.py`). Önceki oturumda
düzeltilen belirsiz sorular (P37, Q04, Q35) bu haliyle taşındı.

### Gramer ağacı: seviye ve `core` atamaları

`content/grammar-tree.json` — eski 22 hata kategorisi + B1–C1 için 14 yeni
düğüm. Seviye ve `core` bayrakları benim atamamdır; Goethe/CEFR müfredatıyla
karşılaştırılıp onaylanmalı. Özellikle: DATIV ve PERFEKT A1 olarak işaretli
(A1 sonunda başlar), KONJUNKTIV2 (nezaket kalıbı) A2.

### Sezgisel kontrol: örnek cümlede kelime bulunamayan 38 kayıt

Hepsi düzensiz, ayrılan ya da dönüşlü fiil; örnek cümlede çekimli biçim var
("anfangen" → "fängt … an", "können" → "kann"). Yanlış alarm olması beklenir
ama tek tek bakılmalı:

sprechen, aufstehen, anfangen, einkaufen, fernsehen, können, müssen, wollen,
dürfen, mögen, ankommen, aussteigen, treffen, bleiben, werden, nehmen,
schreiben, bringen (A1); beginnen, verlieren, gewinnen, entscheiden, steigen,
umziehen, abfahren, vorhaben, sich freuen, sich interessieren, sich treffen,
sich erinnern, sich ärgern, sich entspannen, anziehen, gefallen, einladen,
sich entschuldigen, ausfüllen, weh tun (A2).

---

## İnceleme oturumu bulguları

_Henüz inceleme oturumu yapılmadı._
