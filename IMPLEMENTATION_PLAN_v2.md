# CamWall — план работ v2

Дата: 19.08.2026
Ветка: `ai/compose-mvp`
Статус: приложение собирается и запускается на устройстве; **захват видеокадров не работает** (см. §3).
Предыдущий план: `IMPLEMENTATION_PLAN.md` (v1).

---

## 1. Что реально сделано с момента плана v1

### 1.1. ЭТАП 0 (сборка и модель) — ЗАКРЫТ
- Конфликт Kotlin-плагина с AGP 9.3.1 решён: плагин `org.jetbrains.kotlin.android`
  отключён (закомментирован в `app/build.gradle.kts`), Compose собирается через
  `org.jetbrains.kotlin.plugin.compose`. **Внимание:** в `libs.versions.toml` остались
  мёртвые записи (`jetbrains-kotlin-android`, `hilt*`, `compose-compiler`) — мусор,
  на сборку не влияет. Версии зависимостей зафиксированы пользователем, менять без согласования нельзя.
- Hilt полностью удалён из кода: ручной `CameraWallViewModelFactory`,
  `CameraUpdateWorker` без dagger-импортов.
- Модель `Camera` приведена к единому виду (id: String, name, rtspUrl, enabled,
  createdAt, lastSuccessfulFrameAt, lastAttemptAt, lastError, consecutiveErrors,
  sortOrder) + вычисляемые `frameFilePath` и `status` (в Room не хранятся).
- `CameraDao` синхронизирован: `getCameraById(id: String)`, есть `updateCameraFrame`.
- Экраны CameraWall / AddCamera / CameraDetail / Settings компилируются против
  актуальной модели; навигация работает; приложение запускается на смартфоне.
- Кадр сохраняется в `files/cameras/{cameraId}/latest.jpg` (путь согласован с ТЗ §8).
- При удалении камеры удаляется запись из Room и папка с кадрами.

### 1.2. Не закрыто из ЭТАПА 0 (гигиена)
1. **Изменения не закоммичены** (`git status`: 14 изменённых файлов + неотрейженные
   `presentation/`, `manager/`, `worker/`, `viewmodel_factory/`). Риск потери работы.
2. В `DEVELOPMENT_LOG.md` (строки 69–70) до сих пор лежат реальные RTSP URL с
   паролями в открытом виде. **Удалить из файла, пароль ротировать.** В git-историю
   они пока не попали (файл untracked) — закоммитить только после чистки.
3. Мусор в корне: `copy_project_script.py`, `save_progress.bat`,
   `hs_err_pid17268.log`, `hs_err_pid7048.log` — удалить.
4. В `CameraDetailScreen` RTSP URL с паролем выводится на экран в открытом виде —
   маскировать (`rtsp://user:****@host:554/...`).

---

## 2. Главный текущий дефект: «видео не показывает»

Симптом: приложение запускается, камеры видны в списке, но превью (кадры) не
появляются; в logcat нет ни одной записи об успехе/ошибке захвата.

### Корневые причины (по анализу кода `RtspFrameCapture.kt`)
1. **TextureView создаётся программно и никуда не прикрепляется**
   (`val textureView = TextureView(context)` без addView в иерархию). У такого
   TextureView никогда не создаётся `SurfaceTexture`: `isAvailable == false`,
   `onSurfaceTextureAvailable`/`onSurfaceTextureUpdated` не вызываются → кадр не
   может быть получен в принципе. Захват всегда заканчивается внутренним таймаутом.
2. **Плеер не создаёт поверхность для рендера** — ExoPlayer с неотрисованным
   TextureView не рендерит кадры; `textureView.bitmap` всегда null/пустой.
3. **Утечка ресурсов при таймауте**: `withTimeout` снаружи try/catch — при отмене
   `player.release()` не вызывается.
4. **Двойной механизм таймаутов** (withTimeout + отдельный CoroutineScope внутри
   suspendCoroutine) с гонками по флагу `captured` и подавлением
   IllegalStateException — хрупко и нечитаемо.
5. **Нет обработки ошибок плеера** (`onPlayerError`) — недоступная камера
   диагностируется только общим таймаутом 15 с, причина теряется.
6. Кадр пишется в файл неатомарно (нет tmp + rename).
7. Дополнительная причина «пустой стены»: при старте экрана захват вообще не
   запускается автоматически — только по кнопке Refresh; а Coil кэширует файл по
   имени, поэтому даже после успешной перезаписи `latest.jpg` может показывать
   старое из memory-кэша (нужен `memoryCacheKey` с версией кадра).

