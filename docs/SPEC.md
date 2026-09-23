# GÖREV: "Ich spreche Deutsch" sitesini ücretsiz, otomatik bir Almanca koçuna dönüştür

## 0. Çalışma şekli (ÖNCE BUNU OKU)

Bu iş fazlara bölünmüştür. Her fazda şu döngüyü izle:

1. Fazın başında o fazın alt görev listesini çıkar ve onayımı bekle.
2. Uygula. Saf fonksiyonların birim testlerini aynı fazda yaz (Bölüm 12).
3. Fazın sonunda dur: ne yaptığını özetle, `docs/DECISIONS.md` (alınan kararlar + gerekçe) ve `docs/PROGRESS.md` (durum, açık sorular, sonraki fazın ön koşulları) dosyalarını güncelle ve onayımı bekle.

Testler geçmeden ve build/konsol hatasız olmadan faz bitmiş sayılmaz.

Bu doküman repoda `docs/SPEC.md` olarak durur. Bölüm 2'deki değişmez kuralları her oturumda okuduğun talimat dosyasına da (ör. `CLAUDE.md`) yaz. Yeni bir oturuma SPEC, DECISIONS ve PROGRESS dosyalarını okuyarak başla. Bir ürün kararında emin değilsen tahmin yürütme; seçenekleri ve önerini yazıp sor.

### FAZ 0 — Analiz (kod yazma)

Mevcut repoyu incele ve bana bir rapor ver:

- Teknoloji yığını, dosya yapısı, sayfalar, componentler, CSS
- Veri yapıları ve kalıcılık: veri nerede tutuluyor, hesap/giriş sistemi var mı
- Soru bankası, test sistemi, mevcut seviye testi, çalışma takip sistemi
- Mevcut kullanıcı akışı
- Neyin korunacağı, neyin refactor edileceği, neyin silineceği (gerekçesiyle)
- Hedef mimariye geçiş planı ve mevcut veriden migrasyon planı
- Kalıcılık ve hesap modeli için önerin (9.2). Ben seçmeden Faz 1'e geçme.
- Koç kurallarının öncelik sırasına (5.3) veya Ek A'daki varsayılanlara itirazın varsa, gerekçesiyle

Mevcut teknoloji yığınını koru; değiştirmek gerektiğini düşünüyorsan önce gerekçesiyle sor.

### Sonraki fazlar

- FAZ 1 — Veri modeli, kalıcılık katmanı, config, onboarding, yerleştirme testi, seviye modeli (kanıt → tahmin, çalışma seviyesi, hedef tamamlama mantığı), aktivite kaydı ("Bugün ne yaptın?" + otomatik kayıt altyapısı)
- FAZ 2 — Koç motoru, haftalık/günlük plan, Ana Sayfa, Koçluk sayfası
- FAZ 3a — Kelimeler/SRS, Artikel, cevap kontrolü
- FAZ 3b — Gramer modülü, hata hafızası
- FAZ 3c — Lesen, Hören, Schreiben, Sprechen modülleri
- FAZ 4 — Sınav bölümü, hedef tamamlama ekranı, demo modu, tasarım cilası, uçtan uca kabul testleri

Faz 2'de henüz yazılmamış modüllere (SRS, hata hafızası) dayanan koç kuralları veri modeline göre yazılır ve test verisiyle doğrulanır; modüller geldikçe canlı veriye bağlanır.

Her faz kendi modüllerinin içeriğini de üretir (Bölüm 10). İçerik incelemesi, üretimden ayrı bir oturumda yapılır.

## 1. Ürün

%100 ücretsiz, otomatik Almanca koçu. "Koç" bir insan veya chatbot değil, sitenin kendisidir. Sistem kullanıcının yerine Almanca öğrenmez; öğrenmesini organize eder, yönlendirir, ölçer, hataları hatırlatır ve sonraki adımı belirler. Kullanıcı ister sitedeki içerikle, ister dış kaynaklarla (YouTube, kitap, kurs, öğretmen, podcast, başka uygulamalar) çalışır ve yaptığını sisteme girer.

Kullanıcı siteye bağımlı olmamalı: seviye tahmini ve hedef tamamlama dahil hiçbir kritik işlev yalnızca site içi modüllerle çalışmaya bağlı olmamalı.

Temel his: "Sisteme gir, sistem sana nerede olduğunu ve sıradaki adımı söylesin."

Bu ürün şunlar DEĞİLDİR: Almanca kaynak sitesi, basit çalışma takipçisi, AI chatbot. Premium/Pro/kredi/ücretli plan atmosferi olmayacak.

Arayüz dili Türkçedir; öğrenilen dil Almancadır.

## 2. Değişmez kurallar (tüm kararlarda geçerli)

