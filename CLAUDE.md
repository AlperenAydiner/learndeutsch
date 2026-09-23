# Ich spreche Deutsch — çalışma talimatları

Ürün: %100 ücretsiz, kural tabanlı, otomatik Almanca koçu. Arayüz Türkçe, öğrenilen dil Almanca.
Tam şartname: `docs/SPEC.md`.

## Her oturumun başında

`docs/SPEC.md`, `docs/DECISIONS.md` ve `docs/PROGRESS.md` dosyalarını oku. Kaldığın faz ve açık sorular PROGRESS'te.

## Değişmez kurallar (SPEC Bölüm 2 — tüm kararlarda geçerli)

- **K1.** Gerçek dil seviyesi SADECE dört beceriden oluşur: Lesen, Hören, Schreiben, Sprechen.
- **K2.** Kelime, gramer, artikel, öğrenme testleri ve tekrarlar "öğrenme aktivitesi"dir. Seviyeye ASLA doğrudan çevrilmez ("500 kelime = %10 seviye" yok). Ama koçun kararlarını etkiler.
- **K3.** Veri yoksa sonuç üretme. "Henüz yeterli veri yok" göster. Sahte yüzde, sahte analiz, sahte "koçun seni analiz ediyor" yazısı yok. Her tahmin ve her koç önerisi gerçek veriye dayanır ve hangi veriye, hangi kurala dayandığını gösterebilir.
- **K4.** Sistem olmayan bir yeteneği varmış gibi göstermez. Gerçek AI entegrasyonu yok; koçluk kural tabanlıdır.
- **K5.** Hiçbir sonuç resmi CEFR/Goethe değerlendirmesi gibi sunulmaz. Dil: "Platform içi değerlendirmeye göre ... civarında". Belirsizlik varsa göster.
- **K6.** Kullanıcı hedefini kendisi seçer. Hedefe ulaşınca sistem onu otomatik olarak üst seviyeye itmez; kullanıcı isterse hedefini değiştirir.
- **K7.** Eşik, aralık ve limitler config varsayılanıdır (SPEC Ek A): koda sabit değer olarak yazılmaz, config'den okunur. Bölüm 10'daki içerik miktarları kapsam hedefidir; onboarding seçenekleri arayüz seçenekleridir.

## Çalışma şekli

- **Faz döngüsü:** fazın başında alt görev listesini çıkar ve onay bekle → uygula, saf fonksiyonların birim testlerini aynı fazda yaz → fazın sonunda dur, özetle, `docs/DECISIONS.md` ve `docs/PROGRESS.md`'yi güncelle, onay bekle.
- Testler geçmeden ve build/konsol hatasız olmadan faz bitmiş sayılmaz.
- Bir ürün kararında emin değilsen tahmin yürütme: seçenekleri ve önerini yazıp sor.
- Mevcut teknoloji yığınını koru (Spring Boot + Supabase + vanilla HTML/CSS/JS); değiştirmek gerekiyorsa önce gerekçesiyle sor.
- Zamana bağlı her fonksiyon bugünün tarihini parametre olarak alır; sistem saatini kendisi okumaz.
- İçerik (kelime, gramer, soru, metin, can-do, link) koddan ayrı, seviye bazlı JSON dosyalarındadır ve veritabanına yazılmaz; veritabanı yalnız kullanıcı durumunu tutar.

## İçerik doğruluğu

- Üretilen tüm içerik `verified: false` taşır. **`verified: true`'yu asla işaretleme** — yalnız kullanıcı işaretler.
- İçerik üretimi ve incelemesi ayrı oturumlarda yapılır; inceleme bulguları `docs/content-review.md`'ye yazılır.
- Telifli materyal (Goethe modelltestleri, resmi tanımlayıcılar) kopyalanmaz; can-do ifadeleri kendi ifadenle yazılır. Dış link uydurma; emin olmadığın linki `verified: false` ile işaretle.

## Yerel çalışma ve yayın

- Değişiklikleri yerelde yap ve yerelde doğrula (`SITEYI-AC.bat` → ön yüz 5500, backend 8080). **Commit, push ve deploy yalnız kullanıcı "publish" deyince**, hepsi birlikte.
- Tek Supabase projesi: **geliştirme** (`ichsprechedeutsch-dev`, `lokxrmomepycydvxcsfq`, `backend/.env.dev`). Eski canlı proje silindi (K-009); canlı site publish gününe kadar kapalı. Yerelde `tools/backend.cmd` `.env.dev` ile dev veritabanına bağlanır; ön yüz `localhost`'ta dev Auth'unu kullanır (`core/config.js`). `backend/.env` silinmiş projeyi gösterir, kullanılmaz.
- **Yerel veritabanı (K-030):** Docker'da `isd-postgres` (Postgres 16, port 54329), ayarlar `backend/.env.local`. `tools/backend.cmd` önce bunu kullanır. Entegrasyon testleri: `IT_DB=local ./gradlew test` (Supabase dev için `IT_DB=dev`). Bu ağda Supabase pooler'ına TLS geçmiyor; yerel veritabanı varsayılan.
- **Docker Desktop açılmıyorsa** ("Sistem dosyaya erişemiyor", `dockerInference` / `engine.sock`): kirli kapanıştan kalan Unix soketleri silinemiyor. Docker'ı kapat, `%LOCALAPPDATA%\Docker\run` ve `%LOCALAPPDATA%\docker-secrets-engine` klasörlerini yeniden adlandır (silme), `wsl --shutdown`, Docker'ı aç.
- Gizli bilgiler (`.env`, `.env.*`, `gradle.properties`) repoya girmez.
