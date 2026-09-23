# Kararlar

Her karar: bağlam, karar, gerekçe, bedel. En yenisi en altta.

---

## K-001 — Kalıcılık: B, hesaplı yapı korunur (Faz 0, 2026-09-22)

**Bağlam:** SPEC 9.2 iki seçenek sunuyor: A hesapsız (cihazda), B hesaplı (backend + veritabanı + giriş).

**Karar:** B. Spring Boot + Supabase Postgres + Supabase Auth + giriş korunur.

**Gerekçe:** Kullanıcı seçimi — cihazlar arası erişim (telefon + bilgisayar) ve mevcut giriş sistemi.

**Bedel ve önlemler:**
- Render ücretsiz katmanı 15 dk hareketsizlikte uyur; ilk açılış 30–50 sn. Uyanma bandı kalır. Faz 4'te seçenek: 14 dk'da bir ücretsiz ping (ayda ~720 saat, 750 saatlik sınırın içinde).
- Supabase ücretsiz projesi 7 gün kullanılmazsa duraklatılabilir; aynı ping bunu da önler.
- Alan mantığı Java'da, arayüz JS'te: iki dil.

Not: Faz 0'da önerim A idi (SPEC'in istemci yönlü yapısı, uyku süresi, duraklatma riski). Kullanıcı B'yi seçti.

---

## K-002 — Eski kullanıcı verisi taşınmaz (Faz 0, 2026-09-22)

**Karar:** Yeni sistem sıfırdan başlar. Eski veri migrasyon öncesi otomatik yedekle (`backup_*` şeması) veritabanında korunur ama yeni modele çevrilmez.

**Gerekçe:** Kullanıcı seçimi. Eski veri (SM-2 durumu, görev işaretlemeleri, gramer tabanlı "beceri tahmini") yeni modelle kavramsal olarak örtüşmüyor.

---

## K-003 — Yerleştirme testi: blok 6, en fazla 24 soru (Faz 0, 2026-09-22)

**Bağlam:** Ek A: başlangıç A2, blok 5 soru, geçme %60, yukarı %80, en fazla 25 soru.

**Karar:** Blok 6 soru, en fazla 24 soru. Geçme 4/6, yukarı 5/6. Başlangıç A2 aynı.

**Gerekçe:** 4 şıklı soruda 5'lik blokta şansla 3/5 alma olasılığı ≈ %10; tek şanslı blok kullanıcıyı bir seviye yukarı iter. 6'lık blokta 4/6 şansla geçme olasılığı ≈ %4.

**Bedel:** Seviye başına en az 10 soru şartı değişmez; tekrar çözümde aynı soruların gelmemesi için havuz ≥ 12 olmalı (iki farklı blok).

---

## K-004 — İçerik JSON dosyalarında, veritabanına yazılmaz (Faz 0, 2026-09-22)

**Karar:** İçerik `content/{seviye}/*.json` dosyalarında durur; backend açılışta belleğe yükler (salt okunur katalog). Veritabanı yalnız kullanıcı durumunu tutar. İçerik tabloları ve CSV tohumlayıcı kaldırılır.

**Gerekçe:** SPEC 9.1 (içerik koddan ayrı, seviye bazlı JSON) ve 9.2 (içerik depolamaya yazılmaz). Ayrıca açılışta canlı veritabanına yazan tohumlayıcı bir kez yayını düşürdü (502).

**Sonuç:** Kullanıcı durumu içeriğe kararlı `id` ile bağlanır. İçerikten bir öğe silinirse ona bağlı kullanıcı kayıtları yetim kalır; yükleyici bunları yok sayar, silmez.

---

## K-005 — Config: tipli `@ConfigurationProperties` (Faz 0, 2026-09-22)

**Karar:** Ek A değerleri `application.yml` altında `level.*`, `coach.*`, `srs.*` vb. tipli sınıflarda. Ön yüz ihtiyaç duyduğu değerleri API'den okur.

**Gerekçe:** K7 (koda sabit sayı yok) + tek kaynak. Saf fonksiyonlar config nesnesini parametre alır, testte istenen değerle kurulur.