- K1. Gerçek dil seviyesi SADECE dört beceriden oluşur: Lesen, Hören, Schreiben, Sprechen.
- K2. Kelime, gramer, artikel, öğrenme testleri ve tekrarlar "öğrenme aktivitesi"dir. Seviyeye ASLA doğrudan çevrilmez ("500 kelime = %10 seviye" yok). Ama koçun kararlarını etkiler.
- K3. Veri yoksa sonuç üretme. "Henüz yeterli veri yok" göster. Sahte yüzde, sahte analiz, sahte "koçun seni analiz ediyor" yazısı yok. Her tahmin ve her koç önerisi gerçek veriye dayanır ve hangi veriye, hangi kurala dayandığını gösterebilir.
- K4. Sistem olmayan bir yeteneği varmış gibi göstermez. Gerçek AI entegrasyonu yok; koçluk kural tabanlıdır.
- K5. Hiçbir sonuç resmi CEFR/Goethe değerlendirmesi gibi sunulmaz. Dil: "Platform içi değerlendirmeye göre ... civarında". Belirsizlik varsa göster.
- K6. Kullanıcı hedefini kendisi seçer. Hedefe ulaşınca sistem onu otomatik olarak üst seviyeye itmez; kullanıcı isterse hedefini değiştirir.
- K7. Eşik, aralık ve limitler config varsayılanıdır (Ek A): koda sabit değer olarak yazılmaz, config'den okunur. Bölüm 10'daki içerik miktarları kapsam hedefidir; onboarding seçenekleri arayüz seçenekleridir. Bunların dışındaki sayılar (ör. "der %82", "son 3 oturumda %60") yalnızca örnektir.

## 3. Onboarding (kısa, en fazla 5 adım, dashboard'dan önce)

1. Başlangıç: "Almancaya sıfırdan başlıyorum" (→ çalışma seviyesi A0, test yok) / "Bir miktar Almanca biliyorum" (→ yerleştirme testi önerilir, atlanabilir)
2. Yerleştirme testi veya "Şimdilik atla" (sadece "bir miktar biliyorum" diyenlere)
3. Hedef: A1 / A2 / B1 / B2 / C1. Test yapıldıysa tahminin üstündeki seçenekler gösterilir (tahmin C1 ise C1 sunulur); test atlandıysa hepsi gösterilir.
4. Amaç: Sınav / Üniversite / İş / Günlük hayat / Yurtdışı / Kişisel gelişim / Diğer
5. Çalışma ritmi (tek ekran): günlük süre 15 / 30 / 45 / 60 / 90+ dk ve haftada kaç gün (1–7)

Sonuç: kişisel yol (ör. A1 → A2 → B1) ve ilk haftalık plan (5.4).

Test sonradan yapılırsa ve sonuç seçilen hedefe eşit ya da üstünde çıkarsa bu kullanıcıya söylenir; hedef otomatik değişmez (K6), "Hedefini korumak mı, değiştirmek mi istersin?" diye sorulur. Onboarding cevaplarının hepsi sonradan Koçluk'tan değiştirilebilir.

## 4. Seviye modeli (kanıt tabanlı)

İki kavramı karıştırma:

- **Seviye tahmini:** dört beceriden gelen kanıtlarla hesaplanır; kullanıcıya "Platform içi değerlendirmeye göre ... civarında" diye gösterilir.
- **Çalışma seviyesi:** sadece hangi içeriğin önerileceğini belirler (4.6). Seviye iddiası değildir; arayüzde "seviyen" diye sunulmaz.

### 4.1 Yerleştirme testi

