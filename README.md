# Asistans – Yapay zekâ ile arama yanıtlayan Android uygulaması

Gelen aramayı telefondaki uygulama açar, hoparlöre alır ve Claude ile Türkçe konuşur.
Görüşme bitince Claude kısa bir özet çıkarır, özet uygulamada listelenir.

## Kurulum (yaklaşık 10 dk)
1. **Android Studio**'yu kur (ücretsiz).
2. Bu klasörü aç: *File → Open → Asistans*. Gradle sürümünü sorarsa önerileni kabul et.
3. Telefonda *Geliştirici seçenekleri → USB hata ayıklama*yı aç ve telefonu bilgisayara bağla.
4. ▶ **Run** butonuna bas. Ayrıca *Build → Build APK(s)* ile APK da alabilirsin.
5. Uygulamada sırasıyla:
   - **Varsayılan telefon uygulaması yap**. Bu zorunlu: Android, aramayı açma ve ses yönlendirme yetkisini yalnızca varsayılan telefon uygulamasına verir.
   - **İzinleri ver**.
   - **Arama sesi erişimini aç**: Ayarlar → Erişilebilirlik → *Asistans arama sesi* → Aç. Android, görüşme sırasında mikrofonu yalnızca erişilebilirlik hizmetlerine verir; bu açık olmazsa asistan arayanı duyamaz (Android 13 ve üstü). Seçenek gri görünüyorsa: Ayarlar → Uygulamalar → Asistans → sağ üst ⋮ → *Kısıtlanmış ayarlara izin ver*.
   - Claude API anahtarını gir (console.anthropic.com → API Keys), adını yaz, **Kaydet**.
   - "Gelen aramaları asistan otomatik açsın" seçeneğini aç.
   - **API anahtarını test et** ve **Türkçe sesi test et** düğmeleriyle ayarları kontrol et.

> GitHub'dan indirilen APK'lar her derlemede farklı bir anahtarla imzalanır. Yeni sürümü kurmadan önce eskisini kaldırın, yoksa "Uygulama yüklenmedi" hatası alırsınız.

## Nasıl çalışır
Gelen arama → X saniye sonra açılır → hoparlöre alınır → TTS ile karşılama yapılır → arayanın sesi Google konuşma tanıma ile yazıya çevrilir → Claude'a gönderilir → cevap TTS ile okunur → bu döngü devam eder.
Arama ekranından istediğin an **Görüşmeyi devral** ile konuşmayı kendine alabilirsin.

## Gizlilik
- Rehber verisi cihazdan çıkmaz. Arayanın rehberdeki adı yalnızca ayarlardaki ilgili seçenek açıksa Claude'a gönderilir (varsayılan: kapalı).
- Görüşme metni ve özet sadece telefonda saklanır. Görüşme sırasında konuşmalar Anthropic API'ye gider.
- API anahtarı uygulamanın özel alanında saklanır.

## Bilinen sınırlamalar
- Android, üçüncü taraf uygulamaların arama sesine doğrudan erişmesine izin vermez. Bu yüzden uygulama **hoparlör yöntemini** kullanır: karşı tarafın sesi hoparlörden çıkar, mikrofon bunu duyar; asistanın sesi de hoparlörden çıkar ve aramaya karışır.
  - Ses kalitesi ve yankı iptali cihaza göre değişir. Bazı telefonlarda (özellikle bazı Samsung/Xiaomi modelleri) arama sırasında konuşma tanıma mikrofonu alamayabilir.
  - Sessiz bir ortamda en iyi sonucu verir.
- Konuşma tanıma için Google uygulaması ve Türkçe ses paketi gerekir. Türkçe TTS sesinin de yüklü olması gerekir (*Ayarlar → Metin okuma*).
- Daha profesyonel ve kesin çalışan bir alternatif: bulut telefon numarası (ör. Twilio) ile doğrudan ses akışı kullanmak. Bu kurulum ayrı bir sunucu gerektirir.

## Dosyalar
- `AssistantInCallService.kt` – sistemin aramaları bildirdiği servis
- `CallManager.kt` – arama durumu, otomatik açma, hoparlör
- `AiConversation.kt` – konuş → dinle → Claude → konuş döngüsü, sistem talimatı
- `ClaudeClient.kt` – Anthropic Messages API
- `MainActivity.kt` – ayarlar ve görüşme kayıtları
- `CallActivity.kt` / `DialerActivity.kt` – arama ekranı ve tuş takımı
