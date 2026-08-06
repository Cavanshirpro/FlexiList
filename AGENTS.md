# FlexiList — Proje Özeti ve Gelişim Takip Raporu

Bu dosya, **FlexiList** projesinin mevcut durumunu, antrenman (workout) sisteminin işleyişini, yapılan geliştirmeleri ve gelecek planlarını detaylandırmak üzere hazırlanmış resmi bir kılavuzdur.

---

## 1. Proje Özeti

**FlexiList**, Kotlin, Jetpack Compose ve SQLite tabanlı Room Veritabanı mimarisi kullanılarak geliştirilmiş, ultra hızlı, çevrimdışı öncelikli (offline-first), modern ve esnek bir çok amaçlı liste yönetim uygulamasıdır. 

Uygulama, sıradan yapılacaklar listelerinden farklı olarak dinamik liste türlerini (ToDo, Alışveriş, Alışkanlık Takibi ve Antrenman Planlayıcı gibi) tek bir çatı altında birleştirir. Material Design 3 tasarım yönergelerine tam uyumlu, karanlık oda odaklı (Distraction-Free Dark Slate Space) temasıyla hem estetik hem de ergonomik bir kullanıcı deneyimi sunar.

---

## 2. Antrenman (Workout) Sistemi Detaylı Anlatımı

FlexiList içindeki en güçlü modüllerden biri, esnek egzersiz listelerini tam teşekküllü bir antrenman asistanına dönüştüren **Workout Planner** sistemidir.

### Egzersiz Yapılandırması ve Ayrıştırma (Parsing)
Antrenman listelerindeki her bir öğe, açıklama kısmında gizli bir DSL (Domain Specific Language) formatında egzersiz yapılandırma verilerini taşır. Bu veriler anında ayrıştırılarak UI'da görsel pill'lere (etiketlere) dönüştürülür:
- **Set Sayısı**: Kaç set yapılacağını belirtir.
- **Egzersiz Türü (Reps / Timer)**: Egzersizin tekrar tabanlı (ör. 10 Tekrar) veya süre tabanlı (ör. 60 saniye) olduğunu belirler.
- **Ağırlık (kg)**: Egzersizde kullanılacak hedef ağırlığı gösterir.
- **Dinlenme Süresi (sn)**: Setler arasında yapılacak mola süresini tanımlar.
- **Hedef Kas Grupları**: Göğüs, Sırt, Omuz, Kollar, Bacaklar, Karın gibi kas gruplarını dinamik ve renk kodlu etiketlerle listeler.
- **Süperset Desteği**: Aynı gruba ait egzersizleri birbirine bağlayan "⚡ SÜPERSET" etiketiyle görsel gruplama sunar.

### Antrenman Oynatıcı Ekranı (Workout Player Overlay)
Antrenman başlatıldığında, dikkati dağıtacak tüm dış unsurları gizleyen tam ekran bir **Distraction-Free** oynatıcı devreye girer:
- **Hazırlık Geri Sayımı (Prep State)**: Egzersize başlamadan önce kullanıcıya 5 saniyelik bir konsantrasyon süresi verir.
- **Aktif Egzersiz Ekranı**:
  - Süre tabanlı egzersizlerde otomatik geri sayım sayacı çalışır.
  - Tekrar tabanlı egzersizlerde net ve anlaşılır "SETİ TAMAMLA" butonu gösterilir.
  - Cyberpunk esintili dinamik bir insan anatomisi simülatörü (**Muscle Group Visualizer**) üzerinde çalıştırılan kas grupları görsel olarak parlar.
- **Dinlenme Ekranı (Rest State)**:
  - Set tamamlandığında otomatik olarak başlar.
  - "+15sn Ekle" seçeneği ile mola süresi esnetilebilir.
  - **Molayı Atla** butonu ile mola doğrudan sonlandırılarak bir sonraki aşamaya geçilir.
- **Bunu Sonra Yap (Skip) Mantığı**: Başlama ekranında o an hazır olunmayan bir egzersiz antrenmanın en sonuna ertelenebilir.
- **Otomatik Durum Senkronizasyonu**: Antrenman bittiğinde, o antrenmana ait tüm egzersiz listesi veritabanında otomatik olarak **"tamamlandı"** işaretlenir ve bu durum ana ekrandaki listeye anında yansır.
- **Antrenman Günlüğü**: Tamamlanan tüm antrenmanlar; süre, yapılan set sayısı, kaldırılan toplam tonaj ve istatistiklerle yerel veritabanına kaydedilir.

---

## 3. Gelişim Takip Listesi (To-Do List)

Aşağıdaki liste, FlexiList projesinin başlangıcından bugüne kadarki gelişim adımlarını, eksiklerini ve gelecek planlarını listeler.

