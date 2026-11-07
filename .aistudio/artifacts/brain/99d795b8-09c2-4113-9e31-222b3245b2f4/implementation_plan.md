# FlexiList — Ekran Rotasyonu, Arka Plan / Yaşam Döngüsü ve Görsel İyileştirme Planı

Bu plan, uygulamanın ekranı yan çevrildiğinde (portrait -> landscape), arka plana atılıp geri dönüldüğünde (process recreation / lifecycle) veya küçük ekranlarda karşılaşılan görsel ve teknik sorunları kalıcı olarak çözmek için tasarlanmıştır.

---

## 1. Tespit Edilen Kritik Sorunlar ve Nedenleri

1. **Form State Kaybı (Rotasyonda Sıfırlanma)**:
   - `ItemDetailScreen` ve `NotebooksScreen` diyaloglarında kullanıcı başlık, açıklama, set sayısı veya tarih girerken telefon yan çevrildiğinde `remember` bellekten silindiği için tüm girilen metinler sıfırlanmaktadır.
   - `rememberSaveable` kullanılarak tüm form alanları (`title`, `description`, `priority`, `tags`, `subtasks`, `customFields`, `exerciseConfig`) Bundle/SavedStateRegistry üzerinde korunmalıdır.

2. **Yatay Ekranda (Landscape) Workout Player Düzeni**:
   - Dikey ekranda tek sütun dikey kaydırma çalışırken, yatay ekranda 450dp'lik kas anatomi visualizer canvas'ı ve büyük butonlar ekranı kaplamakta, zamanlayıcı veya egzersiz butonları aşağı taşmaktadır.
   - `BoxWithConstraints` ile ekran genişliği yüksekliğinden büyük olduğunda (`maxWidth > maxHeight`), sol tarafta **Anatomi Visualizer & Egzersiz Bilgisi**, sağ tarafta **Geri Sayım / Set Sayacı / Kontrol Butonları** yer alacak şekilde iki sütunlu modern yatay arayüz uygulanacaktır.

3. **Workout Player'da Sistem Geri Tuşu (BackHandler)**:
   - Kullanıcı aktif antrenmandayken telefonun geri tuşuna veya hareketine (system gesture) bastığında, kullanıcı yanlışlıkla ekranı terk edebilmektedir.
   - `BackHandler` entegre edilerek antrenmanı kapatmak isteyip istemediğini soran ya da güvenli şekilde oturumu koruyarak çıkan akış sağlanacaktır.

4. **Diyalogların Yatay Ekranda Sıkışması ve Taşması**:
   - `ThemeCustomizationDialog`, `WorkoutPlannerSettingsDialog`, `PriorityCustomizationDialog` ve `ListReminderDialog` yatay modda ekranın dikey yüksekliğini aşabilmektedir.
   - Tüm diyalog kartlarına `widthIn(max = 560.dp)`, `heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.9f)` ve iç Column'a `verticalScroll` güvencesi eklenecektir.

---

## 2. Mimari ve Bileşen Düzeni (Landscape & Responsive)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ Landscape Workout Player Screen (maxWidth > maxHeight)                      │
│                                                                             │
│ ┌────────────────────────────────────┬────────────────────────────────────┐ │
│ │ SOL SÜTUN: Anatomi & Egzersiz      │ SAĞ SÜTUN: Zamanlayıcı & Eylemler  │ │
│ │ • Üst Bar (Başlık, Adım Sayacı)    │ • Süre / Set / Ağırlık Göstergesi  │ │
│ │ • Kas Grubu Rozetleri              │ • Dev Dijital Sayaç / Rest Bilgisi │ │
│ │ • Fütüristik Cyberpunk Kas         │ • Dinamik Butonlar:                │ │
│ │   Görselleştirici (Canvas)         │   [ +15s Mola ] [ Molayı Geç ]     │ │
│ │                                    │   [ SETİ TAMAMLA ] / [ Durdur/Devam]│ │
│ └────────────────────────────────────┴────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Uygulanacak Adımlar

### Adım 1: Durum Koruma (State Preservation)
- `ItemDetailScreen.kt`:
  - `itemTitle`, `itemDescription`, `priority`, `tagsInput`, `itemCategory`, `starred`, `dueDate`, `reminderTime`, `isRecurring`, `recurrenceRule`, `exerciseType`, `setsCount`, `targetReps`, `targetTimeSeconds`, `restTimeSeconds`, `weightKg`, `supersetGroupId` durumları `rememberSaveable` olarak güncellenecek.
  - Subtask ve CustomField listeleri için `listSaver` tanımlanarak rotasyonda alt görevlerin silinmesi engellenecek.
- `NotebooksScreen.kt` ve `ItemsScreen.kt`:
  - Açık olan diyalog durumları (`showAddDialog`, `showWorkoutExecution`, `showReminderDialog`, vb.) `rememberSaveable` yapılarak yan çevirmede diyalogların kapanması önlenecek.

### Adım 2: Adaptif Yatay Workout Player
- `WorkoutPlayerScreen.kt`:
  - `BoxWithConstraints` ile ekran yönü algılanacak (`isLandscape = maxWidth > maxHeight`).
  - Dikeyde şık tek sütun, yatayda bölünmüş 2 sütunlu düzen render edilecek.
  - `MuscleGroupVisualizer` canvas boyutu yatay ekranda ekranı taşmayacak şekilde dinamik hesaplanacak.
  - `BackHandler` eklenerek antrenman sırasında onay almadan uygulamanın kazara kapanması engellenecek.

### Adım 3: Diyalogların Yükseklik ve Kaydırma Güvenceleri
- `NotebookSettingsDialogs.kt` ve `ThemeAndReminderDialogs.kt`:
  - Yatay modda klavye açıldığında veya ekran daraldığında butonların ekran dışına taşmasını engelleyen esnek `heightIn(max = ...)` ve `Modifier.imePadding()` optimizasyonları yapılacak.

### Adım 4: Test & Derleme Doğrulaması
- Robolectric ve unit testleri çalıştırılarak durum koruma mantığı ve ekran geçişleri doğrulanacak.
