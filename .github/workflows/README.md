# GitHub workflows

Автоматическая сборка пока не настроена. Release APK собираются локально через Gradle.

Команда:

```powershell
java -classpath "gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain :TMessagesProj_App:assembleAfatRelease
```