---

## K-006 — Eski brief'le çelişkiler SPEC lehine (Faz 0, 2026-09-22)

İlk brief'in iki kuralı SPEC'le çelişiyor; SPEC geçerlidir:

- "Serbest metin yok" → "Bugün ne yaptın?" formunda isteğe bağlı kısa not; Schreiben'de kullanıcı metin yazar.
- "Kullanıcı süre/kaynak girmez" → dış çalışma kaydında süre, kaynak türü ve isteğe bağlı sonuç girilir.

Korunan ilke: formlar olabildiğince hazır seçeneklidir, tek dokunuşla doldurulur.

---

## K-007 — "Hedefe yakın" yorumu (Faz 0, 2026-09-22)

**Karar:** "Hedefe yakın" = en az 3 becerinin tahmini **hedef seviyede veya üstünde**.

**Gerekçe:** SPEC "hedef seviyede" diyor; hedefi aşan bir beceri de hedefe yakınlığa katkı sağlamalı.

---

## K-008 — `verified:false` kanıtı bir kademe düşük: varsayılan korunur (Faz 0, 2026-09-22)

**Bağlam:** `verified: true`'yu yalnız kullanıcı işaretler. Bu yüzden başlangıçta tüm site içeriği doğrulanmamıştır; site içi Lesen kanıtı "orta" yerine "düşük" sayılır. "Güvenilir" durumu en az bir orta/yüksek kanıt ister.

**Karar:** Ek A varsayılanı (açık) korunur. Arayüz, "güvenilir" duruma neden ulaşılamadığını açıkça söyler (ör. "içerik henüz doğrulanmadı; dış sonuç girersen güven artar").

**Bedel:** Kullanıcı içeriği doğrulamadıkça yalnız site içi çalışmayla hiçbir beceri "güvenilir" olamaz.

---

## K-009 — Canlı Supabase projesi silindi; yeni sistem hazır olana kadar tek proje (2026-09-22)

**Bağlam:** Supabase ücretsiz planı aynı anda 2 aktif proje veriyor (biri `denizcilik sitesi`). Geliştirme projesini (`ichsprechedeutsch-dev`, `lokxrmomepycydvxcsfq`) açmak için kullanıcı canlı projeyi (`Alperen Proje`, `ladsutkfjsybylbnnmok`) sildi. Eski kullanıcı verisi ve K-002'de öngörülen `backup_*` yedeği de onunla gitti.

**Karar:** Eski canlı site yeni sistem hazır olana kadar kapalı kalır (Render backend veritabanına bağlanamıyor, Vercel sayfaları veri yükleyemiyor). Geliştirme tek proje üzerinde, `ichsprechedeutsch-dev`'de yapılır. Publish günü ya dev projesi canlıya dönüştürülür ya da yeni bir canlı proje açılır — o gün karar verilir.

**Gerekçe:** Kullanıcı seçimi. Zaten sıfırdan başlanıyor (K-002); eski sürümü ayakta tutmanın faydası yok.

**Sonuç:**
- `backend/.env` artık var olmayan projeyi gösteriyor; kullanılmaz.
- Faz 1 migrasyonlarındaki `beforeMigrate` yedeği yine yazılır (9.2), ama korunacak eski veri yok.
- Publish öncesi yapılacaklar: canlı veritabanı kararı, Render ortam değişkenleri, Supabase Auth URL ayarları, `core/config.js` canlı anahtarları.

---

## K-010 — Kalıcılık katmanı: JdbcTemplate depoları, kademe okunurken hesaplanır (Faz 1, 2026-09-22)

**Karar:** Kullanıcı durumu her alan için bir depo sınıfının (`OnboardingStore`, `LevelStore`, `ActivityStore`, `PlacementStore`) arkasındadır; servisler ve saf sınıflar SQL görmez (SPEC 9.2). Kanıtın güvenilirlik kademesi veritabanına yazılmaz; `TierPolicy` okurken config'e göre hesaplar.

