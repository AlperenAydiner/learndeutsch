# İlerleme

## Durum

| Faz | Durum |
|---|---|
| Faz 0 — Analiz | ✅ Bitti (2026-09-22). `docs/FAZ0-RAPOR.md`, kararlar K-001…K-009 |
| Faz 1 — Veri modeli, config, onboarding, yerleştirme, seviye modeli, aktivite kaydı | ✅ Bitti (2026-09-22). Kararlar K-010…K-015 |
| Faz 2 — Koç motoru, plan, Ana Sayfa, Koçluk | ✅ Bitti (2026-09-22). Kararlar K-016…K-018 |
| Faz 3a — Kelimeler/SRS, Artikel, cevap kontrolü | ✅ Bitti (2026-09-22). Kararlar K-019…K-022 |
| Faz 3b — Gramer, hata hafızası | ✅ Bitti (2026-09-23). Kararlar K-023…K-025 |
| Faz 3c — Lesen, Hören, Schreiben, Sprechen | ✅ Bitti ve doğrulandı (2026-09-23). Kararlar K-026…K-028 |
| Faz 4 — Sınav, hedef tamamlama ekranı, demo modu, cila, kabul testleri | 🟡 Büyük kısmı bitti (2026-09-23): Sınav, demo modu, A1–C1 içerik, uyanık tutma, **6 kabul senaryosunun hepsi geçiyor**. Kalan: yayın hazırlığı. Kararlar K-029…K-030 |

**Yayında olan:** yok — eski canlı veritabanı silindi, site kapalı (K-009). Her şey yerelde, commit edilmedi.

## Faz 1 özeti

