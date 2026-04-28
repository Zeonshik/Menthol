# Building Menthol From Source

Эта инструкция нужна для тех, кто собирает Menthol из исходников.

В репозитории не храняться приватные ключи, `google-services.json`, `agconnect-services.json`, keystore и локальные пароли. Все эти файлы нужно создать или скачать самостоятельно.

## 1. Требования

- Android Studio с Android SDK 35.
- Android NDK `21.4.7075529`.
- CMake `3.10.2` или совместимая установленная версия из Android SDK.
- JDK, который подходит текущему Android Gradle Plugin.
- PowerShell или cmd на Windows.

## 2. Gradle Properties

Скопируйте пример:

```powershell
copy gradle.properties.example gradle.properties
```

Откройте `gradle.properties` и настройте:

```properties
APP_VERSION_CODE=6666
APP_VERSION_NAME=12.6.4
APP_PACKAGE=ru.menthol.app

RELEASE_STORE_PASSWORD=your_store_password
RELEASE_KEY_ALIAS=your_key_alias
RELEASE_KEY_PASSWORD=your_key_password
```

`gradle.properties` добавлен в `.gitignore`, его не нужно публиковать.

## 3. Telegram API ID и API Hash

Получите свои данные здесь:

```text
https://core.telegram.org/api/obtaining_api_id
```

Затем откройте:

```text
TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java
```

И замените:

```java
public static int APP_ID = 0; // YOUR_API_ID
public static String APP_HASH = "YOUR_API_HASH";
```

На свои значения.

Без этих значений APK может собраться, но нормальная авторизация в Telegram работать не будет.

## 4. Google Services

Для обычной Google/standalone-сборки нужен свой Firebase config.

Создайте Android-приложение в Firebase Console с package name из `APP_PACKAGE`, например:

```text
ru.menthol.app
```

Скачайте `google-services.json` и положите его в нужные модули:

```text
TMessagesProj/google-services.json
TMessagesProj_App/google-services.json
```

Если собираете другие app-модули, добавьте файл и туда.

Файл `google-services.json` добавлен в `.gitignore` и не должен публиковаться.

## 5. Huawei Services

Huawei-сборка необязательна.

Если она нужна, создайте приложение в Huawei AppGallery Connect с тем же package name:

```text
ru.menthol.app
```

Скачайте `agconnect-services.json` и положите сюда:

```text
TMessagesProj_AppHuawei/agconnect-services.json
```

Также укажите свой Huawei App ID в `BuildVars.java`:

```java
public static String HUAWEI_APP_ID = "your_huawei_app_id";
```

Если Huawei не нужен, просто не собирайте `TMessagesProj_AppHuawei`.

## 6. Google Auth Client ID

Если нужен Google Sign-In/Identity, создайте OAuth Client ID в Google Cloud/Firebase для вашего package name и SHA-1 сертификата.

Затем в `BuildVars.java` заполните:

```java
public static String GOOGLE_AUTH_CLIENT_ID = "your_client_id.apps.googleusercontent.com";
```

Если не используется, можно оставить пустым.

## 7. Release Keystore

Создайте свой release keystore и положите сюда:

```text
TMessagesProj/config/release.keystore
```

Пароли укажите в локальном `gradle.properties`.

Keystore добавлен в `.gitignore` и не должен публиковаться.

## 8. Сборка обычных APK

```powershell
java -classpath "gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain :TMessagesProj_App:assembleAfatRelease
```

Готовые APK:

```text
TMessagesProj_App/build/outputs/apk/afat/release/
```

Ожидаемые файлы:

```text
app-arm64-v8a.apk
app-armeabi-v7a.apk
app-universal.apk
```

## 9. Сборка Huawei APK

Только если настроен `agconnect-services.json` под ваш package name:

```powershell
java -classpath "gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain :TMessagesProj_AppHuawei:assembleAfatRelease
```

Готовые APK:

```text
TMessagesProj_AppHuawei/build/outputs/apk/afat/release/
```

## 10. Что нельзя коммитить

Не публикуйте:

- `gradle.properties`
- `local.properties`
- `google-services.json`
- `agconnect-services.json`
- `*.keystore`
- `*.apk`, `*.aab`, `*.apks`
- `.gradle/`, `build/`, `.cxx/`

Для публичного репозитория используйте только `gradle.properties.example` и эту инструкцию.