**Gerekçe:** Config değişince (K7) eski kanıtlar da yeni kurala uyar; kademe ile kaynak arasında çelişen iki kayıt oluşmaz.

---

## K-011 — Tablolar kendi fazlarında eklenir (Faz 1, 2026-09-22)

**Karar:** V4 yalnız Faz 1'in ihtiyaç duyduğu tabloları kurar (profil, hedef, ritim, oturum, yerleştirme, değerlendirme, aktivite, kanıt). Kelime/gramer ilerlemesi, hata kaydı, haftalık plan kendi fazlarında ayrı migrasyonla gelir.

**Gerekçe:** Kullanılmayan tablo şeması, ihtiyaç netleşmeden dondurulmuş olur.

---

## K-012 — Yerleştirme testinde "Bilmiyorum" seçeneği (Faz 1, 2026-09-22)

**Karar:** Her soruda şıkların yanında "Bilmiyorum" var; yanlış sayılır. Her soru cevaplanmadan sonraki bloğa geçilmez.

**Gerekçe:** K-003 ile aynı amaç: şansla doğru cevap sonucu yukarı itmesin. Kullanıcıya "tahmin etmek sonucu yanıltır" diye söylenir.

---

## K-013 — Faz 1 arayüzü: Koçluk ve Öğren sayfaları kendi fazlarında (Faz 1, 2026-09-22)

**Karar:** Menüde yalnız mevcut sayfa var (Ana Sayfa; Ayarlar küçük menüde). Olmayan sayfaya link konmaz (K4). Faz 1'de:
- Ana Sayfa: hedef + kısa beceri durumu + "Bugün ne yaptın?" + son 7 gün. Koç önerileri ve günlük plan Faz 2'de eklenir.
- Hedef ve ritim değişikliği şimdilik onboarding ekranından (`baslangic.html?duzenle=1`) yapılır; Faz 2'de Koçluk'a taşınır.
- Ziyaretçi ana sayfasında yalnız hero metni SPEC 7'ye uyduruldu; sayfanın geri kalanı ve "Nasıl çalışır" hâlâ eski gün gün program modelini anlatıyor — Faz 2'de yeniden yazılacak.

---

## K-014 — Uçtan uca API testi geliştirme veritabanında, isteğe bağlı (Faz 1, 2026-09-22)

**Karar:** `ApiFlowIT` gerçek veritabanına (yalnız `ichsprechedeutsch-dev`; başka bir veritabanında çalışmayı reddeder) Spring Security test JWT'siyle istek atar ve sonunda kendi test kullanıcısını siler. Normal build'de atlanır; `IT_DB=dev ./gradlew test --tests '*ApiFlowIT'` ile çalışır.

**Gerekçe:** Gerçek hesap açmadan uçtan uca doğrulama. Birim testleri veritabanı gerektirmez.

---

## K-015 — Kullanıcıya görünen metin düzgün Türkçe (Faz 1, 2026-09-22)

**Karar:** Yeni sunucu hata mesajları Türkçe karakterlerle yazılır ("Kayıt bulunamadı"). Birleşik cümleler (hedef bildirimi gibi) sunucu yerine istemcide üretilir; sunucu yalnız veriyi döner (`goalReached: "B1"`).

**Not:** Faz 1 öncesinden kalan eski mesajlar (güvenlik, profil) hâlâ ASCII; dokunulan yerlerde düzeltilecek.

---

## K-016 — Seviye ölçümü önerisi kural sırasının başına eklendi (Faz 2, 2026-09-22)

**Karar:** SPEC 5.3'te sekiz kural var; bunlara `ASSESSMENT` adında dokuzuncu bir kural eklendi ve sıranın başına kondu (`coach.rule-order`). Kural, SPEC 4.6/4.7'den çıkar: çalışma seviyesi varsayılana düşmüşse (hiç ölçüm yok) yerleştirme testi, ölçüm eskimişse (45 gün / 6 yeni kanıt) yeniden değerlendirme önerir.