### Backend
- **Config (K7):** Ek A'nın tamamı `application.yml`'de, tipli `@ConfigurationProperties` sınıflarında (`config/`). Ek A'dan sapma: yerleştirme blok 6 / en fazla 24 (K-003).
- **İçerik (K-004):** `content/{a1,a2,b1,b2,c1}/*.json` + `grammar-tree.json`. 271 kelime, 46 öğrenme sorusu, 75 yerleştirme sorusu (A1 22, A2 17, B1–C1 12'şer; B1–C1 yeni üretildi), 39 düğümlü gramer ağacı. Hepsi `verified:false`. `ContentCatalog` açılışta belleğe yükler; veritabanına yazılmaz. CSV'ler ve tohumlayıcı kaldırıldı.
- **Şema:** V4 eski alan tablolarını kaldırır, 8 kullanıcı durumu tablosu kurar. Migrasyondan önce otomatik yedek (`MigrationBackup`; dev'de `backup_20260922_152930` alındı).
- **Seviye modeli (saf):** `SkillEstimator` (4.3, 4.4), `OverallEstimate` (4.5), `WorkingLevel` (4.6), `Reassessment` (4.7), `GoalCompletion` (4.8), `TierPolicy`. Her tahmin dayandığı kanıtları döner (K3).
- **Yerleştirme (saf):** `PlacementEngine` (adaptif, 4.1) + `QuestionPicker` (tekrar çözümde görülmemiş sorular önce). Sonuç düşük güvenli kaba başlangıç; beceri kanıtı üretmez.
- **Aktivite kaydı:** `ActivityRules` (6.3: ne zaman kanıt olur; 6.1: 5 dk boşluk kuralıyla aktif süre). Site içi çalışma otomatik kaydedilir (ilk örnek: yerleştirme testi), dış çalışma "Bugün ne yaptın?" ile; düzenlenince kanıt yeniden hesaplanır, silinince kanıt da silinir.
- **API:** `/api/onboarding` (+ `/start`, `/complete`, `/goal`, `/rhythm`), `/api/placement/start`, `/api/placement/{id}/answers`, `/api/level`, `/api/activities` (+ `/options`), `/api/me/data/export|import|reset`.

### Ön yüz
- `app/baslangic.html`: 5 adımlı onboarding (sıfırdan başlayana test adımı yok; test sonrası yalnız tahminin üstündeki hedefler).
- `app/yerlestirme.html`: 6'lık bloklar, "Bilmiyorum" seçeneği (K-012), sonuçta soru soru inceleme ve hedef bildirimi (K6).
- `app/index.html` (Ana Sayfa): hedef ve yol, beceri durumu ("Veri yok" / "En az A2" / güven), "Bugün ne yaptın?" tek dokunuşlu formu, son 7 gün (düzenle/sil).
- `app/ayarlar.html`: hesap, hedef bağlantısı, dışa/içe aktarma, sıfırlama.
- Eski sayfalar (bugün, program, kelimeler, quiz, testler, gelişim) ve CSS'leri kaldırıldı.

### Testler
- **62 birim testi** (config bağlama 3, içerik doğrulayıcı 6, seviye modeli 32, yerleştirme 13, aktivite kuralları 8). Veritabanı gerektirmez.
- **1 uçtan uca entegrasyon testi** (`ApiFlowIT`, dev veritabanı, K-014): onboarding → yerleştirme → hedef → kanıt → düzenle/sil → dışa aktar/sıfırla/içe aktar. Geçiyor.
- Arayüz: sahte API'li kopyada dört sayfa 1280px ve 375px'te denendi; taşma ve konsol hatası yok.

## Faz 2 özeti

### Backend
- **Koç motoru (saf):** `Coach` arayüzü (5.6: ileride AI ile değiştirilebilir) + `RuleBasedCoach`. SPEC 5.3'ün sekiz kuralı, başına `ASSESSMENT` (K-016), yeni kullanıcı için başlangıç planı. En fazla 3 öneri, kategori başına 1; sıra config'den. Her öneri `reason` (insan diliyle neden) ve `basis` (dayandığı sayılar) taşır (K3).
- **A0 / içerik olmayan durum:** kuralın önerdiği çalışma sitede yoksa öneri "dışarıda yap, sonucu gir" biçiminde çıkar (SPEC 10.1). Zayıf konu ve tekrarlayan hata kuralları A0'da kapalı.
- **Plan (saf):** `WeeklyPlanner.weekly()` seviye bazlı paylarla haftalık dağıtım (aktif önerinin türü pay kazanır), `daily()` önce önerileri, kalan süreyi haftalık hedefte en geri kalmış türe verir. Geri dönüş günü kısaltılır. Plan öneridir, zorunluluk değil (5.4).
- **Veri:** `CoachService` 60 günlük geçmişi okur, alışkanlık özetini (son 7 gün / son 30 gün) üretir. SRS, hata hafızası ve artikel girdileri Faz 3'te bağlanacak; şimdilik boş — uydurma sayı yok.
- **API:** `GET /api/coach` (öneriler + haftalık + günlük plan + alışkanlık). Hedef/ritim yoksa 404.

### Ön yüz
- **Menü:** Ana Sayfa · Koçluk (Öğren Faz 3'te).
- `app/kocluk.html` (yeni): hedef ve yol, başlangıç noktası, "Ne kadar Almanca biliyorum?" (beceri kartları, güven, "Neye dayanıyor?" açılır kanıt listesi, hedefe göre durum) ile "Ne kadar çalıştım?" (7 günlük tür bazlı çubuklar, son 30 gün toplamları) görsel olarak ayrı; "Sistem ne öneriyor?"; bu haftanın öncelikleri; hedef ve ritim düzenleme.
- `app/index.html`: "Koçun önerisi" ve "Bugünkü önerilen plan" bölümleri eklendi; kayıt/silme sonrası öneriler tazeleniyor.
- `assets/js/components/kocKartlari.js` (yeni): öneri/plan/hafta gösterimi iki sayfada ortak.
- **Ziyaretçi sayfaları** koç modeline göre yeniden yazıldı; sahte ürün görseli kaldırıldı, "Şu an ne hazır, ne yolda" bölümü eklendi (K-018).

### Testler
- **87 test, 0 hata** (Faz 2'de eklenen `CoachTest`: her kural, 3/kategori sınırları, yeni kullanıcı başlangıç planı, haftalık/günlük plan hesabı = 24 test).
- `ApiFlowIT` genişletildi: hedef yokken `/api/coach` 404; hedef sonrası öneri sınırları, gerekçe zorunluluğu, günlük sürenin aşılmaması, haftalık toplam, 7 günlük alışkanlık. Dev veritabanında geçiyor.
- Arayüz: sahte API'li kopyada Ana Sayfa, Koçluk ve iki ziyaretçi sayfası 1280px ve 375px'te denendi; yatay taşma ve konsol hatası yok.

## Faz 3a özeti

### Backend
- **Cevap kontrolü (saf):** `AnswerCheck` (SPEC 8.3) — fazla boşluk ve noktalama yok sayılır; büyük/küçük harf ve ae/oe/ue/ss yazımı varsayılan olarak kabul edilir ama uyarı üretir (config'den katı mod). Türkçe yerelde bozulmaması için her küçültme `Locale.ROOT`.
- **SRS merdiveni (saf):** `SrsLadder` + `Grade` (Bilemedim/Zorlandım/Bildim) — 1→3→7→14→30→60→120 (config). Bildim bir üst basamak, Zorlandım aynı, Bilemedim başa. "Güçlü kelime" eşiği ve günlük yeni kelime sayısı config'den.
- **Günün oturumu (saf):** `VocabularyPlanner` — önce en gecikmiş tekrarlar, günlük sınır kadar; kalanı sonraki günlere. Sınır dolduysa yeni kelime yok (K-019).
- **Artikel (saf):** `ArticleDrill` — önce yanlış yapılanlar, sonra hiç sorulmamışlar, en sonda bilinenler.
- **Şema V5:** `vocabulary_progress` (merdiven durumu) ve `learning_answer` (kelime/artikel/gramer yanıtları, "son N yanıt" pencereleri). İkisi de RLS açık. Dışa aktarma/içe aktarma/sıfırlama listesine eklendi.
- **Servisler ve API:** `/api/words/session`, `/api/words/answer`, `/api/words/session/{id}/finish`, `/api/words/stats`; `/api/article/session`, `/api/article/answer`, `/api/article/session/{id}/finish`, `/api/article/stats`. Oturum bitince çalışma otomatik kaydedilir (SPEC 6.1); öğrenme testi seviye kanıtı üretmez (K2, 8.5).
- **Koça bağlandı:** `dueReviews`, `dueReviewMinutes`, `articleWindows` ve `siteModules` artık gerçek veriden geliyor; kelime ve artikel önerileri "dışarıda yap" yerine site içi eyleme dönüyor.

### Ön yüz
- **Menü:** Ana Sayfa · Öğren · Koçluk (SPEC 7 tamamlandı).
- `app/ogren.html` (yeni): modül kartları (kelime, artikel, "yolda" olanlar), kelime oturumu (yeni kelime tanıtımı; tekrar kartında artikel sorusu → cevabı göster → üç not) ve artikel pratiği. Cevaptan sonra doğru cevap ekranda kalır, "Devam" ile ilerlenir (K-022).
- `app/kocluk.html`: "Öğrenme aktivitelerin" bölümü gerçek kelime/artikel istatistiğini gösteriyor; gramer için not duruyor.

### Testler
- **113 test, 0 hata** (Faz 3a'da eklenen 25: cevap kontrolü 9, SRS + gün planı 12, artikel seçimi 4).
- `ApiFlowIT` genişletildi: kelime oturumu → merdivene giriş → artikel yanlışında "Zorlandım" → istatistik → oturum bitişi → artikel pratiği → öğrenme testinin seviye kanıtı üretmediği. Dev veritabanında geçiyor (V5 orada uygulandı).
- Arayüz: gerçek API yanıtlarıyla (K-020) Öğren, Koçluk ve Ana Sayfa 1280px ve 375px'te denendi; taşma ve konsol hatası yok.

## Faz 3b özeti

### İçerik
- **22 gramer dersi** (A1 15, A2 7): kısa açıklama, Türkçeyle karşılaştırma notu (Akkusativ ≈ -i hâli, fiilin ikinci sırada olması, Türkçede cinsiyet olmaması…), en az iki örnek cümle, iki kontrollü üretim alıştırması. Hepsi `verified:false` (K-024).
- Mini sorular yeniden yazılmadı: konuya etiketli mevcut 46 öğrenme sorusu kullanılıyor.
- **İçerik doğrulayıcıya iki yeni kural:** ders gramer ağacındaki bir konuya bağlı ve aynı seviyede mi, örnek/üretim alanları dolu mu, boşluk doldurmada `___` var mı, kelime sıralamada parçalar cevapta geçiyor mu; ders içeriği olan her konunun mini sorusu var mı.

### Backend
- **Hata hafızası (saf):** `ErrorMemory` — aktif → düzeliyor → çözüldü; art arda doğru sayacı, farklı oturum sayacı, çözülen etiketin yeniden aktifleşmesi. Eşikler config'den (Ek A).
- **Kontrol tekrarları (saf):** `GrammarCheck` — tamamlanan konu 7 ve 30 gün sonra "tekrar bekleyen" listesine düşer.
- **Şema V6:** `grammar_progress` (konu sayaçları, tamamlanma, sıradaki kontrol) ve `error_record` (etiket bazlı durum). Dışa aktarma/içe aktarma/sıfırlama listesine eklendi.
- **API:** `/api/grammar/topics`, `/api/grammar/topics/{id}/session`, `/api/grammar/answer`, `/api/grammar/session/{id}/finish` (`completed` ile konu tamamlama), `/api/grammar/stats`, `/api/grammar/errors`.
- **Cevap kontrolü bağlandı:** yazmalı alıştırmalarda `AnswerCheck`; büyük/küçük harf ve umlaut yazımı kabul edilir ama ayrı hata etiketi olarak kaydedilir (K-025).
- **Koça bağlandı:** `grammarWindows`, `recurringErrors` ve `dueGrammarChecks` gerçek veriden geliyor. WEAK_GRAMMAR ve RECURRING_ERROR önerileri artık doğrudan ilgili konunun sayfasına bağlanıyor (`/app/ogren.html#gramer:KONU`).
- **Düzeltme:** A0 kullanıcısına hiç içerik gelmiyordu; artık A1 içeriğiyle çalışıyor (K-023).

### Ön yüz
- `app/ogren.html`: **Gramer** modülü eklendi — konu listesi (seviye grupları, ilerleme, "kontrol zamanı" rozeti, içeriği olmayan konularda "içerik hazır değil") ve konu döngüsü: açıklama + Türkçe notu + örnekler → mini sorular → kontrollü üretim (boşluk doldurma için ä ö ü ß butonları, kelime sıralama için parça seçme ve geri alma) → her cevapta doğru cevap ve açıklama, "Devam" ile ilerleme, son adımda "Konuyu tamamla".
- `app/kocluk.html`: öğrenme aktiviteleri tablosuna gramer sütunu, ayrıca **hata hafızan** bölümü (etiket, durum rozeti, kaç hata / kaç oturum, tekrarlayan işareti).

### Testler
- **126 test, 0 hata** (Faz 3b'de eklenen 10: hata hafızası durum geçişleri 8, kontrol tekrarı 2; içerik doğrulayıcıya 2 yeni kural).
- `ApiFlowIT`'e ikinci uçtan uca senaryo: sıfırdan başlayan kullanıcı → konu listesi → ders döngüsü → yanlış cevabın hata hafızasına düşmesi → yazım uyarısının ayrı etiket olması → konu tamamlama ve 7 günlük kontrolün planlanması → gramer çalışmasının otomatik kaydı ve seviye kanıtı üretmemesi. İki test de dev veritabanında geçiyor (V6 orada uygulandı).
- Arayüz: gerçek API yanıtlarıyla (K-020) Öğren (konu listesi, ders döngüsü, üç alıştırma türü) ve Koçluk 1280px ve 375px'te denendi; taşma ve konsol hatası yok.

## Faz 3c özeti

### İçerik (A1 ve A2, hepsi `verified:false`)
- **Okuma:** 2'şer metin, 5'er soru, kelime yardımı listesi.
- **Dinleme:** 2'şer dikte + 1'er anlama alıştırması (metin + Türkçesi).
- **Yazma:** 2'şer görev; Türkçe yönerge, Almanca yönerge, hazır kalıplar, örnek cevap, asgari kelime sayısı.
- **Konuşma:** 2'şer görev; yönlendirici sorular ve kalıplar.
- **Kann-Beschreibungen:** seviye başına 11 ifade (dört beceri). Resmî CEFR metinleri kopyalanmadı, kendi ifademizle yazıldı (SPEC 10.2).
- Doğrulayıcıya üç yeni kural grubu: okuma/dinleme sorularının tek doğru cevabı, yazma görevinin örnek cevabının asgari uzunluğu karşılaması, konuşma görevi olan her seviyede Kann-Beschreibung bulunması.

### Backend
- **Saf mantık:** `DictationCheck` (kelime bazlı karşılaştırma, puan = doğru kelime oranı; umlaut yazımı "yaklaşık" sayılır), `WritingRubric` (4 ölçüt × 3 puan → oran, ölçüt adları config'den — K-026), `CanDoScore` (evet/kısmen/hayır → oran).
- **`SkillPracticeService` + `/api/skills/*`** (K-027): liste, oturum, gönderim. Kanıt kuralları SPEC 4.2'den:
  - Lesen otomatik puanlanır → **kanıt**,
  - Schreiben rubrikle öz değerlendirme → **kanıt**,
  - Sprechen Kann-Beschreibung öz değerlendirmesi → **kanıt**,
  - Hören site içi (sentetik ses) → **kanıt değil**, yalnız çalışma geçmişi.
  Yanıt `evidence` ve `reasonTr` alanlarıyla bunu kullanıcıya da söyler (K3).
- İçerik `verified:false` olduğu sürece kanıt bir kademe düşük sayılır (K-008); Lesen kanıtı bu yüzden şimdilik "düşük" kademede.
- Koçun `siteModules` listesi dört beceriyi de içeriyor: öneriler artık "dışarıda yap" yerine site içi çalışmaya bağlanıyor.

### Ön yüz
- `assets/js/components/beceriler.js` (yeni): dört modülün liste ve çalışma ekranları.
  - **Lesen:** metin + kelime yardımı + sorular → gönder → puan ve soru soru inceleme.
  - **Hören:** Web Speech (de-DE) ile dinle / yavaş dinle; cihazda Almanca ses yoksa bu açıkça yazılır (K4). Dikte alanında ä ö ü ß butonları; sonuçta kelime kelime renkli karşılaştırma.
  - **Schreiben:** görev, kalıplar, kelime sayacı, rubrik ile öz değerlendirme; gönderimden sonra örnek cevap.
  - **Sprechen:** görev, yönlendirici sorular, kalıplar; "şunu yapabiliyorum" ifadeleri evet/kısmen/hayır.
- `app/ogren.html`: dört yeni modül kartı; koç bağlantıları `#lesen`, `#hoeren`, `#schreiben`, `#sprechen` ile doğrudan modüle giriyor. "Yolda" kartında artık yalnız Sınav bölümü var.

### Testler
- **141 birim testi, 0 hata** (Faz 3c'de eklenen 15: dikte 6, rubrik 3, can-do 2, içerik doğrulayıcı 3 + mevcut kurallara eklemeler).
- `ApiFlowIT`'e üçüncü uçtan uca senaryo yazıldı (Lesen kanıtı ve kademesi, Hören'in kanıt üretmemesi, kısa metnin reddi, rubrik ve Kann-Beschreibung ile kanıt, dört çalışmanın geçmişe yazılması). **Henüz çalıştırılamadı:** geliştirme veritabanına TLS el sıkışması zaman aşımına uğruyor (TCP açılıyor, Postgres `SSLRequest`'e `S` diyor, TLS takılıyor). Ağ bağlantısı düzelince `IT_DB=dev ./gradlew test --tests '*ApiFlowIT'` ile koşulacak.
- Arayüz doğrulaması da aynı nedenle bekliyor: gerçek API yanıtları dökülemedi (K-020). Modüllerin yüklendiği ve konsolun temiz olduğu kontrol edildi.

## Faz 4 — şu ana kadar

- **Sınav bölümü:** `content/exam-links.json` (Goethe, telc, ÖSD, BAMF — yalnız kurum kök adresleri, `verified:false`), `ExamService` + `GET /api/exam` (hedef seviyedeki dört beceri pratiği, beceri başına hedef durumu, kurum bağlantıları), `app/sinav.html` ve Öğren'de "🎯 Sınav" kartı. Hedefe ulaşıldığında K6 bildirimi: sistem seviye yükseltmez, kullanıcı seçer.
- **İçerik doğrulayıcı:** dış bağlantı kuralı eklendi — `https://`, derin link yok (kurum kökü), `verified:true` yasak.
- **B1 gramer:** 8 konunun tamamına ders içeriği (açıklama, Türkçe karşılaştırma, örnekler, iki kontrollü üretim) ve 16 yeni mini soru. Artık A1–B1 gramer modülü tam.
- **B1 beceri içeriği:** 2 okuma metni (5–6 soru, sözlük), 2 dikte + 1 dinleme-anlama, 2 yazma görevi (görüş yazısı, resmî şikâyet; örnek cevaplı), 2 konuşma görevi, 12 Kann-Beschreibung. Dört beceri modülü A1–B1 kapsıyor.
- **B1 kelime:** 86 kelime (49 isim artikel+çoğuluyla, 25 fiil, 12 sıfat); iş/başvuru, sağlık, eğitim, çevre, medya, toplum, duygular, günlük hayat temaları. Toplam kelime 357.
- **B2 ve C1 gramer:** B2'nin 5 konusu (Partizip sıfatları, isim-fiil kalıpları, iki parçalı bağlaçlar, Genitiv edatları, als ob) ve C1'in isimleştirme konusu; 12 yeni mini soru. **Gramer ağacındaki 36 konunun tamamının ders içeriği hazır.**
- **B2 beceri içeriği:** 2 okuma (dört günlük hafta tartışması, konut sorunu — yazarın tutumunu ve bilgi/yorum ayrımını soran sorularla), 2 dikte + 1 panel dinlemesi, 2 yazma görevi (argümantasyon, resmî itiraz; örnek cevaplı), 2 konuşma görevi (tez–karşı tez, veri yorumlama), 12 Kann-Beschreibung.
- **B2 kelime:** 63 kelime (argümantasyon ve resmî dil ağırlıklı: Auswirkung, Einwand, nachvollziehbar, hinterfragen…). Toplam kelime 420.
- **C1 beceri içeriği:** 1 analitik okuma metni (kaynak eleştirisi, örtük tutum soruları), 1 dikte + 1 konferans dinlemesi, 1 yazma görevi (özet + eleştirel değerlendirme), 2 konuşma görevi (sunum; karşı argümanı adil aktarma), 11 Kann-Beschreibung.
- **C1 kelime:** 40 kelime (akademik ve tartışma dili: Evidenz, Stichprobe, entkräften, in Kauf nehmen…). **Toplam 460 kelime; içerik artık A1–C1 boyunca her modülde var.**
- **Demo modu (K-029):** V7 ile `app_user.is_demo`, `Today` içinde `X-Demo-Date` desteği (yalnız demo hesabında), `/api/me` yanıtında `demo` bayrağı, arayüzde DEMO şeridi + simüle tarih seçici. 6 yeni birim testi: gerçek hesapta başlık yok sayılıyor, demo hesabında zaman ileri sarılıyor, bozuk tarih reddediliyor.
- **Yerel veritabanı (K-030):** Supabase'e TLS geçmediği için geliştirme Docker'daki Postgres'e taşındı. Docker Desktop'ın açılmasını engelleyen iki takılı soket dosyası kenara alındı (CLAUDE.md'de not var).
- **Doğrulama tamamlandı — 154 test, 0 hata:** `ApiFlowIT`'in üç senaryosu (Faz 3c'ninki ilk kez koştu) ve **SPEC 12.3'ün altı kabul senaryosunun tamamı** (`AcceptanceIT`, demo modunda, zamanı ileri sararak).
- **Doğrulamanın yakaladığı hatalar (düzeltildi):**
  - Gramer oturumunu bitirme ucu, gövdede `completed` yoksa **500** veriyordu (ilkel `boolean`). Alan kutulu tipe çevrildi; ayrıca okunamayan istek gövdeleri artık genel olarak 400 dönüyor.
  - **Sınav sayfası, çalışma seviyesinin üstündeki hedefe hiç pratik göstermiyordu** (A2 kullanıcı, B1 hedef → "hazır pratik yok"). Pratik artık hedef seviyeden geliyor, "Çalış" düğmesi doğrudan o görevi açıyor; regresyon testi eklendi.
- **Arayüz:** Öğren'in dört beceri modülü (okuma + inceleme, dikte + kelime karşılaştırması, rubrik, can-do) ve Sınav sayfası gerçek API yanıtlarıyla 1280px ve 375px'te denendi; taşma ve konsol hatası yok. Hören, tarayıcıda Almanca ses olmadığında bunu açıkça söylüyor (K4).
- **Uyanık tutma:** `.github/workflows/ping.yml` — 14 dakikada bir `/actuator/health`; hem Render uykusunu hem Supabase duraklatmasını önler. Adres `API_BASE_URL` depo değişkeninden; tanımsızsa atlanır.
- **Artık bekleyen:** yalnız yayın hazırlığı (açık soru 2: canlı veritabanı; açık soru 3: içerik incelemesi).
- ~~Bekleyen: veritabanı bağlantısı olmadığı için `ApiFlowIT` ve arayüz doğrulaması yapılamadı.~~ Yapıldı (yukarıda).

## Açık sorular

1. **Render uyku / Supabase duraklatma** (K-001): Faz 4'te 14 dk'da bir ücretsiz ping kurulsun mu?
2. **Publish günü canlı veritabanı** (K-009): dev projesi mi canlıya dönüşecek, yeni proje mi açılacak?
3. **İçerik incelemesi:** 36 yeni B1–C1 yerleştirme sorusu ve gramer ağacı seviyeleri ayrı bir inceleme oturumu bekliyor (`docs/content-review.md`).

## Faz 3c — alt görev listesi (tamamlandı, doğrulama bekliyor)

1. **Lesen:** seviye bazlı okuma metinleri + otomatik puanlanan sorular; sonuç beceri kanıtı olur (SPEC 4.2, site içi test kademesi).
2. **Hören:** Web Speech API (de-DE) ile dinleme/dikte; cihazda Almanca ses yoksa bunu açıkça söyle. Öğrenme aktivitesidir, kanıt değildir.
3. **Schreiben:** yazma görevleri, rubrikle öz değerlendirme (4 ölçüt × 3 puan, config), örnek cevap.
4. **Sprechen:** konuşma görevleri ve Kann-Beschreibung öz değerlendirmesi; gerçek konuşma değerlendirmesi yapılıyormuş gibi davranılmaz (K4).
5. **Dikte cevap kontrolü:** kelime bazlı karşılaştırma, puan = doğru kelime oranı (SPEC 8.3).
6. **Koça bağlama:** dört becerinin site içi kanıtı; MISSING_EVIDENCE ve IDLE_SKILL önerileri site içi eyleme döner.
7. **Kapanış:** birim testleri, `ApiFlowIT` genişletme, arayüz doğrulaması, DECISIONS/PROGRESS.

## Faz 4 ön koşulları

- Geliştirme veritabanı bağlantısının geri gelmesi; `ApiFlowIT` ve arayüz doğrulamasının tamamlanması.
- Faz 3c'nin kullanıcı tarafından onaylanması.
- Faz 4 alt görev listesinin onayı.

## Faz 4 — alt görev taslağı (onaya sunulacak)

1. **Sınav bölümü:** hedef seviye öne çıkar; dört beceri formatında pratik, resmî modelltest bağlantıları (telifli materyal kopyalanmaz) ve sonuç girişi.
2. **Hedef tamamlama ekranı:** hedefe ulaşınca ne olduğu, hedefi değiştirme (K6).
3. **Demo modu:** ayrı demo kullanıcısı, simüle tarih, ekranda "DEMO" etiketi.
4. **B1–C1 içeriği:** gramer dersleri ve beceri içeriğinin üst seviyelere genişletilmesi.
5. **Render uyku / Supabase duraklatma:** 14 dakikada bir ücretsiz ping (açık soru 1).
6. **Cila ve kabul senaryoları:** SPEC 12.3'teki senaryoların uçtan uca denenmesi.
7. **Yayın hazırlığı:** canlı veritabanı kararı (açık soru 2), içerik incelemesi (açık soru 3).

## Faz 2 — alt görev listesi (tamamlandı)

1. **Koç motoru (saf):** SPEC 5.3'teki 8 kural + A0/yeni kullanıcı başlangıç planı; en fazla 3 öneri, kategori başına 1; her öneride "neden" (kural + veri); içerik yoksa "dışarıda yap + sonucu gir". Sıra config'den. Henüz olmayan modüllere (SRS, hata hafızası) dayanan kurallar veri modeline göre yazılıp test verisiyle doğrulanır.
2. **Plan (saf):** haftalık pay dağılımı (seviye bazlı paylar, config), günlük plan (`estimatedMinutes` ile günlük süreye sığdırma).
3. **Koç arayüzü:** AI ile değiştirilebilecek bir arayüz (`Coach`), kural tabanlı gerçekleştirim.
4. **API:** `/api/coach` (öneriler + haftalık/günlük plan).
5. **Ana Sayfa:** koçun bugünkü önerileri (en fazla 3) ve bugünkü önerilen plan eklenir.
6. **Koçluk sayfası:** hedef ve yol; başlangıç noktası (yerleştirme, tarihli); "Ne kadar Almanca biliyorum?" (beceriler, güven, kanıt sayısı, genel tahmin) ile "Ne kadar çalıştım?" (öğrenme aktiviteleri) görsel olarak ayrı; bu haftanın öncelikleri; son 7 gün tür bazlı alışkanlık; "Sistem ne öneriyor?"; seviye testi, hedef ve ritim değiştirme buraya taşınır.
7. **Menü:** Ana Sayfa · Koçluk (Öğren Faz 3'te).
8. **Ziyaretçi sayfaları:** ana sayfanın geri kalanı ve "Nasıl çalışır" koç modeline göre yeniden yazılır (K-013).
9. **Kapanış:** birim testleri (kurallar, plan), entegrasyon testi genişletilir, DECISIONS/PROGRESS.

## Faz 3a — alt görev listesi (tamamlandı)

1. **Cevap kontrolü (saf):** büyük/küçük harf ve umlaut yazımı (`answer.*` config), kabul edilen alternatif cevaplar, "yaklaşık doğru" uyarısı.
2. **SRS merdiveni (saf):** 1→3→7→14→30→60→120; Bilemedim/Zorlandım/Bildim; günlük yeni kelime sayısı günlük süreye göre (`srs.daily-new-by-minutes`), tekrar sınırı; "güçlü kelime" eşiği.
3. **Kelime deposu ve API:** `vocabulary_progress` tablosu, `/api/words/session` (bugünün tekrarı + yeni), `/api/words/answer`.
4. **Artikel çalışması:** der/die/das ayrı beceri; renk kodu; yanlışta doğruyu gösterme; `/api/article/*`.
5. **Öğren sayfası:** menüye "Öğren" eklenir; kelime oturumu ve artikel alıştırması ekranları.
6. **Koça bağlama:** `dueReviews`, `articleWindows`, `siteModules` gerçek veriyle dolar; DUE_REVIEWS ve WEAK_ARTICLE kuralları site içi eyleme döner.
7. **Aktivite kaydı:** kelime/artikel oturumları otomatik `ActivityLog` üretir (site içi, aktif süre kuralıyla).
8. **Kapanış:** birim testleri, `ApiFlowIT` genişletme, arayüz doğrulaması, DECISIONS/PROGRESS.

## Faz 3b — alt görev listesi (tamamlandı)

1. **Gramer içeriği:** her konu için kısa açıklama, örnek, mini sorular ve kontrollü üretim alıştırmaları (JSON, `verified:false`); Türkçe karşılaştırma notları (Akkusativ ≈ -i hâli vb.).
2. **Gramer döngüsü:** açıklama → örnek → mini soru → hata açıklaması → yeni örnek → kontrollü üretim → mini test.
3. **Hata hafızası:** etiketli hata kaydı, aktif → düzeliyor → çözüldü geçişleri (config eşikleri), tekrarlayan hata tespiti.
4. **Gramer kontrol tekrarları:** tamamlanan konu config aralıklarıyla (7, 30 gün) "tekrar bekleyen" listesine düşer.
5. **Koça bağlama:** `grammarWindows`, `recurringErrors`, `dueGrammarChecks` gerçek veriyle dolar; WEAK_GRAMMAR ve RECURRING_ERROR site içi eyleme döner.
6. **Öğren sayfası:** gramer konu ağacı ve konu ekranı; Koçluk'a gramer istatistiği.
7. **Kapanış:** birim testleri, `ApiFlowIT` genişletme, arayüz doğrulaması, DECISIONS/PROGRESS.

