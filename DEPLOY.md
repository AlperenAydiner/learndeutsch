# Yayına Alma

**Durum: yayında.** 20 Eylül 2026'da kuruldu.

| Parça | Adres |
|---|---|
| Site | https://learndeutsch-alpi7.vercel.app |
| API | https://ichsprechedeutsch-api.onrender.com |
| Veritabanı | Supabase — Alperen Proje (eu-central-1) |

Aşağıdaki adımlar sıfırdan kurulum içindir; bir şey bozulursa veya
ikinci bir ortam kurulacaksa buraya bakılır.

## Kurulum sırasında karşılaşılanlar

**Vercel Deployment Protection varsayılan olarak AÇIK geliyor.** Site
`vercel.com/sso-api`'ye yönlendirir ve yalnızca proje sahibi görebilir.
Settings → Deployment Protection → Vercel Authentication kapatılmalı.

**`learndeutsch.vercel.app` adresi başkasına ait.** Projenin gerçek
adresi takım adını içerir: `learndeutsch-alpi7.vercel.app`.

**Render ortam değişkeni düzenlerken** maskeli alanların DOM değeri boş
görünür; kaydetmeden önce göz simgesine basıp hepsini açmak güvenlidir.

---

## Sıra önemli

Frontend, backend'in adresini bilmek zorunda. O yüzden **önce backend**.

```
1. Render'a backend        → bir URL alırsın
2. O URL'i frontend'e yaz  → commit + push
3. Vercel'e frontend       → bir URL daha alırsın
4. İki URL'i Render ve Supabase ayarlarına yaz
```

---

## 1. Backend → Render

1. [render.com](https://render.com) → GitHub hesabınla giriş yap
2. **New → Blueprint** → `AlperenAydiner/learndeutsch` deposunu seç
3. Render depodaki `render.yaml` dosyasını okur ve servisi kurar

Sonra **Environment** sekmesinde şu değişkenleri gir. Değerler
`backend/.env` dosyanda duruyor:

| Değişken | Nereden |
|---|---|
| `DB_URL` | `backend/.env` içinden aynen kopyala |
| `DB_USERNAME` | aynı |
| `DB_PASSWORD` | aynı |
| `SUPABASE_JWKS_URI` | aynı |
| `FRONTEND_ORIGINS` | **şimdilik boş bırak** — Vercel adresini alınca dolduracaksın |

Deploy bitince `https://ichsprechedeutsch-api.onrender.com` gibi bir adres
alırsın. Doğrula:

```bash
curl https://ichsprechedeutsch-api.onrender.com/api/health
```

`{"status":"UP",...}` dönmeli.

> **Ücretsiz katman uyarısı:** Render 15 dakika hareketsizlikten sonra
> servisi uykuya alır. İlk istek ~50 saniye sürer. Bu normaldir, hata değil.

---

## 2. Frontend'e backend adresini yaz

`frontend/assets/js/core/config.js` içindeki satırı güncelle:

```js
const RENDER_API = "https://SENIN-ADRESIN.onrender.com/api";
```

Sonra:

```bash
git add frontend/assets/js/core/config.js
git commit -m "Canli API adresi"
git push
```

---

## 3. Frontend → Vercel

1. [vercel.com](https://vercel.com) → GitHub ile giriş
2. **Add New → Project** → `learndeutsch` deposunu seç
3. Ayarlar:
   - **Framework Preset:** Other
   - **Root Directory:** boş bırak (depo kökü)
   - Build command ve install command: **boş**

Depodaki `vercel.json` `outputDirectory: "frontend"` diyor, gerisini Vercel
halleder.

Deploy bitince `https://learndeutsch.vercel.app` gibi bir adres alırsın.

---

## 4. İki adresi ayarlara yaz

### Render

**Environment** sekmesinde:

```
FRONTEND_ORIGINS = https://learndeutsch.vercel.app
```

Birden fazla adres virgülle ayrılır (localhost'u da tutmak istersen):

```
FRONTEND_ORIGINS = https://learndeutsch.vercel.app,http://localhost:5500
```

Kaydettikten sonra Render servisi yeniden başlatır.

### Supabase

**Authentication → URL Configuration:**

- **Site URL:** `https://learndeutsch.vercel.app`
- **Redirect URLs** listesine ekle: `https://learndeutsch.vercel.app/**`

Localhost satırını **silme** — geliştirmeye devam edeceksin, ikisi de
listede durabilir.

---

## 5. Kontrol

Canlı adreste sırayla:

1. Kayıt ol → doğrulama e-postası gelmeli, bağlantı canlı siteye dönmeli
2. Giriş yap → `bugun.html` açılmalı
3. Hedef seç + yerleştirme testi
4. Program oluştur
5. Bir görevi işaretle

Beşi de çalışıyorsa yayın tamam.

---

## Sorun çıkarsa

**CORS hatası (konsolda "blocked by CORS policy")**
`FRONTEND_ORIGINS` yanlış. Adres tam olarak eşleşmeli: `https://` var mı,
sonda `/` yok değil mi?

**401 alıyorsun ama giriş yaptın**
`SUPABASE_JWKS_URI` eksik veya yanlış. Render log'unda
"Kimlik dogrulama reddedildi" satırı sebebi yazar.

**E-posta bağlantısı localhost'a götürüyor**
Supabase Site URL'i güncellenmemiş.

**İlk istek çok yavaş**
Render ücretsiz katmanı uykudan uyanıyor. Beklenen davranış.

**Veritabanı bağlantı hatası**
`DB_URL` session pooler adresi olmalı (port 5432), transaction pooler
(6543) değil. Transaction pooler prepared statement'ları kapatır ve
Hibernate onunla çalışmaz.

---

## Sonraki adım: kendi alan adın

`ichsprechedeutsch.com` alırsan:

1. Vercel → Project Settings → Domains → alan adını ekle
2. Render'da `FRONTEND_ORIGINS`'e yeni adresi ekle
3. Supabase'de Site URL ve Redirect URLs'i güncelle