**Gerekçe:** Sekiz kuralın hepsi "seviyen belli" varsayar. Ölçüm yokken koç, dayanağı olmayan bir seviyeye göre öneri üretmiş olurdu (K3). Kural sırası config'de olduğu için istenirse kapatılabilir.

---

## K-017 — Plan hesabının ayarları da config'e taşındı (Faz 2, 2026-09-22)

**Karar:** Ek A'da bulunmayan ama plan hesabının ihtiyaç duyduğu değerler `coach.*` altına eklendi: `comeback-day-fraction` (0.5), `comeback-spread-days` (3), `plan-block-minutes` (5), `plan-min-item-minutes` (10), `recommendation-share-boost` (5), `exam-practice-fraction` (0.5), `weekly-shares` + `shares-by-level`.

**Gerekçe:** K7 — eşik ve oran koda yazılmaz. Değerler SPEC 5.3/5.4'ün sözünden türetildi ("geri dönüş günü kısa olsun", "birikmiş tekrarı birkaç güne yay"), sayıları SPEC vermediği için varsayılan seçildi ve buraya yazıldı.

---

## K-018 — Ziyaretçi sayfaları yalnız çalışan özelliği anlatır (Faz 2, 2026-09-22)

**Karar:** Ana sayfadaki ürün görseli, uydurma bir "9. gün / 30" ekranı yerine koç kartlarının gerçek biçimini gösterir ve "örnek" diye etiketlenir. "Nasıl çalışır" sayfasına "Şu an ne hazır, ne yolda" bölümü eklendi: kelime tekrarı, gramer, artikel ve hata hafızası henüz site içinde yok, bu açıkça yazılıyor.

**Gerekçe:** K5 (resmî seviye iddiası yok) ve K3 (uydurma yok) ziyaretçi sayfaları için de geçerli. Olmayan modülün ekran görüntüsünü göstermek, kayıt olan kullanıcıyı ilk dakikada yanıltırdı.

---

## K-019 — Yeni kelime tekrarın önüne geçmez (Faz 3a, 2026-09-22)

**Karar:** Günün kelime oturumunda önce vadesi gelen tekrarlar alınır (en gecikmişten başlayarak, `srs.daily-review-limit` kadar). Tekrar sınırı dolduysa o gün hiç yeni kelime verilmez; kalan tekrarlar sonraki günlere yayılır.

**Gerekçe:** SPEC 8.1 "günlük yeni kelime ve tekrar limitleri vardır, birikmiş tekrarlar günlere yayılır" diyor ama ikisi çakışınca hangisinin önce geldiğini yazmıyor. Biriken tekrarın üstüne yeni kelime eklemek borcu büyütür; koç kuralı da (DUE_REVIEWS) tekrarı öne alıyor. Sınır config'de (K7).

---

## K-020 — Arayüz doğrulaması gerçek API yanıtlarıyla (Faz 3a, 2026-09-22)

**Karar:** `MockDumpIT` (yalnız `MOCK_DUMP=<klasör>` verilince çalışır) geliştirme veritabanında kendi test kullanıcısını kurar, uçtan uca bir akış yaşar ve `/api/...` yanıtlarını dosyaya döker. Arayüz bu dosyalarla, girişsiz bir sahte kopyada denenir; dosyalar `frontend/_mock/` altında durur ve `.gitignore`'dadır.

**Gerekçe:** Arayüzü doğrulamak için ya gerçek hesapla giriş yapmak ya da elle sahte JSON yazmak gerekiyordu. Birincisini yapmıyorum (parola kullanıcınındır), ikincisi gerçek yanıt şeklinden sapıp yanıltıcı "çalışıyor" sonucu verir. Bu yol ikisini de çözer ve her fazda yeniden kullanılır.

---

## K-021 — Artikel istatistiği SRS durumunu değiştirmez (Faz 3a, 2026-09-22)

**Karar:** Artikel pratiğinin sonuçları `learning_answer` tablosuna yazılır; `vocabulary_progress` (merdiven durumu) etkilenmez. Koçluk ve Öğren'deki der/die/das oranları bu tablodan türetilir.

