# FlexiList 📱✨

[![Android](https://img.shields.io/badge/Platform-Android-green.svg?logo=android)](https://www.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple.svg?logo=kotlin)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-blue.svg?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Package](https://img.shields.io/badge/Package-xyz.younglion.flexilist-informational.svg)](https://github.com/)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Release](https://img.shields.io/badge/Release-v1.0.0-brightgreen.svg)](releases/FlexiList-v1.0.0.apk)

**FlexiList**, Kotlin, Jetpack Compose ve SQLite tabanlı Room Veritabanı mimarisi kullanılarak geliştirilmiş; ultra hızlı, çevrimdışı öncelikli (offline-first), modern, modüler ve esnek bir çok amaçlı liste yönetim ve antrenman planlama uygulamasıdır.

Sıradan yapılacaklar (To-Do) listelerinden farklı olarak FlexiList; Yapılacaklar, Alışveriş, Alışkanlık Takibi ve gelişmiş **Workout Planner (Antrenman Asistanı)** modüllerini tek bir çatı altında birleştirir. Material Design 3 yönergelerine tam uyumlu, karanlık oda odaklı (*Distraction-Free Dark Slate Space*) şık ve ergonomik arayüzü ile eksiksiz bir kullanıcı deneyimi sunar.

---

## 🚀 Öne Çıkan Özellikler

### 1. 🏋️‍♂️ Gelişmiş Antrenman Asistanı (Workout Planner)
- **Akıllı DSL Ayrıştırma (Parsing)**: Egzersiz açıklamalarından Set, Tekrar/Süre, Ağırlık (kg), Mola (sn) ve Hedef Kas Gruplarını otomatik olarak görsel pill'lere (etiketlere) dönüştürür.
- **⚡ Süperset Desteği**: Birbiriyle bağlantılı egzersizleri otomatik gruplayan görsel süperset rozetleri.
- **Distraction-Free Tam Ekran Oynatıcı**:
  - 5 saniyelik hazırlık geri sayımı (Prep Countdown).
  - Süre tabanlı egzersizlerde otomatik sayaç; tekrar tabanlı egzersizlerde hızlı "SETİ TAMAMLA" butonu.
  - Cyberpunk esintili **Muscle Group Visualizer**: Çalıştırılan kas grupları görsel anatomik insan modeli üzerinde parlar.
  - Otomatik dinlenme molası (Rest State), "+15sn Mola Ekle" ve "Molayı Atla" seçenekleri.
  - O an hazır olunmayan hareketler için "Bunu Sonra Yap (Skip)" mantığı.
- **Periyot Yönetimi**:
  - **Günlük Programlar**: Haftanın günlerine özel antrenman akışı (Pzt, Sal, Çar...).
  - **Aylık Programlar**: 1-31 gün arası yatay kaydırılabilir (LazyRow) çip tasarımı.
- **Otomatik Senkronizasyon & Günlük**: Antrenman bittiğinde tüm liste veritabanında otomatik "tamamlandı" olarak işaretlenir; süre, set sayısı ve kaldırılan tonaj antrenman günlüğüne kaydedilir.

### 2. 📝 Çok Amaçlı Liste & Defter Yönetimi
- **Esnek Defter Modelleri**: Normal, Yapılacaklar, Alışveriş, Alışkanlık ve Workout türünde defterler.
- **Sürükle-Bırak & Toplu İşlemler**: Manuel liste sıralama (Drag & Drop) ve çoklu seçim (Bulk Mode) ile toplu silme/taşıma.
- **Özelleştirilebilir Öncelik Seviyeleri**: Özel renk paleti ve seviye isimleri tanımlayabilme (`PriorityCustomizationDialog`).
- **Gelişmiş Filtreleme**: Yıldızlama (Starring), öncelik rozetleri (badges) ve kategori/etiket filtreleri.

### 3. 🎨 Tema & Görsel Özelleştirme
- **El Yapımı Renk Paletleri**: Flexi Cyan, Neon Emerald, Electric Violet, Sunset Crimson, Cyber Gold ve Deep Ocean vurgu renkleri.
- **Koyu / Açık Tema**: Otomatik veya elle kontrol edilebilir göz dostu temalar.
- **Dokunsal Geri Bildirim**: İşlemlerde hassas titreşim (Haptic Feedback) motoru.

### 4. 🔔 Bildirimler, Sesler & Hatırlatıcılar
- **Sesli Geri Sayım**: Son 3 saniye tikleme sesi (Tick) ve bitiş gong sesi (Ding).
- **Yerel Hatırlatıcılar**: `AlarmManager` ve `NotificationManager` tabanlı bağımsız bildirim kurma sistemi.
- **Kilit Ekranı & Sistem Bildirimi**: Arka plan servisi ile kesintisiz antrenman ve hatırlatıcı takibi.

### 5. ☁️ Bulut Yedekleme & Paylaşım
- **SSL Korumalı Bulut Senkronizasyonu**: `JSONBlob` REST API entegrasyonu ile tek tıkla buluta yedekleme ve geri yükleme.
- **Güvenli Kod Üretimi**: Otomatik `FLX-XXXXXX` senkronizasyon kodları.
- **Flexi Dosya Paylaşımı**: `.flexi` uzantılı defter dosyalarını dışa aktarma ve içe aktarma (`ACTION_VIEW` & `ACTION_SEND` Intent desteği).

---

## 🛠️ Teknolojik Mimari & Kütüphaneler

- **Dil**: Kotlin (100%)
- **UI Framework**: Jetpack Compose + Material Design 3
- **Mimari**: MVVM (Model-View-ViewModel) + Clean Architecture prensipleri
- **Yerel Veritabanı**: Room Persistence Library + KSP
- **Asenkron İşlemler**: Kotlin Coroutines & Flow
- **Ağ & REST API**: Retrofit 2, OkHttp 4, Moshi Converter
- **Navigasyon**: Type-Safe Navigation Compose
- **Test**: JUnit 4, Robolectric, Roborazzi (Visual Regression Test)
- **Paket Adı (Application ID)**: `xyz.younglion.flexilist`

---

## 📥 İndirme ve Yükleme (APK)

Uygulamanın derlenmiş **v1.0.0** sürümünü doğrudan cihazınıza indirip kurabilirsiniz:

Direct Link: [`releases/FlexiList-v1.0.0.apk`](releases/FlexiList-v1.0.0.apk)

### Manuel Yükleme Adımları:
1. `FlexiList-v1.0.0.apk` dosyasını Android cihazınıza indirin.
2. Tarayıcı veya dosya yöneticisinden indirdiğiniz APK'ya dokunun.
3. İstenirse "Bilinmeyen Kaynaklardan Yükleme" iznini onaylayın.
4. Yüklemeyi tamamlayıp **FlexiList**'i kullanmaya başlayın!

---

## 💻 Projeyi Yerel Ortamda Derleme (Build Guide)

### Gereksinimler:
- **Android Studio**: Ladybug (2024.2.1+) veya üzeri
- **JDK**: OpenJDK 11 veya 17
- **Android SDK**: API level 36 (Min SDK: 24)

### Adımlar:

```bash
# 1. Depoyu klonlayın
git clone https://github.com/username/flexilist.git

# 2. Proje dizinine gidin
cd flexilist

# 3. Gradle ile Debug APK derleyin
./gradlew assembleDebug

# 4. Derlenen APK dosyasını bulabilirsiniz:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 📁 Proje Klasör Yapısı

```
FlexiList/
├── app/
│   ├── src/main/
│   │   ├── java/xyz/younglion/flexilist/
│   │   │   ├── data/
│   │   │   │   ├── backup/         # Cloud & File Backup yöneticileri
│   │   │   │   ├── db/             # Room DAO ve AppDatabase
│   │   │   │   ├── model/          # Entity verileri
│   │   │   │   ├── notifications/  # AlarmManager & Notification yöneticileri
│   │   │   │   └── repository/     # FlexiRepository
│   │   │   ├── ui/
│   │   │   │   ├── detail/         # Detay ekranları
│   │   │   │   ├── dialogs/        # Tema, Ayar & İstatistik Diyalogları
│   │   │   │   ├── items/          # Liste Öğeleri Ekranı
│   │   │   │   ├── notebooks/      # Defterler Ekranı
│   │   │   │   ├── theme/          # Color, Type, Theme M3 tanımları
│   │   │   │   └── workout/        # Workout Player & Kas Görselleştiricisi
│   │   │   └── util/               # Animasyon & Ağ Monitörü Araçları
│   │   ├── res/                    # Görsel, Ses, XML ve String Kaynakları
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts            # Application ID: xyz.younglion.flexilist
├── releases/
│   └── FlexiList-v1.0.0.apk        # Hazır Yükleme APK Dosyası
├── AGENTS.md                       # Detaylı Gelişim Raporu & Kod Kılavuzu
├── RELEASE.md                      # Sürüm Notları (v1.0.0)
├── LICENSE                         # GNU General Public License v3.0
├── CONTRIBUTING.md                 # Katkıda Bulunma Kılavuzu
└── SECURITY.md                     # Güvenlik Politikası
```

---

## 🤝 Katkıda Bulunma (Contributing)

Açık kaynak topluluğunun katkılarını içtenlikle karşılıyoruz!
Katkıda bulunmak istiyorsanız lütfen önce [CONTRIBUTING.md](CONTRIBUTING.md) dosyasını inceleyin.

1. Projeyi çatallayın (**Fork**).
2. Yeni bir özellik dalı oluşturun (`git checkout -b feature/harika-ozellik`).
3. Değişikliklerinizi işleyin (`git commit -m 'feat: Yeni özellik eklendi'`).
4. Dalınıza itin (`git push origin feature/harika-ozellik`).
5. Bir **Pull Request (PR)** açın.

---

## 📜 Lisans

Bu proje **GNU General Public License v3.0 (GPLv3)** ile lisanslanmıştır. Detaylı bilgi için [LICENSE](LICENSE) dosyasını inceleyebilirsiniz.

---

<p align="center">
  <b>FlexiList</b> — Ultra Hızlı, Özgür ve Esnek Liste & Antrenman Asistanı 🚀
</p>
