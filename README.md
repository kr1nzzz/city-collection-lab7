# City Collection — Lab 7

Учебный клиент-серверный проект на Java для управления коллекцией городов через UDP с хранением данных в PostgreSQL.

Educational Java client-server application for managing a collection of cities over UDP with PostgreSQL persistence.

## Русский

### О проекте

Лабораторная работа №7 по программированию. Проект демонстрирует работу с объектной моделью, коллекциями Java, сетевым обменом, JDBC и многопоточностью. Пользователь работает через консольный клиент; сервер обрабатывает команды и сохраняет изменения в базе данных.

Возможности:

- Регистрация и вход пользователей.
- Просмотр, добавление, изменение и удаление городов; изменение и удаление ограничены владельцем записи.
- Сортировка, выборки по климату и губернатору, история команд.
- Выполнение команд из файла через `execute_script` с проверкой рекурсивных вызовов.
- Передача сериализованных объектов по UDP с разбиением сообщений на фрагменты.
- Многопоточная обработка запросов и журналирование через Log4j 2.

### Технологии и структура

Java 17, Maven, PostgreSQL JDBC 42.7.3, Log4j 2.23.1.

```text
pom.xml                 Maven, зависимости и сборка двух JAR
src/main/java/          клиент, сервер, модель City и работа с БД
src/main/resources/    конфигурация журналирования
```

Основные классы: `ClientMain`, `ServerMain`, `ClientCommandParser`, `CommandProcessor`, `CollectionManager`, `DatabaseManager`, `UdpClient` и `City`. Коллекция в памяти хранится в `Vector<City>`; таблицы `app_users` и `cities` создаются сервером автоматически.

### Сборка и запуск

Требуются JDK 17, Maven и запущенный PostgreSQL. Создайте базу данных и пользователя с правами создания таблиц. Порт PostgreSQL в исходном коде фиксирован: **5433**.

Из корня проекта:

```sh
mvn clean package
java -jar target/server-jar-with-dependencies.jar 5555 YOUR_DB_USER YOUR_DB_PASSWORD localhost YOUR_DB_NAME
```

В отдельном терминале:

```sh
java -jar target/client-jar-with-dependencies.jar localhost 5555 5000
```

Аргументы клиента: адрес сервера, UDP-порт, тайм-аут в миллисекундах. Значения по умолчанию: `localhost`, `5555`, `5000`.

Сервер принимает 1, 3 или 5 аргументов: UDP-порт; дополнительно пользователь и пароль БД; дополнительно хост и имя БД. При запуске только с портом используются `PGUSER` и `PGPASSWORD`. Хост и имя БД берутся из `PGHOST` и `PGDATABASE`, если не заданы аргументами; значения по умолчанию — `pg` и `studs`. `PGPORT` не используется.

После запуска клиента выберите регистрацию или вход, затем введите `help`. Команда `add` запрашивает поля города по очереди. `exit` завершает клиент; в консоли сервера `exit` останавливает сервер. Отдельная команда `save` не требуется: изменения сохраняются в PostgreSQL.

### Учебный статус

Исходный код и `pom.xml` перенесены из исходного архива без изменений. Добавлен только README; служебные файлы IDE и готовые `.class` из архива не включены в структуру репозитория. Проект предназначен для изучения и демонстрации. В текущей реализации используется MD5 для паролей, а UDP-обмен не шифруется; для демонстрации используйте тестовые учётные данные в доверенной среде. Автоматические тесты в архиве отсутствуют. Сборка и интеграционный запуск в среде подготовки репозитория не выполнялись.

## English

### About

Programming laboratory assignment No. 7. This project explores object modelling, Java collections, networking, JDBC and concurrency. A console client sends commands to the server, which processes requests and persists changes in PostgreSQL.

Features:

- User registration and authentication.
- Viewing, adding, updating and removing cities, with ownership checks for updates and deletions.
- Sorting, climate and governor queries, and command history.
- Script execution via `execute_script`, including recursive-call detection.
- Serialized object exchange over UDP with message fragmentation.
- Multithreaded request processing and Log4j 2 logging.

### Technology and structure

Java 17, Maven, PostgreSQL JDBC 42.7.3 and Log4j 2.23.1. `pom.xml` defines dependencies and builds client and server JARs. Application sources are in `src/main/java/`; logging configuration is in `src/main/resources/`.

Entry points are `ClientMain` and `ServerMain`. Command parsing and execution are handled by `ClientCommandParser` and `CommandProcessor`. `CollectionManager` maintains a `Vector<City>`, while `DatabaseManager` provides persistence. The server automatically creates the `app_users` and `cities` tables.

### Build and run

Install JDK 17 and Maven, and start PostgreSQL. Create a database and a database user allowed to create tables. The PostgreSQL port is hard-coded to **5433**.

Run from the project root:

```sh
mvn clean package
java -jar target/server-jar-with-dependencies.jar 5555 YOUR_DB_USER YOUR_DB_PASSWORD localhost YOUR_DB_NAME
```

In another terminal:

```sh
java -jar target/client-jar-with-dependencies.jar localhost 5555 5000
```

Client arguments are server host, UDP port and timeout in milliseconds; defaults are `localhost`, `5555` and `5000`.

The server accepts 1, 3 or 5 arguments: UDP port; optionally database user and password; optionally database host and name. With only a port argument, credentials come from `PGUSER` and `PGPASSWORD`. When not provided as arguments, database host and name come from `PGHOST` and `PGDATABASE`, defaulting to `pg` and `studs`. `PGPORT` is not used.

Register or sign in, then enter `help`. Use `add` to enter a city interactively. `exit` closes the client; entering `exit` in the server console stops the server. No separate `save` operation is needed because changes are persisted in PostgreSQL.

### Educational status

Source files and `pom.xml` are preserved byte-for-byte from the original archive. Only this README was added; IDE metadata and compiled `.class` files from the archive are omitted from the repository layout. This is a learning and demonstration project. The current implementation uses MD5 password hashes and unencrypted UDP communication; use test credentials in a trusted environment. The archive contains no automated tests. A build and integration run were not performed in the repository preparation environment.
