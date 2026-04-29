<p align="center">
  <img src="TMessagesProj/src/main/res/drawable-xxhdpi/logo_middle.png" width="128" alt="Menthol" />
</p>

<h1 align="center">Menthol</h1>

<p align="center">
  An unofficial Telegram client with a built-in proxy server and minor improvements 
</p>

<p align="center">
  <a href="https://github.com/Zeonshik/Menthol/releases">
    <img src="https://img.shields.io/github/v/release/Zeonshik/Menthol?style=for-the-badge&label=pre-release" alt="Release" />
  </a>
  <a href="https://github.com/Zeonshik/Menthol/releases">
    <img src="https://img.shields.io/github/downloads/Zeonshik/Menthol/total?style=for-the-badge" alt="Downloads" />
  </a>
  <img src="https://img.shields.io/badge/platform-Android-3DDC84?style=for-the-badge" alt="Android" />
  <img src="https://img.shields.io/badge/license-GPL--2.0-blue?style=for-the-badge" alt="GPL-2.0" />
</p>

<p align="center">
  <a href="https://github.com/Zeonshik/Menthol/releases">Downloads</a>
  ·
  <a href="https://t.me/MentholGram">Telegram Channel</a>
  ·
  <a href="https://github.com/Zeonshik/Menthol/issues">Report an issue</a>
</p>

---

## О проекте

🍃 Menthol - неофициальный клиент на базе Telegram

## Возможности

- Автоматический перебор серверов при проблемах с подключением.
- Визуальная отметка удаленных сообщений.
- Мелкие UI-улучшения.

## Скачать

Готовые APK доступны в разделе Releases:

```text
https://github.com/Zeonshik/Menthol/releases
```

Telegram-канал проекта где вы можете сообщить о проблемах или как то помочь проекту:

```text
https://t.me/MentholGram
```

Рекомендуемые APK:

```text
arm64-v8a      современные Android-устройства
armeabi-v7a    старые 32-битные устройства
universal      универсальная сборка
```

## Сборка

Перед сборкой из исходников нужно подготовить свои файлы и значения:

- Telegram `api_id` и `api_hash` из https://my.telegram.org/apps
- `google-services.json` из Firebase Console для своего package name
- свой release keystore и пароли к нему
- при необходимости Huawei `agconnect-services.json` из AppGallery Connect
- при необходимости Google OAuth Client ID

Эти файлы и значения не входят в репозиторий и должны быть созданы самостоятельно.

Подробная инструкция сборки из исходников находится здесь:

```text
docs/BUILDING.md
```

Обычная release-сборка:

```powershell
java -classpath "gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain :TMessagesProj_App:assembleAfatRelease
```

Готовые APK будут здесь:

```text
TMessagesProj_App/build/outputs/apk/afat/release/
```

## Disclaimer

Menthol is an unofficial Telegram client. This project is not affiliated with Telegram Messenger Inc.

Telegram is a trademark of Telegram Messenger Inc.

## License

This project is based on Telegram for Android and follows the original GPL-2.0-or-later licensing requirements.
