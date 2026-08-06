# 🤝 Katkıda Bulunma Kılavuzu (Contributing Guidelines)

**FlexiList** açık kaynaklı bir projedir ve topluluktan gelen her türlü katkıyı memnuniyetle karşılıyoruz!

---

## 🚀 Katkıda Bulunma Yolları

1. **Hata Bildirimi (Bug Report)**: Karşılaştığınız hataları ayrıntılı bir şekilde [Issues](https://github.com/username/flexilist/issues) sayfasında bildirebilirsiniz.
2. **Yeni Özellik Önerisi (Feature Request)**: Eklenmesini istediğiniz mantıklı özellikleri öneri olarak açabilirsiniz.
3. **Kod Katkısı (Pull Request)**: Yeni bir özellik geliştirebilir veya mevcut hataları düzeltebilirsiniz.

---

## 🛠️ Kodlama Kuralları ve Standartlar

- **Dil**: Tüm yeni geliştirmeler **Kotlin** dili ve **Jetpack Compose** kullanılarak yazılmalıdır.
- **Mimari**: Proje MVVM ve Clean Architecture prensiplerini takip eder.
- **Biçimlendirme**: Kotlin kod biçimlendirme kurallarına (`ktlint` / Android Studio varsayılan stili) uyunuz.
- **Derleme**: Gönderdiğiniz kodların sorunsuz derlendiğinden ve testleri geçtiğinden emin olunuz (`./gradlew testDebugUnitTest`).

---

## 🔄 Pull Request (PR) Süreci

1. Repoyu kendi hesabınıza çatallayın (**Fork**).
2. Ana daldan (`main`) yeni bir geliştirme dalı açın:
   ```bash
   git checkout -b feature/yeni-ozellik
   ```
3. Değişikliklerinizi yapın ve açıklayıcı commit mesajları yazın:
   ```bash
   git commit -m "feat: Antrenman sayacına yeni ses efekti eklendi"
   ```
4. Değişikliklerinizi kendi reponuza itin:
   ```bash
   git push origin feature/yeni-ozellik
   ```
5. Ana depoya yönelik bir **Pull Request (PR)** açın ve şablonu eksiksiz doldurun.

---

## 📜 Lisans Bilgisi

Projeye yaptığınız tüm katkıların **GNU General Public License v3.0 (GPLv3)** lisansı altında lisanslanacağını kabul etmiş olursunuz.
