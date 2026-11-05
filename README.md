# LifeAssistant

Учебное Android-приложение на Kotlin и Jetpack Compose для задач и планирования дня.

## Состояние проекта

- Задачи: создание, редактирование и хранение в Room.
- Календарь: текущая реализация расписания, мероприятий и размещения задач; перемещение и обновление времени требуют дальнейшей доработки.
- Финансы и лекции: запланированные разделы, пока представлены заглушками.

Этот импорт сохраняет код из исходного архива. Он не исправляет ошибки календаря; сборка и работа на устройстве в рамках импорта не проверены.

## Открытие проекта

1. Клонируйте репозиторий:

   ```bash
   git clone https://github.com/Anasikus/LifeAssistant.git
   ```

2. Откройте в Android Studio корневую папку `LifeAssistant`, содержащую `settings.gradle.kts`, а не только папку `app`.
3. Дождитесь Gradle Sync. Установите требуемый Android SDK по предложению IDE. В исходной конфигурации compileSdk и targetSdk — 37, минимальная версия — 24; критерий JVM для Gradle — 25.
4. Выберите устройство или эмулятор и запустите конфигурацию `app`.

Версии зависимостей сохранены из архива в `gradle/libs.versions.toml`, версия Gradle — в `gradle/wrapper/gradle-wrapper.properties`. Их совместимость при импорте отдельно не проверялась.

## Структура

| Путь | Назначение |
| --- | --- |
| `app/src/main/java/com/anasikus/lifeassistant/MainActivity.kt` | Вход в приложение |
| `app/src/main/java/com/anasikus/lifeassistant/data/` | Room: база, сущности и DAO |
| `app/src/main/java/com/anasikus/lifeassistant/domain/` | Правила задач, даты и планирование |
| `app/src/main/java/com/anasikus/lifeassistant/repository/` | Работа с данными |
| `app/src/main/java/com/anasikus/lifeassistant/viewmodel/` | Состояние экранов и действия |
| `app/src/main/java/com/anasikus/lifeassistant/ui/` | Экраны, компоненты, навигация и тема |
| `app/src/main/res/` | Ресурсы Android |
| `gradle/`, `gradlew`, `gradlew.bat` | Конфигурация и запуск Gradle Wrapper |

## Дальнейшая работа

Для изменений создавайте отдельную ветку от актуальной `main`, например `fix/calendar-interactions`. После проверки изменения можно объединять через pull request.

Папки сборки, кэш Gradle, настройки IDE, `local.properties` и ключи подписи исключены через `.gitignore`. Служебная папка `.git` из исходного архива не переносилась.
