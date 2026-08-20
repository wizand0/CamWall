# IMPLEMENTATION_PLAN_v7 — повороты экрана в CameraDetailScreen + починка автообновления кадров в UI

Дата: 2026-08-20
Ветка: `ai/compose-mvp`
Основание: замечания пользователя от 20.08.2026 после теста плана v6.

---

## 1. Диагностика

### Проблема 1: live-трансляция останавливается при повороте в альбом
**Корень:** `RtspLiveViewer` живёт в `remember { }` внутри `CameraDetailScreen`, а `liveMode` — в `mutableStateOf`. При повороте Activity пересоздаётся:
1. композиция уничтожается → `DisposableEffect.onDispose` → `liveViewer.stop()` → FFmpeg-сеанс отменяется;
2. состояние `liveMode = true` теряется (не `rememberSaveable`).

**Решение:** вынести live-сеанс из композиции в `CameraWallViewModel`:
- VM создаётся на `NavBackStackEntry` (владелец ViewModelStore — backstack entry навигации), поэтому **переживает повороты**, но очищается при выходе с экрана (`onCleared`).
- В VM: поле `liveViewer: RtspLiveViewer?` + `StateFlow<Boolean> isLiveActive` + методы `startLiveView(cameraId)`, `stopLiveView()`, `latestLiveFrame(): File?`.
- `stop()` вызывается в `onCleared()` VM — сеанс гарантированно гасится при уходе с экрана (back, delete, navigate), но не при повороте.
- `DisposableEffect` в экране больше не останавливает сеанс (удаляется).
- Кнопка Play/Close в TopAppBar читает `isLiveActive` из VM.

Побочный эффект (принимаем): если свернуть приложение с включённой трансляцией, сеанс продолжит работать до возврата/выхода с экрана. Опционально на будущее: остановка по `ProcessLifecycleOwner.ON_STOP` — в этот план не входит.

### Проблема 2: в альбомном режиме всё мелкое
**Решение:** адаптивная раскладка `CameraDetailScreen` по ориентации (`LocalConfiguration.current.orientation`):

- **Live включён + альбом** → трансляция на весь экран: Scaffold без TopAppBar (или с прозрачным overlay-управлением), `AsyncImage` занимает `fillMaxSize()`, информационная панель скрывается. Кнопка остановки — плавающий IconButton поверх видео (полупрозрачный фон), сверху-справа. Индикатор «LIVE» остаётся.
- **Live выключен + альбом** → `Row`: слева кадр камеры на половину ширины (`weight(1f)`, `ContentScale.Fit`), справа — информационная колонка (`weight(1f)`): имя, URL (маскированный), статус, время кадра, переключатель Auto-update, кнопки действий (Refresh/Delete переезжают из TopAppBar в колонку, т.к. TopAppBar в альбомном snapshot-режиме можно оставить — решение при реализации: проще оставить TopAppBar и в правой колонке только инфо+тумблер).
- **Портрет** — текущая раскладка без изменений (Column: кадр сверху, инфо снизу).

Реализация: один `CameraDetailScreen`, внутри `if (isLandscape) LandscapeLayout(...) else PortraitLayout(...)` с общими дочерними composables (`CameraInfoPanel`, `LivePreview`, `SnapshotPreview`), чтобы не дублировать код.

### Проблема 3: кадры не обновляются по таймеру, пока не сменишь экран
**Корень:** Coil кэширует изображение по пути файла. Кадр пишется в один и тот же `latest.jpg` (атомарная перезапись) → путь не меняется → Coil отдаёт старый кадр из memory-кэша. Обновление видно только после смены экрана (пересоздание AsyncImage с новым запросом… фактически видно после пересборки кэша/экрана).

Это известная проблема, зафиксированная ещё 19.08 (п.4 «НЕ сделано») — теперь делаем.

**Решение:** `memoryCacheKey`, меняющийся с каждым новым кадром:
- `CameraCard` (стена): `ImageRequest.Builder(...).data(frameFile).memoryCacheKey("${camera.id}-${camera.lastSuccessfulFrameAt ?: 0}")`. `lastSuccessfulFrameAt` обновляется в БД при каждом успешном захвате → ключ меняется → Coil перечитывает файл. Disk-кэш Coil для `File`-моделей не используется (файл читается напрямую), поэтому достаточно только memoryCacheKey.
- `CameraDetailScreen` (snapshot-превью): тот же приём.
- Live view не затрагивается (там ключ = уникальное имя файла `frame_%05d.jpg`, сделано в v6).

Проверить, что foreground-цикл обновления реально тикает (лог `refreshAllCameras` уже есть) — по симптому он работает, проблема именно в кэше Coil; если на устройстве подтвердится, что и БД не обновляется — отдельная диагностика (в план не закладываем).

---

## 2. Изменения по файлам

| Файл | Изменение |
|---|---|
| `viewmodels/CameraWallViewModel.kt` | + `liveViewer`, `isLiveActive: StateFlow<Boolean>`, `startLiveView(cameraId)`, `stopLiveView()`, `latestLiveFrame(): File?`; `stopLiveView()` в `onCleared()` |
| `presentation/screens/CameraDetailScreen.kt` | убрать локальный `RtspLiveViewer`/`DisposableEffect`-stop; live-состояние из VM; адаптивная раскладка (портрет/альбом, live-fullscreen); snapshot-превью с `memoryCacheKey` |
| `presentation/screens/CameraWallScreen.kt` | `CameraCard`: `ImageRequest` с `memoryCacheKey = id + lastSuccessfulFrameAt` |

Версии зависимостей не меняются (жёсткое ограничение). Новых зависимостей не требуется (`BackHandler`/`LocalConfiguration` уже доступны).

## 3. Порядок работ

1. ViewModel: перенос live-сеанса + onCleared.
2. CameraDetailScreen: подключение live к VM, адаптивная раскладка (портрет/альбом × live/snapshot).
3. CameraWallScreen + CameraDetailScreen: memoryCacheKey для snapshot-кадров.
4. `assembleDebug` + `testDebugUnitTest` (до 3 попыток при ошибках).
5. Локальный коммит — **только с разрешения пользователя**.

## 4. Критерии приёмки (на устройстве)

1. Запустить live в портрете → повернуть в альбом → трансляция продолжается без перезапуска, видео на весь экран.
2. Повернуть обратно в портрет → трансляция продолжается в обычной раскладке.
3. Альбом без live: кадр слева ~пол-экрана, справа инфо; всё читаемо, не мелкое.
4. Оставить стену на 2–3 интервала обновления: превью и время «Last frame» обновляются без смены экрана; в logcat видны успешные захваты, в UI — новые кадры.
5. Выход с экрана детали (back) гасит FFmpeg-сеанс: в logcat `stop: cancelling live view session`.
