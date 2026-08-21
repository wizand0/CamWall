# CamWall

Android-приложение для мониторинга RTSP-камер: видеостена с превью всех камер, live-просмотр потока и фоновое обновление кадров по расписанию.

**Текущая версия: 1.3**

## Скриншоты

| | |
|---|---|
| ![Видеостена](images/screenshot1.png) | ![Live-просмотр](images/screenshot2.png) |
| ![Экран камеры](images/screenshot3.png) | ![Настройки](images/screenshot4.png) |

## Возможности

- **Видеостена** — сетка превью всех камер; отключённые камеры выносятся в отдельный раздел в конец списка.
- **Добавление камер** — вручную (имя + RTSP URL с проверкой соединения) или по **QR-коду** (CameraX + ML Kit; QR должен содержать plain-text ссылку `rtsp://...`).
- **Live-просмотр** — просмотр RTSP-потока в реальном времени на экране камеры (FFmpeg пишет последовательность кадров, UI подхватывает каждый новый файл).
- **Фоновое обновление** — WorkManager периодически обновляет кадры всех включённых камер; интервал задаётся в настройках.
- **Настройки** — интервал обновления, ночная тема, число повторных попыток захвата (с экспоненциальным backoff), ограничение одновременных захватов.
- **Отключение камер** — переключатель на экране камеры: выключенная камера не участвует в автообновлении, но доступна вручную.
- **Безопасность** — RTSP URL хранятся в `EncryptedSharedPreferences` (Android Keystore, AES), а не в базе; в UI и логах URL всегда маскируются (`rtsp://user:****@host/...`), включая нативные логи ffmpeg-kit.

## Стек

| Компонент | Технология |
|---|---|
| Язык | Kotlin |
| UI | Jetpack Compose, Material 3 |
| БД | Room (KSP) |
| Настройки | DataStore Preferences |
| Фоновые задачи | WorkManager |
| Захват кадров / live view | FFmpegKit (`-rtsp_transport tcp`) |
| QR-сканер | CameraX + ML Kit Barcode Scanning |
| Загрузка изображений | Coil |
| Безопасность | androidx.security:security-crypto |

Media3 ExoPlayer-RTSP сознательно не используется для захвата: он требует `fmtp` в SDP и не работает с большинством дешёвых IP-камер/NVR. FFmpeg покрывает эти случаи.

## Требования

- Android: minSdk **29**, targetSdk/compileSdk **37**
- JDK 11+ (toolchain 11)
- Gradle wrapper (используется только `./gradlew`, глобальный Gradle не требуется)

## Сборка

```bash
./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # unit-тесты
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Архитектура

MVVM с одним `CameraWallViewModel` (ручная фабрика, без DI-фреймворков):

```
presentation/   экраны Compose + навигация
viewmodels/     CameraWallViewModel (StateFlow)
domain/         модель Camera, репозиторий-интерфейс, use cases
data/           Room (AppDatabase, CameraDao), репозиторий
rtsp/           RtspFrameCapture (snapshot), RtspLiveViewer (live)
security/       RtspUrlCryptoStore (EncryptedSharedPreferences)
worker/         CameraUpdateWorker (фоновое обновление)
```

Ключевые детали:

- Кадр камеры хранится как файл `files/cameras/{cameraId}/latest.jpg` (атомарная запись: tmp + rename).
- Live view: FFmpeg-сеанс пишет `cache/live/frame_%05d.jpg` с low-latency флагами (`-fflags nobuffer -flags low_delay`); UI опрашивает каталог каждые 100 мс, декодирует кадр вручную (`BitmapFactory` на IO-потоке) и показывает его только после успешного декода — старый кадр не сбрасывается на время загрузки нового, что убирает мерцание. Из последовательности файлов всегда берётся предпоследний, а не самый новый: FFmpeg открывает следующий файл только после полного закрытия предыдущего, поэтому это гарантирует, что читается не оборванный кадр.
- Обновление камер ограничено семафором (не более 2 одновременных захватов), обходы защищены мьютексом от пересечения.

## Формат QR-кода

Plain-text RTSP URL:

```
rtsp://user:password@192.168.1.100:554/stream1
```

## Статус

Проект в активной разработке (ветка `ai/compose-mvp`). Планы и история изменений — в `IMPLEMENTATION_PLAN_v*.md`.