### Исправление (выполняется сейчас, без изменения версий Gradle)
Переписать `RtspFrameCapture` на схему без View-иерархии (работает и в UI, и в Worker):
1. ExoPlayer + `ImageReader` (RGBA_8888): слушатель `onVideoSizeChanged` создаёт
   ImageReader под размер потока и вызывает `player.setVideoSurface(reader.surface)`.
2. Первый `onImageAvailable` → Image → Bitmap (с учётом rowStride/pixelStride),
   даунскейл до ~640px по большей стороне, JPEG 80%, атомарная запись
   (tmp-файл + rename) в `files/cameras/{cameraId}/latest.jpg`.
3. RTSP-источник через `RtspMediaSource.Factory().setForceUseRtpTcp(true)` —
   надёжнее на камерах, где UDP-пакеты теряются (типично для внешних камер за NAT).
4. Единый `withTimeoutOrNull(15000)`; `player.release()` и `imageReader.close()` —
   в finally при любом исходе; `onPlayerError` пробрасывает причину в Result.failure.
5. Весь захват на Dispatchers.Main (ExoPlayer требует Looper), вызов из любого потока безопасен.

Сопутствующие правки:
- Маскер URL `RtspUrlMasker` (`rtsp://user:****@host:port/path`) — используется в
  `CameraDetailScreen` (UI), дальше — во всех логах (ТЗ §43).
- Автозапуск обновления на стене: при открытии CameraWall обновляются камеры без
  кадров (`lastSuccessfulFrameAt == null`) — «быстрый старт» по ТЗ §48.
- Coil: `memoryCacheKey = cameraId + версия кадра`, чтобы новый `latest.jpg`
  показывался сразу, а не из кэша.

---

## 3. Статус этапов (от v1) после исправления захвата

| Этап | Статус | Комментарий |
|---|---|---|
| 0. Сборка и модель | ✅ в основном закрыт | остаток — §1.2 (git, чистка логов, мусор) |
| 1. RTSP POC | 🔧 в работе | переписать capture на ImageReader; **прогнать на реальных камерах пользователя**, собрать цифры 10/10 |
| 2. Данные/секреты | ⬜ | EncryptedSharedPreferences для URL, миграция Room v1→v2, парсер/маскер |
| 3. Snapshot engine | ⬜ | контракт `suspend captureFrame(...)`, очистка файлов, счётчики ошибок |
| 4. Стена CameraWall | ⬜ частично | каркас есть; нужны статусы, устаревание кадра, SwipeRefresh |
| 5. Планировщик | ⬜ | concurrency=2, retry≤2, интервалы из DataStore; сейчас refreshAll — последовательный цикл |
| 6. Live View | ⬜ | Media3-плеер в CameraDetailScreen (сейчас там статичный кадр) |
| 7. CRUD/меню | ⬜ частично | Add/Detail есть; нужны EditCameraScreen, меню «⋮», вкл/выкл, sortOrder |
| 8. Настройки/ночь/фон | ⬜ частично | SettingsScreen-заглушка есть; нужен SettingsRepository |
| 9. M3U/QR | ⬜ | |
| 10. Тесты/диагностика | ⬜ | |
| 11. Оптимизация | ⬜ | |

MVP = этапы 0–8 (ТЗ §52).

---

## 4. Ближайшие шаги (по порядку)

1. ✅/🔧 Переписать `RtspFrameCapture` (ImageReader + TCP + finally-release) + маскер +
   авто-обновление камер без кадров + cache key Coil. → `.\gradlew.bat assembleDebug`.
2. Ручная проверка на реальных камерах пользователя (URL — только на устройстве,
   в код/логи не писать): 10 попыток на доступной камере, поведение на недоступной.
   Зафиксировать время до первого кадра.
3. Коммиты: разбить текущие изменения на атомарные (gradle-фикс / модель+Room /
   экраны / rtsp-фикс). Перед коммитом — почистить `DEVELOPMENT_LOG.md`.
4. Удалить мусор из корня репозитория.
5. Далее — ЭТАП 2 (защищённое хранение секретов), затем планировщик (этап 5) как
   самый заметный для пользователя следующий шаг после стабильного захвата.

## 5. Ограничения
- Версии Gradle/зависимостей (AGP 9.3.1, Kotlin 2.4.10, Compose BOM 2026.08.00,
  Media3 1.11.0 и т.д.) зафиксированы пользователем — менять только с согласования.
- RTSP-пароли: не в коде, не в логах, не в документации; в UI — только маскированные.
