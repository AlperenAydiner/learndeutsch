# İçerik Kaynakları ve Lisans

## Kelime listeleri (`words_a1.csv`, `words_a2.csv`)

**Türkçe anlamlar, örnek cümleler ve çevirileri bu proje için özgün olarak
yazılmıştır.** Hiçbir ticari kaynaktan, ders kitabından veya sınav
materyalinden kopyalanmamıştır.

**Biçimbilimsel veriler** (isimlerin artikeli ve çoğul biçimi) Almanca
Wiktionary ile karşılaştırılarak doğrulanmıştır:

- Kaynak: https://de.wiktionary.org
- Lisans: [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/)
- Kullanım: yalnızca doğrulama. Bir ismin cinsiyeti ve çoğul biçimi
  dilbilgisel bir olgudur; Wiktionary'nin metinleri kopyalanmamıştır.

Doğrulama sonucu: 136 ismin 135'i uyumlu çıktı, bir hata (`Wasser`)
düzeltildi. Wiktionary'nin listelediği ancak pratikte kullanılmayan
çoğul biçimleri (`Milche`, `Kleidungen` gibi) A1/A2 seviyesi için
bilinçli olarak alınmamıştır.

## İçerik birimleri (`content_units.csv`)

Goethe-Zertifikat A2'nin **kapsamı** referans alınmıştır: hangi gramer
konularının ve kelime alanlarının bu seviyeye ait olduğu. Goethe'nin
resmî kelime listeleri, sınav materyalleri veya metinleri
kopyalanmamıştır.

## Neden böyle

Telifli bir derlemeden kopyalamak, kaynak gösterilse bile telif ihlalidir.
Atıf, açık lisanslı içerik için gereken şeydir; telifli içeriği serbest
hale getirmez. Bu yüzden:

- olgusal veri (cinsiyet, çoğul) → açık lisanslı kaynakla doğrulanır
- yaratıcı içerik (örnek cümle, çeviri, açıklama) → özgün yazılır
- seviye kapsamı (hangi konu hangi seviyede) → referans olarak bakılır

Her `word` ve `reading_passage` satırı veritabanında `source` ve
`license` alanlarını taşır; içerik büyüdükçe kaynak takibi veride kalır.