### [A] Notebook ve Temel Liste Yönetimi
- `[+]` Room veritabanı mimarisi ve veri tablosu şemalarının oluşturulması
- `[+]` Farklı liste türlerine (Normal, ToDo, Workout vb.) göre notebook oluşturabilme
- `[+]` Sürükle-bırak yöntemiyle manuel liste sıralama (Drag & Drop) ve animasyonlar
- `[+]` Çoklu seçim (Bulk Mode) ve toplu silme/taşıma işlemleri
- `[+]` Yıldızlama (Starring) ve öncelik seviyelerine göre filtreleme
- `[+]` Kategori ve etiket (Tag) yönetimi

### [B] Workout Planlayıcı ve Egzersiz Yapılandırması
- `[+]` Egzersiz açıklama metinlerinden (Description) parametre ayrıştırma motoru
- `[+]` Egzersiz kartlarında özelleştirilmiş parametre pill tasarımları (Set, Tekrar/Süre, Mola, Ağırlık)
- `[+]` Hedef kas grupları için otomatik renk kodlu etiket tasarımı
- `[+]` Süperset (Superset) görsel bağlantı etiketleri ve ayrıştırması
- `[+]` **[Geliştirildi]** Antrenman periyodu "Günlük" seçildiğinde haftanın günleri yerine başlangıçtan beri kaçıncı günde olunduğunu ve yapılan antrenman gün sayısını gösteren ilerleme çubuklu (Progress Bar) sayaç kartı gösterimi
- `[+]` **[Geliştirildi]** Antrenman periyodu "Aylık" seçildiğinde ayın günlerinin (1..31) yatay kaydırılabilir (LazyRow) çip tasarımıyla sorunsuz gösterimi
- `[+]` Günlük filtreleme sistemi (Pzt, Sal, Çar vb. günlere göre antrenman programı gösterme)

### [C] Workout Oynatıcı ve Akış Yönetimi
- `[+]` Tam ekran Distraction-Free antrenman oynatıcı arayüzü
- `[+]` Egzersiz kas grubu görselleştiricisi (Muscle Group Visualizer) tuval çizimleri
- `[+]` 5 saniyelik başlangıç hazırlık geri sayımı (Prep Countdown) ve atlama seçeneği
- `[+]` Süre tabanlı egzersizler için otomatik countdown mekanizması
- `[+]` Tekrar tabanlı egzersizlerde "Seti Tamamla" butonu
- `[+]` Set aralarında otomatik dinlenme molası (Rest State) tetiklenmesi
- `[+]` Dinlenme molasını esnetmek için "+15sn Ekle" seçeneği
- `[+]` **[Geliştirildi]** Antrenman başlatıldıktan sonra çıkış butonuna basıldığında veya telefon kapanıp açıldığında durumun kalıcı olarak (SharedPreferences) saklanması, kaldığı yerden "Devam Et" butonu ile sürdürülmesi
- `[+]` **[Geliştirildi]** Antrenman filtre çiplerinden "Hepsi" seçeneğinin kaldırılması ve doğrudan haftanın günlerine odaklanılması
- `[+]` **[Geliştirildi]** Workout Planner defterlerinden "Defter Analizi" düğmesinin kaldırılması
- `[+]` **[Geliştirildi]** Defter oluşturma/düzenleme pencerelerinde ("Aylık" seçimi vb.) diyalog boyutu sabitlemesi ve dikey kaydırma (vertical scroll) entegrasyonu
- `[+]` **[Geliştirildi]** Sadece bugünün gününe ait antrenman programının başlatılabilmesi; diğer günlerde başlat butonunun pasifleştirilmesi ve durum bilgilendirmesi yapılması
- `[+]` **[Geliştirildi]** Antrenman tamamlandığında "Başla" butonunun "Tamamlandı ✓" durumuna geçerek pasifleşmesi
- `[+]` **[Geliştirildi]** Gün değiştiğinde geçmiş güne ait tamamlandı işaretlerinin otomatik sıfırlanması
- `[+]` **[Geliştirildi]** Antrenman başlatıldığında yüzen ekleme butonunun (+ FAB) gizlenmesi
- `[+]` **[Geliştirildi]** Workout kartlarında tamamlama oranı bileşenlerinin ve arama altı öncelik filtrelerinin kaldırılması
- `[+]` **[Geliştirildi]** Antrenman başlatma kartının modern ve yüksek kontrastlı GUI tasarımı ile yenilenmesi