**Gerekçe:** SPEC 8.1: "SRS durumunu tekrar oturumu değiştirir; diğer pratikler istatistiğe yazılır." Artikel pratiğinde bir kelimeye ilk kez dokunmak onu tekrar sırasına sokmamalı. Tek istisna SPEC'in kendi kuralı: tekrar oturumunda kelime doğru ama artikel yanlışsa not "Zorlandım"a düşer.

---

## K-022 — Alıştırma ekranı kendiliğinden ilerlemez (Faz 3a, 2026-09-22)

**Karar:** Kelime ve artikel alıştırmalarında cevap verildikten sonra doğru cevap ekranda kalır ve ilerlemek için "Devam"a basılır. Otomatik ilerleme (eski sürümdeki 900 ms) yok.

**Gerekçe:** Kullanıcının bildirdiği sorun: artikel sorusunda ekran hızla ilerlediği için doğru cevap görülemiyordu. Yanlış yapılan soruda öğrenilecek tek şey doğru cevaptır; onu göstermeden geçmek alıştırmayı işe yaramaz hale getirir.

---

## K-023 — A0 kullanıcısı A1 içeriğiyle çalışır (Faz 3b, 2026-09-23)

**Karar:** İçerik seçiminde çalışma seviyesi A0 ise A1 içeriği gösterilir (`Level.forContent()`); kelime, artikel ve gramer modüllerinin üçü de bunu kullanır.

**Gerekçe:** "A0" bir içerik seviyesi değil, "henüz A1 değil" demektir (SPEC 4.6). Filtre birebir uygulanınca sıfırdan başlayan kullanıcıya hiçbir kelime ve konu gelmiyordu — yani sisteme en çok ihtiyacı olan kullanıcı boş ekran görüyordu. Faz 3a'da kelime modülünde de aynı sessiz hata vardı; bu kararla üçü birden düzeldi.

---

## K-024 — Gramer ders içeriği A1–A2 ile başladı (Faz 3b, 2026-09-23)

**Karar:** 22 A1/A2 konusunun tamamına ders içeriği yazıldı (açıklama, Türkçe karşılaştırma notu, en az iki örnek, iki kontrollü üretim alıştırması). B1–C1'deki 14 konu gramer ağacında görünür ama "içerik hazır değil" etiketiyle listelenir ve açılamaz.

**Gerekçe:** SPEC 10.1'in kapsam hedefi bütün seviyeleri istiyor; hepsini tek fazda yazmak içerik kalitesini düşürürdü. Olmayan içeriği gizlemek yerine açıkça söylemek K4'e uygun: kullanıcı neyin hazır olduğunu görür. Mini sorular yeniden yazılmadı; konuya etiketli mevcut 46 öğrenme sorusu kullanıldı.

---

## K-025 — Yazım hataları ayrı etiketle hafızaya girer (Faz 3b, 2026-09-23)

**Karar:** Büyük/küçük harf ve ä/ö/ü/ß yazımı kabul edilir (K7, `answer.*`), ama hata hafızasına `YAZIM_BUYUK_KUCUK` ve `YAZIM_UMLAUT` etiketleriyle yazılır. Bu etiketler gramer konularıyla aynı akışta ilerler: aktif → düzeliyor → çözüldü.

**Gerekçe:** SPEC 8.3 "kabul et + uyar, ayrıca hata etiketiyle kaydet" diyor. Ayrı etiket, koçun "sürekli umlaut atlıyorsun" gibi bir öneri üretebilmesini sağlar; cevabı yanlış saymak ise kullanıcıyı gramer hatasıyla yazım hatasını karıştırmaya iterdi.

---

## K-026 — Rubrik ölçüt adları da config'de (Faz 3c, 2026-09-23)

**Karar:** Schreiben rubriğinin dört ölçütünün adı (`Görevi tamamlama`, `Tutarlılık`, `Kelime çeşitliliği`, `Gramer doğruluğu`) `level.schreiben-rubric.criteria-names` altında tutulur; sayıları zaten Ek A'daydı.

**Gerekçe:** Ölçüt sayısı config'de, adları kodda olsaydı ikisi sessizce ayrışabilirdi. Doğrulayıcı da adların sayıyla uyuştuğunu kontrol ediyor.

