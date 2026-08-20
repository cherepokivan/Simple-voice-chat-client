# Архитектура Android-клиента

> **Цель:** Создать Android-клиент для Simple Voice Chat, использующий тот же протокол, что и Windows-клиент, с адаптацией под мобильную платформу.

## Технологический стек

- **Язык:** Kotlin
- **UI:** Jetpack Compose
- **Асинхронность:** Kotlin Coroutines / StateFlow
- **Сеть:** `java.net.DatagramSocket` (UDP)
- **Аудио захват:** `AudioRecord` (Android SDK)
- **Аудио воспроизведение:** `AudioTrack` (Android SDK)
- **Кодек:** Opus (JNI / NDK или готовая библиотека-обертка)
- **Минимальная версия:** Android 10 (API 29)
- **Архитектура CPU:** arm64-v8a

## Структура модулей

```text
android/
├── protocol/           # Общая логика протокола (Handshake, Packets, Encryption)
├── app/                # Android Application (Jetpack Compose UI)
│   ├── ui/             # Экраны (Main, Settings, Group)
│   ├── audio/          # Реализация IAudioCapture (AudioRecord) и IAudioPlayback (AudioTrack)
│   ├── network/        # Реализация UDP-клиента
│   └── service/        # Foreground Service для фоновой работы
```

## Разделение протокола

Протокольная логика (сериализация пакетов, шифрование AES-GCM, управление состояниями `ConnectionStateMachine`) должна быть вынесена в отдельный Kotlin Multiplatform (или чистый Kotlin/JVM) модуль `protocol`, чтобы избежать дублирования логики между платформами, если бы обе использовали JVM. В текущем решении Windows использует C#, а Android — Kotlin, поэтому протокол реализуется параллельно, но строго по одной и той же спецификации, описанной в `PROTOCOL.md`.

## Фоновая работа и уведомления

Android ограничивает работу с микрофоном в фоне. Для обеспечения непрерывного голосового чата:
1. Приложение использует **Foreground Service** с типом `microphone`.
2. В шторке уведомлений отображается постоянное уведомление (Ongoing Notification).
3. Уведомление содержит действия: `Mute`, `Disconnect`, `Open app`.
4. Запрашивается разрешение `FOREGROUND_SERVICE_MICROPHONE`.

## Разрешения (Permissions)

Минимально необходимые разрешения в `AndroidManifest.xml`:
- `android.permission.INTERNET`
- `android.permission.RECORD_AUDIO`
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_MICROPHONE`
- `android.permission.BLUETOOTH_CONNECT` (для Android 12+, чтобы управлять Bluetooth-гарнитурами)

## Аудио и Bluetooth

- Использовать `AudioManager` для управления маршрутизацией звука.
- При подключении/отключении Bluetooth (SCO) или проводных наушников звук должен автоматически переключаться.
- Захват звука использует `MediaRecorder.AudioSource.VOICE_COMMUNICATION` для аппаратного эхоподавления, если это возможно.

## Ограничения Android

- Задержка аудио (Audio Latency) на Android может быть выше, чем на Windows. Рекомендуется использовать OpenSL ES / AAudio (через NDK) для минимизации задержки, если `AudioTrack`/`AudioRecord` окажутся недостаточно быстрыми.
- Режим энергосбережения (Doze Mode) может обрывать UDP-пакеты. Foreground Service смягчает эту проблему.

## Интеграция с Windows-клиентом (QR-код)

Для удобства передачи настроек (IP, порт), Windows-клиент может генерировать QR-код, а Android-клиент — сканировать его (например, через библиотеку ZXing или ML Kit). В QR-коде передаются только публичные данные соединения, **без секретов сессии**.