- A1–C1 aralığını kapsar ve kademeli (adaptif) çalışır. Algoritma basit ve açıklanabilir olmalı:
  - Yukarı aşama: başlangıç seviyesinde bir blok soru sorulur. Blok başarısı ≥ yukarı eşik → bir üst seviyeye geç (C1'de dur; sonuç C1). Geçme eşiği ≤ başarı < yukarı eşik → sonuç bu seviye. Başarı < geçme eşiği → yukarı çıkarak gelindiyse sonuç bir önceki seviye; başlangıç seviyesindeysen aşağı aşamaya geç.
  - Aşağı aşama: bir alt seviyeye in; geçme eşiğini geçen ilk seviye sonuçtur. Aşağı inerken yukarı çıkılmaz. A1'de de geçilemezse sonuç "A1'in altında".
  - Toplam soru sayısı üst sınırlıdır. (Değerler: Ek A)
- Soru bankası: her seviyede en az 10 soru (tekrar çözümde aynı sorular gelmesin), seviye ve tür (gramer / kelime / kısa okuma) etiketli.
- Sonuç sadece KABA başlangıç noktasıdır: "A2 civarı (yerleştirme testi, düşük güven)". Beceri profilini doldurmaz; Koçluk'ta beceri profilinden ayrı, tarihiyle gösterilir.
- Mevcut seviye testi bu yapıya uyarlanabiliyorsa Faz 1'de uyarlanır; değilse minimum soru seti üretilir.

### 4.2 Beceri kanıtları

Her kanıt kaydı: beceri, seviye, kaynak, güvenilirlik kademesi (4.4), puan ve azami puan, tarih; site içi ise içerik id'si ve o içeriğin `verified` durumu.

- Lesen: site içi okuma testleri ve Sınav bölümündeki Lesen pratikleri (otomatik puan) + kullanıcının girdiği dış sonuçlar.
- Schreiben: site içi yazma görevlerinde kullanıcının rubrikle kendi değerlendirmesi (görev tamamlama, tutarlılık, kelime çeşitliliği, gramer doğruluğu) + dış sonuçlar (ör. "Goethe A2 Schreiben, kendi puanım 17/20").
- Hören: sadece kullanıcının girdiği dış sonuçlar (ör. modelltest, sınav, öğretmen, kurs testi). Site içi Web Speech API dinleme/dikte alıştırmaları sentetik sesle çalıştığı için öğrenme aktivitesidir, kanıt değildir.
- Sprechen: CEFR "Kann-Beschreibungen" öz değerlendirmesi (seviye bazlı yapabilirlik ifadeleri: evet / kısmen / hayır) + kullanıcının girdiği dış değerlendirmeler.

Öz değerlendirmelerde puan oranı: rubrikte alınan puan / azami puan; Kann-Beschreibungen'de cevapların puan ortalaması (Ek A).

Sadece süre kaydı ("30 dk DW dinledim") beceri seviyesi üretmez; çalışma geçmişine yazılır.

### 4.3 Kanıttan seviye tahminine

- Puan oranı ≥ geçme eşiği olan kanıt, kendi seviyesini ve altındaki seviyeleri destekler (pozitif kanıt).
- Geçme eşiğinin altındaki kanıt, kendi seviyesine ve üstündekilere karşı sayılır (negatif kanıt).
- Her kanıtın ağırlığı güvenilirlik kademesinden gelir (4.4). Tahminde yalnızca belirli bir süreden yeni kanıtlar kullanılır (Ek A).
- Bir becerinin tahmini: destek ağırlığı karşı ağırlıktan büyük olan en yüksek seviye.
- Hiçbir seviye bunu sağlamıyorsa, başarısız olunan en düşük seviyenin altında olduğu söylenir: "B1'in altında (alt seviyelerde veri yok)". Bu durum düşük güvendir.
- Tahmin seviyesinin bir üstünde hiç deneme yoksa tahmin bir alt sınırdır: "en az A2" diye göster.
- Her tahmin hangi kanıtlara dayandığını listeleyebilmeli (K3).

Daha iyi bir yöntem önerirsen gerekçesiyle sor; açıklanabilirlik şartı değişmez.

### 4.4 Güvenilirlik kademeleri ve beceri durumu

Kaynak → kademe (varsayılanlar config'de):

- Yüksek: resmi sınav sonucu
- Orta: öğretmen değerlendirmesi; cevap anahtarlı modelltest (Lesen, Hören); site içi otomatik puanlı beceri testi
- Düşük: öz değerlendirme (rubrik, Kann-Beschreibungen); kendi puanlanan Schreiben/Sprechen modelltesti; uygulama/kurs testi

`verified: false` içerikten gelen kanıt bir kademe düşük sayılır (config'den kapatılabilir).

Beceri durumu:

- "Veri yok": hiç kanıt yok.
- "Tahmini: X (düşük güven)": tahmin var ama "güvenilir" şartı sağlanmıyor.
- "X (güvenilir)": tahmin seviyesini destekleyen yeterli sayıda pozitif kanıt var; en az biri güncel, en az biri orta veya yüksek kademede (Ek A).

Koçluk'ta her beceri için durum, güven ve kanıt sayısı gösterilir.

### 4.5 Genel seviye tahmini

- Verisi olan becerilerin tahminlerinin medyanı, aşağı yuvarlanarak (yöntem config'de; alternatif: en zayıf beceri).
- Yanında aralık ve en zayıf beceri gösterilir: "A2 civarı (beceriler A1–B1 arası; en zayıf: Sprechen)".
- Verisi olan beceri sayısı eşiğin altındaysa: "Henüz yeterli veri yok".
- Eksik beceri açıkça belirtilir: "Hören ve Sprechen için veri olmadığından tahmin eksik".

### 4.6 Çalışma seviyesi (içerik seçimi için)

- Sırayla: (1) genel seviye tahmini varsa o; (2) yoksa yerleştirme testi sonucu ("A1'in altında" ise A0); (3) o da yoksa onboarding cevabı: "sıfırdan" → A0; "bir miktar biliyorum" ama test yok → varsayılan seviye (Ek A) ve koç yerleştirme testini önerir.
- Belirli bir beceri için içerik seçerken o becerinin tahmini varsa o kullanılır.
- A0'dan çıkış: A0 temel konuları tamamlandığında, yerleştirme testi A1 veya üstünü verdiğinde ya da A1 düzeyinde beceri kanıtı girildiğinde çalışma seviyesi A1 olur. Bu bir seviye iddiası değildir.

### 4.7 Yeniden değerlendirme

Son değerlendirmeden bu yana belirli bir süre geçtiğinde veya yeterli yeni kanıt biriktiğinde (hangisi önce gelirse; Ek A) koç yeniden yerleştirme testi veya Kann-Beschreibungen öz değerlendirmesi önerir.

### 4.8 Hedef tamamlama (kriterler config'de)

- Dört becerinin her birinde tahmin ≥ hedef seviye;
- her beceride hedef seviyeyi destekleyen yeterli sayıda pozitif kanıt, en az biri güncel;
- (opsiyonel, varsayılan KAPALI) hedef seviyenin temel gramer konuları (`core: true`) tamamlanmış. Varsayılan kapalıdır, çünkü dışarıda çalışıp kanıt giren kullanıcı da hedefe ulaşabilmeli (Bölüm 1).
- Minimum güven config'dedir (varsayılan: "tahmini" yeterli). Tamamlama ekranı her becerinin güven durumunu açıkça gösterir: "Sprechen: yalnızca öz değerlendirme, düşük güven".

Ekran: "🎉 Hedef seviyene ulaştın — platform içi verilerine göre". Resmi sertifika dili yok. Sistem yeni hedef dayatmaz (K6); kullanıcıya hedefini korumak mı değiştirmek mi istediği sorulur. Hedef değişmezse koç seviyeyi koruma ve pekiştirme önerileri üretir.

## 5. Koç motoru (kural tabanlı, modüler)

### 5.1 Girdi ve çıktı

Girdi: hedef, çalışma seviyesi, beceri tahminleri ve kanıtları, aktivite kayıtları, öğrenme testi sonuçları, hata kayıtları, SRS ve gramer tekrar kuyruğu, çalışma ritmi (günlük süre, haftalık gün), son aktif tarih ve bugünün tarihi (parametre olarak).

Çıktı: öneri listesi. Her öneri: tür, başlık, tahmini süre, öncelik, "neden" açıklaması (hangi kurala ve hangi verilere dayandığı, ör. "Son 20 Dativ yanıtında %55"), ilgili içeriğe link veya "dışarıda yap + sonucu gir" aksiyonu.

### 5.2 Tanımlar

- Oturum: bir modülde başlatılıp bitirilen tek bir alıştırma veya test çalıştırması. Test ve hata kayıtları oturum id'si taşır.
- Başarı penceresi: bir konunun başarısı tüm zamanlar üzerinden değil, son N yanıt üzerinden hesaplanır; kullanıcı düzeldiğinde öneri de kalkar.
- Hedefe yakın: belirli sayıda becerinin tahmini hedef seviyede.

(Değerler: Ek A)

### 5.3 Kurallar (varsayılan öncelik sırasıyla; sıra config'den değiştirilebilir)

1. Kullanıcı bir süredir hiç çalışmadıysa → kısa, geçmişe dayalı geri dönüş planı (birikmiş tekrarları günlere yayar).
2. Tekrar zamanı gelmiş kelimeler veya gramer kontrolleri varsa → tekrar (adet ile, günlük limit dahilinde).
3. Tekrarlayan hata varsa (aynı hata etiketi, birden fazla farklı oturumda; 8.4) → hedefli tekrar.
4. Bir gramer konusunda pencere içi başarı eşiğin altında ve pencerede yeterli yanıt varsa → konu tekrarı.
5. Bir becerinin hiç kanıtı yoksa → o beceri için kanıt üretecek çalışma (site içi beceri testi veya dış sonuç girişi).
6. Bir beceri bir süredir hiç çalışılmadıysa → o beceriyi öner.
7. Bir artikel türünde (der/die/das) başarı eşiğin altındaysa (tür başına yeterli yanıtla) → artikel pratiği.
8. Hedefe yakınsa → sınav pratiğini artır.

- Veri yoksa (yeni kullanıcı / A0) → onboarding cevaplarına ve çalışma seviyesine göre başlangıç planı. Çalışma seviyesi A0 iken 5. ve 6. kurallar çalışmaz.
- Ekranda aynı anda en fazla 3 öneri; aynı kategoriden en fazla 1 (üç öneri birden gramer olmasın).
- Önerilen çalışma için sitede o seviyede veya türde içerik yoksa öneri "dışarıda yap + sonucu gir" türüne döner (ör. resmi modelltest linki + sonuç girişi).
- Tüm eşikler ve süreler: Ek A.

### 5.4 Haftalık ve günlük plan

- Haftalık plan: haftalık toplam süre (günlük süre × haftalık gün) çalışma seviyesine göre türlere (dört beceri, gramer, kelime) dağıtılır; paylar seviye bazlı config'dedir. Hafta başında ve hedef veya ritim değişince yeniden hesaplanır; aktif koç önerileri dağılımı kaydırabilir.
- Günlük plan: haftalık plandan ve öncelikli önerilerden, içeriklerin tahmini süreleriyle (`estimatedMinutes`) günlük süreye sığdırılarak oluşturulur (ör. 20 dk Dativ, 15 dk kelime, 20 dk Hören).
- Plan öneridir, zorunlu değildir; "görevleri başlat" zorunluluğu yoktur.

### 5.5 Dil ve ton

Klişe motivasyon cümlesi minimumda; geri bildirim veriye dayalı olur: "Dativ testlerinde son 3 oturumda %60'ın altındasın", "Bu hafta henüz Schreiben girmedin".

### 5.6 Mimari

Koç motoru UI'dan bağımsız, saf fonksiyonlardan oluşan bir modüldür; ileride AI ile değiştirilebilecek bir arayüzü olur.

## 6. Aktivite kaydı (ürünün kalbi, hızlı olmalı)

### 6.1 Otomatik kayıt

Site içinde tamamlanan her çalışma (tekrar, gramer, artikel, okuma, yazma, dikte, test) aktivite geçmişine otomatik yazılır: tür, süre (ölçülen aktif süre; uzun boşluklar sayılmaz), kaynak "site", sonuç, oturum id.

### 6.2 "Bugün ne yaptın?" (dış çalışmalar için)

- Tek dokunuşlu hazır seçenekler: tür (Hören, Lesen, Schreiben, Sprechen, Gramer, Kelime), süre çipleri, kaynak türü (YouTube, kitap, podcast, kurs, öğretmen, AI, başka uygulama, kendi çalışmam), seviye; isteğe bağlı kısa not ve isteğe bağlı sonuç (ör. 8/10, 17/20).
- Sonuç girilince tek dokunuşlu ek alan açılır: "Sonuç nereden?" (resmi sınav / modelltest / öğretmen değerlendirmesi / uygulama veya kurs testi / kendi değerlendirmem). Güvenilirlik kademesi buradan belirlenir (4.4).
- Sprechen için: kiminle (öğretmen / arkadaş / AI / kendi başıma) ve konu.
- Gramer için: konu seçimi (gramer ağacından).
- Formda o gün sitede otomatik kaydedilen çalışmalar görünür; kullanıcı aynı şeyi ikinci kez girmez.
- Geriye dönük tarih girilebilir; kayıtlar düzenlenip silinebilir.

### 6.3 Kayıt ne zaman kanıt olur?

- Sadece dört beceride (Lesen, Hören, Schreiben, Sprechen), seviye alanı dolu ve sonuç (puan / azami puan) girilmişse.
- Gramer ve Kelime sonuçları öğrenme istatistiğine yazılır, kanıt olmaz (K2).
- Sadece süre olan kayıtlar çalışma geçmişidir.

## 7. Navigasyon ve ekranlar

Ana menü: 🏠 Ana Sayfa · 📚 Öğren · 🤖 Koçluk. Ayarlar/profil küçük bir menüde. Ayrı bir "İlerleme" sayfası yok. Üç ana sayfa görsel olarak birbirinden ayrışmalı.

🏠 Ana Sayfa (yeni ziyaretçi, onboarding tamamlanmamış): Hero "🇩🇪 %100 Ücretsiz Almanca Koçu", alt mesaj "A1'den C1'e kadar Almanca öğrenme sürecini planla, çalış ve takip et". Altına "%100 ücretsiz" vurgusu ve ürünün gerçek bir özelliği (ör. hesapsız modelde "Kayıt gerekmez · Verilerin cihazında kalır"). "Kredi kartı yok" veya "gizli ücret yok" gibi ücretli-SaaS dilinden kaçın.

🏠 Ana Sayfa (kullanıcı): hedef + mevcut durum (kısa), koçun bugünkü önerisi (en fazla 3), bugünkü önerilen plan, "Bugün ne yaptın?" alanı, son aktiviteler. "Çalışmaya Başla" gibi zorlayıcı bir ana buton YOK.

🤖 Koçluk:

- Hedef ve yol (ör. A1 → A2 → B1, şu an nerede)
- Başlangıç noktası: yerleştirme testi sonucu ve tarihi (beceri profilinden ayrı, küçük)
- "Dil becerilerin": Lesen/Hören/Schreiben/Sprechen, durum + güven + kanıt sayısı; genel tahmin (4.5)
- "Öğrenme aktivitelerin": kelime (çalışılan/güçlü/tekrar bekleyen), gramer (çalışılan/iyi/tekrar gereken), artikel başarısı (der/die/das), testler, tekrarlar
- Bu iki bölüm görsel olarak açıkça ayrı: "Ne kadar Almanca biliyorum?" vs "Ne kadar çalıştım?"
- Bu haftanın öncelikleri, tekrar bekleyenler, tekrarlayan hatalar
- Son 7 gün çalışma alışkanlığı (tür bazlı: dört beceri + gramer + kelime)
- "Sistem ne öneriyor?" (gerekçeli)
- Seviye testi, öz değerlendirme, hedef ve çalışma ritmi değiştirme buradan

📚 Öğren:

- 🧠 Kelimeler: yeni, tekrar (SRS), testler, artikel, çoğul, örnek cümleler
- 📖 Gramer: seviye bazlı konu ağacı; her konu kısa döngü: kısa açıklama → örnek → mini soru → cevap + hata açıklaması → yeni örnek → kontrollü üretim → mini test (öğren → uygula → hata yap → düzelt → tekrar uygula). Uzun ders sayfası yok.
- 🔤 Artikel: der/die/das pratiği, kendi istatistiği. Kelime verisini kullanır; ayrı artikel verisi tutulmaz.
- 📰 Lesen: metinler + sorular (otomatik puan, beceri kanıtı olur)
- 🎧 Hören: tarayıcının Web Speech API'si (de-DE) ile basit dinleme/dikte alıştırmaları (destekleniyorsa; cihazda Almanca ses yoksa bunu açıkça söyle). Bunlar öğrenme aktivitesidir, kanıt değildir (4.2). Ayrıca dış çalışma/sonuç kaydı ve kaynak önerileri.
- ✍️ Schreiben: yazma görevleri, metni girme, rubrikle öz değerlendirme, örnek cevap
- 🗣️ Sprechen: konuşma görevleri/konuları, Kann-Beschreibungen öz değerlendirmesi, dış pratik kaydı. Gerçek konuşma değerlendirmesi yapıyormuş gibi davranma.
- 🎯 Sınav: A1–C1, hedef seviye öne çıkar; dört beceri formatında pratik; resmi modelltestlere link + sonuç girişi. Telifli sınav materyali kopyalanmaz. Kanıt kuralları 4.2 ve 4.4'teki gibidir.

Ayrı bir "Testler" ana bölümü yok: öğrenme testleri ilgili modülün içinde, seviye testi Koçluk'ta, sınav pratiği Sınav'da.

## 8. Öğrenme modülleri detay

### 8.1 Kelimeler ve SRS

Kelime kaydı: Almanca, Türkçe anlam, artikel, çoğul, tür, seviye, örnek cümle + Türkçesi, tahmini süre, `verified`, SRS durumu. Pratik türleri: hatırlama, anlam seçme, artikel seçme, cümlede kullanma (boşluk doldurma). Kelimeler Web Speech API ile sesli okunabilir (destekleniyorsa).

SRS:

- Yeni kelime öğrenme oturumunda ilk kez çalışılan kelime, merdivenin ilk aralığıyla SRS'e girer. Aralık merdiveni config'dedir (Ek A).
- SRS durumunu tekrar oturumu değiştirir; diğer pratikler istatistiğe yazılır.
- Tekrar oturumunda kullanıcı "Bilemedim / Zorlandım / Bildim" işaretler: Bildim → bir üst aralık; Zorlandım → aynı aralık tekrar; Bilemedim → merdivenin başı.
- Kelime doğru ama artikel yanlışsa "Zorlandım" sayılır ve artikel hatası kaydedilir.
- "Güçlü kelime": aralığı eşik ve üstünde olan kelime (Ek A).
- Günlük yeni kelime limiti (günlük süreye göre) ve günlük tekrar limiti vardır. Birikmiş tekrarlar en gecikmişten başlayarak günlere yayılır.

SRS ve modül istatistikleri seviyeyi değiştirmez (K2).

### 8.2 Gramer

- Seviye bazlı konu ağacı. Her konu: kısa döngü (Bölüm 7), tahmini süre, `core` bayrağı, `verified`.
- Açıklamalarda uygun yerlerde Türkçeyle kısa karşılaştırma yap: Akkusativ ≈ -i hali, Dativ ≈ -e hali (birebir örtüşmediği notuyla), Türkçede artikel ve dil bilgisel cinsiyet olmaması, ana cümlede çekimli fiilin 2. sırada olması (Türkçede fiil sonda).
- Tamamlanan konu için kontrol mini testleri config aralıklarıyla "tekrar bekleyen" listesine düşer (Ek A).
- Serbest cümle üretimi AI olmadan puanlanamaz: kontrollü üretim (boşluk doldurma, kelime sıralama, seçmeli) kullan; serbest cümlede kullanıcı kendi cevabını örnek cevapla karşılaştırıp kendisi işaretler.

### 8.3 Cevap kontrolü

- Yazmalı sorularda ekranda ä ö ü ß (ve Ä Ö Ü) butonları.
- Sorular birden fazla kabul edilen cevap taşıyabilir (`acceptedAnswers`).
- Büyük/küçük harf farkı: varsayılan "kabul et + uyar"; ayrıca "yazım: büyük/küçük harf" hata etiketiyle kaydedilir (config'den katı mod seçilebilir).
- ä/ö/ü/ß yerine ae/oe/ue/ss yazımı: varsayılan "kabul et + uyar".
- Fazla boşluk ve noktalama farkı yok sayılır.
- Dikte: kelime bazlı karşılaştırma; yanlış kelimeler işaretlenir, puan doğru kelime oranıdır.

### 8.4 Hata hafızası

- Her soru konu etiketleri taşır (gramer konusu, hal, artikel türü vb.).
- Hata kaydı: tarih, oturum id, konu etiketi, kullanıcının cevabı, doğru cevap, tekrar sayısı, durum.
- Aynı etiket belirli sayıda farklı oturumda hatalıysa → "tekrarlayan hata".
- Durumlar: aktif → düzeliyor (sonraki denemelerde doğru gelmeye başladı) → çözüldü (art arda belirli sayıda doğru). Çözülen etiket yeniden hatalı olursa tekrar aktif olur. (Değerler: Ek A)

### 8.5 Test ayrımı

Öğrenme testleri (kelime/gramer/artikel) ile seviye/beceri testleri veri yapısında ve arayüzde ayrıdır. Öğrenme testi sonucu seviye kanıtı değildir.

## 9. Veri modeli ve mimari

### 9.1 Modüller ve tipler

Ayrı modüller/tipler: User, Goal, StudyRhythm (günlük süre, haftalık gün), LevelAssessment (yerleştirme testi, öz değerlendirme), SkillEvidence (Lesen/Hören/Schreiben/Sprechen), SkillProfile (türetilmiş), ActivityLog, Session, VocabularyItem, VocabularyProgress (SRS), GrammarTopic, GrammarProgress, Question (etiketli), TestResult (learning | assessment ayrı), ErrorRecord, WeeklyPlan, CoachRecommendation, Config (Ek A).

- İçerik (kelimeler, gramer, sorular, metinler, can-do ifadeleri, dış linkler) koddan ayrı, seviye bazlı bölünmüş JSON dosyalarında.
- Koç motoru, seviye hesaplama, SRS ve cevap kontrolü saf fonksiyonlar olarak ayrı modüllerde; UI'dan bağımsız.
- Zamana bağlı her fonksiyon bugünün tarihini parametre olarak alır, sistem saatini kendisi okumaz (birim testleri ve demo modu için şart).
- Tek devasa dosya yok (JS veya başka).
- AI entegrasyonu yok; ileride eklenebilecek bir arayüz bırak. API anahtarı frontend'e konmaz.

### 9.2 Kalıcılık ve hesap modeli (Faz 0 raporundan sonra ben karar vereceğim)

- A) Hesapsız: veriler cihazda (localStorage veya IndexedDB). "Giriş" yok; yeni ziyaretçi = onboarding'i tamamlamamış kullanıcı. JSON dışa/içe aktarma zorunlu.
- B) Hesaplı: backend + veritabanı + giriş. Mevcut yapı buysa korunur.

Hangisi seçilirse seçilsin:

- Depolama tek bir kalıcılık katmanının arkasında soyutlanır; seçim kodun geri kalanına sızmaz.
- İçerik JSON'ları depolamaya yazılmaz; yalnızca kullanıcı durumu yazılır.
- Veri şeması versiyonlanır, eski veriden migrasyon yazılır, migrasyondan önce otomatik yedek alınır.
- Kullanıcıya JSON dışa/içe aktarma ve sıfırlama sunulur.

## 10. İçerik kapsamı ve doğruluk

### 10.1 Kapsam

İlk sürümde A0–A2 içeriği kullanılabilir düzeyde tam olmalı. Minimum hedefler:

- A0 yolu: alfabe/telaffuz, selamlaşma, zamirler, sein/haben, temel kelimeler, cümle yapısı, sayılar, gün/saat, artikeller, temel fiiller
- Kelime: A0–A1 için en az 300, A2 için en az 300 ek kelime
- Gramer: A0–A1 için en az 15, A2 için en az 12 konu; her konuda farklı türlerde en az 15 soru
- Lesen: A1 ve A2'de en az 8'er metin, her metinde 4–6 soru
- Schreiben: A1 ve A2'de en az 5'er görev (rubrik + örnek cevap)
- Sprechen: A1 ve A2'de en az 6'şar konu; A1–C1 her seviye için 8–12 Kann-Beschreibung (Türkçe, sade, kendi ifadenle)
- Yerleştirme testi: A1–C1 her seviyede en az 10 soru
- B1–C1: yapı + her seviyede örnek içerik (en az 3 gramer konusu, 2 okuma metni, 50 kelime), genişletilebilir şekilde

Her içerik öğesi `estimatedMinutes` taşır.

### 10.2 Doğruluk

- Almanca içeriğin doğruluğu kritik. Üretilen tüm içerik `verified: false` taşır. `verified: true`'yu yalnızca ben işaretlerim; sen asla işaretleme.
- Otomatik içerik doğrulayıcıları yaz ve her içerik değişikliğinde çalıştır: şema uyumu; her sorunun tek doğru cevabı olması (veya `acceptedAnswers`); doğru cevabın seçenekler arasında olması; etiketlerin gramer ağacında bulunması; örnek cümlenin ilgili kelimeyi içermesi; artikel ve çoğul alanlarının biçimi; yinelenen kayıt olmaması; zorunlu alanların (Türkçe anlam, seviye, tahmini süre) dolu olması.
- İçerik üretimi ile içerik incelemesi ayrı oturumlarda yapılır. İnceleme oturumu her dosyayı baştan kontrol eder; düzeltmeleri ve şüpheli kayıtları `docs/content-review.md`'ye yazar. Emin olmadığın kayıtları ayrıca faz raporunda listele.
- Telifli materyal (Goethe modelltestleri, resmi tanımlayıcı metinleri vb.) kopyalanmaz; can-do ifadeleri kendi ifadenle yazılır, resmi materyale link verilir.
- Dış linkleri uydurma; emin olmadığın linkleri `verified: false` ile işaretle.

## 11. Tasarım

Modern, sade, güvenilir, profesyonel. Mobil öncelikli, masaüstünde de kaliteli. Fazla renkli/çocukça değil, kart çöplüğü değil. Mevcut işe yarayan görsel parçaları koru.

## 12. Test ve kabul

### 12.1 Birim testleri (Faz 1'den itibaren)

Seviye tahmini, güven durumu, yerleştirme algoritması, çalışma seviyesi, hedef tamamlama, koç kuralları, SRS, cevap kontrolü ve migrasyon için birim testleri ilgili fazda yazılır. Zamana bağlı testler tarihi parametre olarak verir.

### 12.2 Demo modu (Faz 4)

Test için örnek veri ayrı ve açıkça işaretli bir "demo modu"nda üretilir: ayrı depolama alanı (veya ayrı demo kullanıcısı), ekranda görünür "DEMO" etiketi, simüle edilmiş tarih ("zamanı ileri sar"). Gerçek kullanıcı verisine asla yazmaz.

### 12.3 Kabul senaryoları

Mantık kısmı ilgili fazda birim testi olarak yazılır; Faz 4'te demo modunda uçtan uca uygulanır ve raporlanır.

- Senaryo 1: A0 kullanıcı, hedef A1, günde 15 dk, haftada 5 gün — onboarding, ilk haftalık plan, ilk gün önerisi, 1 haftalık kayıt; site içi çalışmalar otomatik loglanıyor, çift kayıt oluşmuyor.
- Senaryo 2: Yerleştirme testinden A1 çıkan kullanıcı, hedef B1 — farklı oturumlarda Dativ hataları → tekrarlayan hata kaydı ve koçun Dativ tekrarı önermesi; ardından art arda doğrularla durumun "çözüldü"ye geçmesi.
- Senaryo 3: B2 kullanıcı, hedef C1 — Hören/Sprechen verisi yokken "veri yok" ve eksik genel tahmin gösterimi; C1 site içeriği olmayan yerde "dışarıda yap + sonucu gir" önerisi.
- Senaryo 4: Hedef A2'ye ulaşan kullanıcı — beceri bazlı güven dökümüyle tamamlama ekranı, B1'e otomatik itilmeme, hedef değişmezse pekiştirme önerileri.
- Senaryo 5: 5 gün çalışmayan kullanıcı — geri dönüş planı; birikmiş SRS tekrarlarının günlere yayılması.
- Senaryo 6: Testi atlayıp hedef A2 seçen kullanıcı sonra yerleştirme testinden B1 alıyor — bilgilendirme yapılıyor, hedef otomatik değişmiyor.

## Öncelik sırası

Bu sıra yapım sırası değildir; iki hedef çatıştığında hangisinin kazanacağını belirler:

Öğrenme mantığı → otomatik koçluk → kişiselleştirme → gerçek veriyle analiz → modüller → dört beceri takibi → basit UX → görsel tasarım

Not: Dört beceri modeli seviye tahmininin temelidir (Bölüm 4); bu sıralama onu kısmak için gerekçe olamaz.

## Ek A — Config varsayılanları

Hepsi başlangıç değeridir; config dosyasından okunur ve kolayca değiştirilebilir (K7).

| Ayar | Varsayılan |
|---|---|
| Geçme eşiği (kanıt kendi seviyesini destekler) | %60 |
| Güvenilirlik ağırlıkları (yüksek / orta / düşük) | 1,0 / 0,6 / 0,3 |
| `verified: false` içerikten gelen kanıt | bir kademe düşük (açık) |
| Tahminde kullanılan kanıtların azami yaşı | 12 ay |
| "Güvenilir" durumu | tahmin seviyesini destekleyen ≥2 pozitif kanıt; ≥1'i son 30 günde; ≥1'i orta/yüksek kademede |
| Genel tahmin yöntemi | medyan, aşağı yuvarlanır (alternatif: en zayıf beceri) |
| Genel tahmin için gereken verili beceri sayısı | 2 |
| Kann-Beschreibungen puanı | evet 1 · kısmen 0,5 · hayır 0 |
| Schreiben rubriği | 4 kriter × 0–3 puan |
| Yerleştirme testi | başlangıç A2 · blok 5 soru · geçme %60 · yukarı %80 · en fazla 25 soru |
| Test yapmamış "bir miktar biliyorum" kullanıcısının çalışma seviyesi | A1 |
| Yeniden değerlendirme önerisi | 45 gün veya 6 yeni kanıt (hangisi önce gelirse) |
| Hedef tamamlama: kanıt | her beceride hedefi destekleyen ≥2 pozitif kanıt; ≥1'i son 30 günde |
| Hedef tamamlama: minimum güven / temel modül şartı | "tahmini" / kapalı |
| Koç: ekrandaki öneri / kategori başına öneri | en fazla 3 / en fazla 1 |
| Gramer: başarı penceresi / zayıflık eşiği / pencerede min yanıt | son 20 yanıt / %70 / 10 |
| Artikel: tür başına pencere / eşik / min yanıt | son 20 yanıt / %70 / 10 |
| Tekrarlayan hata | aynı etiket 3 farklı oturumda |
| Hata "çözüldü" | art arda 3 doğru |
| Beceri "bir süredir çalışılmadı" | 7 gün |
| Geri dönüş planı | 3 gün hiç çalışmama |
| "Hedefe yakın" | en az 3 becerinin tahmini hedef seviyede |
| SRS aralıkları (gün) | 1 → 3 → 7 → 14 → 30 → 60 → 120 |
| "Güçlü kelime" | aralık ≥ 30 gün |
| Günlük yeni kelime limiti | 15 dk: 5 · 30 dk: 8 · 45 dk: 10 · 60 dk: 12 · 90+ dk: 15 |
| Günlük tekrar limiti | 50 |
| Gramer kontrol testleri | konu tamamlandıktan 7 ve 30 gün sonra |
| Haftalık plan payları, A0–A1 (%) | kelime 30 · gramer 30 · Hören 15 · Lesen 10 · Sprechen 10 · Schreiben 5 |
| Haftalık plan payları, A2–B1 (%) | kelime 20 · gramer 25 · Hören 15 · Lesen 15 · Sprechen 15 · Schreiben 10 |
| Haftalık plan payları, B2–C1 (%) | kelime 15 · gramer 15 · Hören 20 · Lesen 15 · Sprechen 20 · Schreiben 15 |
| Hafta başlangıcı | Pazartesi |
| Aktif süre ölçümü | iki etkileşim arasındaki 5 dk'dan uzun boşluk sayılmaz |
| Cevap kontrolü: büyük/küçük harf · ae/oe/ue/ss | kabul + uyarı · kabul + uyarı |
