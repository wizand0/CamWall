# IMPLEMENTATION_PLAN_v6 — плавный live view без моргания + закрытие утечки паролей в логах

Дата: 2026-08-19
Ветка: `ai/compose-mvp`
Основание: тест на устройстве 19.08.2026 (лог logcat, приложение работает: QR добавляет камеру, миграция БД 1→2 прошла, live view запускается и останавливается).

---

## 1. Диагностика по логу (подтверждено)

| Проверка | Результат |
|---|---|
| Миграция Room 1→2 на устройстве | ✅ `DB version upgrading from 1 to 2`, камера «лобби» (добавлена по QR) обновляется — URL читаются из EncryptedSharedPreferences |
| Завершение live-трансляции | ✅ `stop: cancelling live view session 2` → `Exiting normally, received cancel request.` → `returnCode=255` |
| QR-сканер (CameraX + ML Kit) | ✅ разрешение, открытие/закрытие камеры телефона, сканирование — без ошибок |
| Моргание live view | ❌ `skia: libjpeg error 118 <Corrupt JPEG data>`, `libjpeg error 105 ... Incomplete image data` — Coil читает `live.jpg` во время перезаписи файла FFmpeg'ом (`-update 1`) |
| Отставание от реального времени | ❌ нет low-latency флагов у входа RTSP + опрос файла раз в 400 мс. FFmpeg успевает (speed≈1.2x) — узкое место не декодер |
| **Утечка пароля** | ❌ `ffmpeg-kit I Input #0, rtsp, from 'rtsp://web:<пароль>@...'` — ffmpeg-kit логирует аргументы с URL целиком |

Постороннее в логе (не требует действий): `Unable to open libpenguin.so` (системное), `Kumiho` (Samsung-декодер JPEG), пересоздание WorkManager-джобов, HEVC-ошибки первых кадров `PPS id out of range` / `Could not find ref with POC` (норма при входе в поток до ключевого кадра).

---

## 2. Задача A (основная): плавный live view

### 2.1. Причина моргания и решение
Сейчас: один файл `live.jpg` + `-update 1` + чтение раз в 400 мс → чтение в момент перезаписи = битый JPEG = чёрный/артефактный кадр.

**Решение: последовательность файлов кадров вместо одного перезаписываемого.**
- Выход FFmpeg: `<cache>/live/frame_%05d.jpg` (каждый кадр — отдельный файл, запись атомарна с точки зрения читателя: файл появляется только после полного закрытия).
- UI отслеживает появление нового файла (по имени-счётчику), загружает его в Coil.
- Опрос сократить до ~100 мс (поток 15 fps = кадр каждые ~66 мс; 100 мс даёт почти каждый кадр, без пропусков на уровне опроса).

### 2.2. Снижение задержки (аргументы FFmpeg в `RtspLiveViewer.start`)
```
-y
-rtsp_transport tcp
-fflags nobuffer
-flags low_delay
-probesize 65536
-analyzeduration 0
-i <rtspUrl>
-q:v 3
-vf scale='min(960,iw)':-2
-f image2
<liveDir>/frame_%05d.jpg
```
(`-probesize`/`-analyzeduration` — быстрый старт без долгого анализа; при проблемах стартового артефакта можно вернуть `analyzeduration 100000`.)

### 2.3. Изменения в коде
- `RtspLiveViewer`:
  - `start()` — новые аргументы (п. 2.2), сброс каталога `live/` перед стартом;
  - `latestFrameFile(): File?` — поиск максимального `frame_*.jpg` в каталоге (кэш результата между вызовами по списку файлов, чтобы не листать каталог на каждый тик);
  - очистка: удалять файлы старше последних ~30 кадров (в цикле опроса UI либо по счётчику в viewer); `stop()` чистит весь каталог;
  - защита от гонок: `latestFrameFile` возвращает только файл, размер которого не меняется/файл закрыт (проверка длины дважды с паузой — опционально, обычно не требуется при image2).
- `CameraDetailScreen`:
  - `LIVE_POLL_INTERVAL_MS` 400 → 100;
  - модель Coil — `viewer.latestFrameFile()`, `memoryCacheKey = имя файла` (уникален сам по себе, тик можно убрать);
  - не показывать кадр, если файл ещё не появился (статус «Подключение…»).

### 2.4. Критерии приёмки на устройстве
- Моргание исчезло (нет `Corrupt JPEG data` в logcat).
- Задержка визуально сопоставима с реальностью (проверка по часам в кадре).
- CPU на 15 fps/640x360 приемлемый (HEVC программный уже тянет: speed≈1.2x).

### 2.5. Опционально (если останется недовольство плавностью/CPU)
- Аппаратный декодер HEVC: `-c:v hevc_mediacodec` (сборка ffmpeg-kit mediacodec включает; риск — нестабильность hw-декодера в ffmpeg на части устройств, проверять отдельно).
- Эксперимент: media3-exoplayer-rtsp вместо FFmpeg. Против: известная проблема SDP/fmtp на Hikvision (в логе — HIK Media Server V4.51.006, т.е. именно проблемный случай). Только как эксперимент с быстрым откатом.

---

## 3. Задача B: убрать пароль из logcat

В `CamWallApplication.onCreate()`:
```kotlin
FFmpegKitConfig.enableRedactedLog()
```
- Редктит аргументы команд в логах ffmpeg-kit (URL с паролем перестанут печататься).
- Проверить, что свои классы (`RtspFrameCapture`, `RtspLiveViewer`, `CameraUpdateWorker`) не логируют URL — по текущему логу чисто, закрепить правилом: URL в логах только через `RtspUrlMasker`.
- Опционально для release: `FFmpegKitConfig.disableLogs()` целиком (решение принять отдельно).

Примечание: ML Kit шлёт телеметрию использования (`FIREBASE_ML_SDK` → firebaselogging.googleapis.com), это поведение самой библиотеки; отключение официально не поддерживается — принять как известное.

---

## 4. Мелочи
- Удалить мусорные `hs_err_pid*.log` в корне проекта; добавить в `.gitignore`.
- `DEVELOPMENT_LOG.md` исторически содержит реальные RTSP-пароли и в git — рекомендуется удалить файл и вычистить из истории (git filter-repo), решение за пользователем. Пароль, засветившийся в logcat 19.08, также считается скомпрометированным — сменить на камере при возможности.

---

## 5. Порядок работ
1. **B** (5–10 минут, безопасность) — `enableRedactedLog()`, проверка масок.
2. **A** (основное) — переписать `RtspLiveViewer` на последовательные файлы + low-latency аргументы, поправить `CameraDetailScreen` (интервал 100 мс, очистка старых кадров).
3. Сборка `assembleDebug` + `testDebugUnitTest`, локальный коммит (без push).
4. Проверка на устройстве по критериям 2.4; при остаточных проблемах — опции 2.5.

Ожидаемые изменённые файлы:
- `app/src/main/java/ru/wizand/camwall/CamWallApplication.kt`
- `app/src/main/java/ru/wizand/camwall/rtsp/RtspLiveViewer.kt`
- `app/src/main/java/ru/wizand/camwall/presentation/screens/CameraDetailScreen.kt`
- `.gitignore`