---

## K-027 — Dört beceri tek serviste toplandı (Faz 3c, 2026-09-23)

**Karar:** Lesen, Hören, Schreiben ve Sprechen çalışmaları tek `SkillPracticeService` ve tek `/api/skills/*` denetleyicisiyle sunulur. Saf mantık ayrı sınıflarda: `DictationCheck`, `WritingRubric`, `CanDoScore`.

**Gerekçe:** Dört akışın ortak işi aynı: oturum aç, gönder, kanıt kurallarını uygula, çalışmayı otomatik kaydet. Dört ayrı servis bu ortak kısmı dört kez kopyalardı. Beceriye özgü kurallar (Hören kanıt üretmez, Schreiben öz değerlendirme) tek yerde ve okunur biçimde duruyor.

---

## K-028 — Beceri içeriği A1–A2 ile başladı (Faz 3c, 2026-09-23)

**Karar:** Her iki seviye için 2 okuma metni (5'er soru), 3 dinleme alıştırması (2 dikte + 1 anlama), 2 yazma görevi (örnek cevaplı), 2 konuşma görevi ve 11 Kann-Beschreibung yazıldı. Hepsi `verified:false`. B1–C1 beceri içeriği Faz 4'te.

**Gerekçe:** Kapsam hedefi bütün seviyeleri istiyor ama dört beceri × altı seviye içeriği tek fazda üretmek kaliteyi düşürürdü. İçeriği olmayan seviyede liste boş kalır ve arayüz "bu seviyede hazır içerik yok, dışarıda çalışıp sonucunu gir" der — olmayan şey varmış gibi gösterilmez (K4).

---

## K-029 — Demo modu ayrı hesapta, simüle tarih yalnız orada geçerli (Faz 4, 2026-09-23)

**Karar:** Demo verisi `app_user.is_demo` ile işaretli ayrı bir hesapta tutulur (V7). Simüle tarih `X-Demo-Date` başlığıyla gönderilir; sunucu bu başlığı **yalnız demo hesabında** dikkate alır, diğer hesaplarda sessizce yok sayar. Arayüzde demo hesabı için ekranın üstünde sarı bir "DEMO" şeridi ve tarih seçici çıkar. Uygulama kendiliğinden demo hesabı açmaz; bayrak elle işaretlenir.

**Gerekçe:** SPEC 12.2 ayrı depolama, görünür etiket ve "zamanı ileri sar" istiyor. Başlığı herkese açmak, gerçek kullanıcının geçmişine ileri tarihli kayıt yazma riski doğururdu; bayrağı hesapta tutmak bu riski tek satırda kapatıyor (`if (!user.isDemo()) return gerçek;`).

---

## K-030 — Yerel geliştirme veritabanı Docker'da (Faz 4, 2026-09-23)

**Karar:** Yerel geliştirme ve entegrasyon testleri Docker'daki Postgres 16 konteynerinde (`isd-postgres`, port 54329, hacim `isd-pgdata`) çalışır; ayarlar `backend/.env.local`'da (repoya girmez). `tools/backend.cmd` önce bunu kullanır, konteyner yoksa oluşturur. Testler `IT_DB=local` ya da `IT_DB=dev` ile hedef seçer (`TestDatabase`); ikisi de canlı veritabanını reddeder. Supabase yalnız giriş (Auth/JWKS, 443) için kullanılır; yayın mimarisi değişmez.

**Gerekçe:** Bu ağda Supabase pooler'ına (5432/6543) TLS el sıkışması kesiliyordu; bilgisayarda suçlu bulunmadı (üçüncü parti antivirüs, VPN, proxy yok), IPv6 çıkışı da yok. Geliştirmeyi ağa bağımlı olmaktan çıkarmak, günlerdir bekleyen doğrulamayı açtı. Migration'lar düz Postgres'le uyumlu (Supabase'e yalnız yorumlarda atıf var). Kullanıcı onayıyla `postgres:16` imajı indirildi.
