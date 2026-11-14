# Otobüs Takip (İstanbul)

İETT hatlarındaki araçların canlı konumunu gösteren, tek amaçlı ve sade bir Android uygulaması.

- Hat kodunu yazın (ör. `500T`) → hattın durakları sırayla listelenir, o an hangi durağın yakınında araç varsa kapı numarası ve son konum saatiyle işaretlenir.
- Yön (gidiş / dönüş) tek dokunuşla değişir; konumlar 20 saniyede bir kendiliğinden yenilenir.
- Araca dokununca konumu harita uygulamasında açılır.
- Sık kullanılan hatlar ☆ ile favorilere eklenir.

Yalnızca `INTERNET` izni ister; hesap, reklam, analitik yoktur. Çalışma zamanı kütüphane bağımlılığı yoktur (yalnızca Android çatısı, Kotlin).

## Veri kaynağı

İBB / İETT açık veri SOAP servisleri (`api.ibb.gov.tr/iett`):

| Servis | Metot | Kullanım |
|---|---|---|
| `FiloDurum/SeferGerceklesme.asmx` | `GetHatOtoKonum_json` | Hattaki araçların canlı konumu |
| `ibb/ibb.asmx` | `DurakDetay_GYY` | Hattın yön bazında durak sırası |
| `UlasimAnaVeri/HatDurakGuzergah.asmx` | `GetHat_json` | Hat adı |

Açık veri "durağa kaç dakika kaldı" tahmini sunmaz; uygulama aracın en son bildirdiği yakın durağı gösterir.

## Derleme

```bash
cd otobus-takip
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest  # ayrıştırıcı testleri
```

JDK 17 ve Android SDK (platform 35) gerekir. Her push'ta GitHub Actions (`Android APK` iş akışı) APK'yı derler ve `otobus-takip-debug-apk` adıyla artifact olarak yükler.

Android 8.0 (API 26) ve üzeri desteklenir.