### [D] Ses, Sistem Bildirimleri ve Otomasyon
- `[+]` Geri sayımlarda son 3 saniye tikleme sesi (Tick) ve bitiş gong sesi (Ding) entegrasyonu
- `[+]` Arka planda veya ekran kilitliyken takip edilebilmesi için sistem bildirim çubuğu entegrasyonu
- `[+]` **[Eklendi]** Defter ve antrenman listeleri için `AlarmManager` ve `NotificationManager` tabanlı yerel bildirim/hatırlatıcı (Local Notification / Reminder) kurma sistemi
- `[+]` **[Geliştirildi]** Antrenman bittiğinde tüm antrenman egzersizlerinin veritabanında otomatik olarak tamamlandı durumuna geçirilmesi
- `[+]` **[Geliştirildi]** Antrenman tamamlandığında ana listedeki egzersizlerin anında tamamlandı olarak işaretlenip arayüze gecikmesiz yansıması

### [E] Raporlama, İstatistikler ve Günlük Kayıtları
- `[+]` Antrenman raporlarının (süre, tamamlanan set, toplam kaldırılan ağırlık) detaylı analitik json olarak kaydedilmesi
- `[+]` Geçmiş raporların listelendiği Rapor Sayfası arayüzü ve rapor silme desteği
- `[+]` Antrenman istatistikleri için görsel grafikler ve kas gelişim haritası çizimleri
- `[+]` Haftalık ve aylık kaldırılan toplam tonaj grafikleri ve gelişim raporları
- `[+]` Defter İstatistik ve Analiz Paneli (`NotebookStatisticsDialog`): Halkalı Tamamlama Oranı (Donut Gauge) grafiği ve Öncelik/Renk Dağılım çubuğu

### [F] Genel GUI, Hata Fixleri ve Optimizasyonlar
- `[+]` Dinlenme ekranındaki "Sıradaki Set" bilgisinin gerçek sonraki seti doğru göstermesi
- `[+]` Edge-to-Edge ekran tasarımı ve Android 12+ dinamik renk uyumluluğu
- `[+]` **[Eklendi]** Gelişmiş Tema & Vurgu Rengi Özelleştirme Ayarları (`ThemeCustomizationDialog`): Koyu/Açık tema geçişi ve el yapımı renk paleti seçenekleri (Flexi Cyan, Neon Emerald, Electric Violet, Sunset Crimson, Cyber Gold, Deep Ocean)
- `[+]` Özelleştirilebilir Öncelik Seviyeleri (Priority Customization Dialog): Dinamik öncelik ekleme, silme, derece ismi ve renk paleti belirleme
- `[+]` Workout Planlayıcı defterlerinden gereksiz Öncelik Seviyesi Ayarları düğmesinin kaldırılması (Ana liste ve Defter Ayarları)
- `[+]` Liste Kartlarında Renkli Öncelik Görselleştirmesi: Öncelikli öğelerde özel arka plan tonlaması, sol dikey renk çubuğu ve öncelik rozeti (badge) gösterimi
- `[+]` Öğe Ekleme/Düzenleme Ekranlarında Yatay Kaydırma (Horizontal Scroll): Dar ekranlarda öncelik çiplerinin taşmasını engelleyen UX düzeltmesi
- `[+]` Dokunsal Geri Bildirim Entegrasyonu (Haptic Feedback): Öğe tamamlama, yıldızlama ve seçme işlemlerinde `LocalHapticFeedback` titreşim geribildirimi
- `[+]` Tüm Derleme ve Lint İkazlarının (Deprecation Warnings) Temizlenmesi
- `[ ]` Tamamlanan egzersizlerin otomatik olarak listenin en altına taşınması animasyonu optimizasyonu
- `[ ]` Arka plan servislerinin daha kararlı çalışması için WakeLock iyileştirmeleri

### [G] Bulut Depolama, Yedekleme ve Senkronizasyon
- `[+]` Sıfır konfigürasyonlu, kullanıcı dostu Bulut Yedekleme ve Senkronizasyon mimarisi
- `[+]` `CloudSyncManager` üzerinden `kvdb.io` REST API entegrasyonu (OkHttp tabanlı)
- `[+]` Her yedek için otomatik ve rastgele üretilebilen yüksek güvenlikli senkronizasyon kodları (`FLX-XXXXXX`)
- `[+]` Buluttan tek dokunuşla geri yükleme (Download & Restore) ve yerel SQLite veritabanı ile otomatik veri birleştirme
- `[+]` AndroidManifest.xml'de güvenli `INTERNET` izinlerinin yapılandırılması
- `[+]` Yedekleme ve geri yükleme durumları için görsel yükleme göstergeleri (Progress Indicators) ve tost bildirimleri


---

*Not: FlexiList gelişim süreci bu dosya üzerinden anlık olarak güncellenecektir.*
