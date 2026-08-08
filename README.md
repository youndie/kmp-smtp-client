# kmp-smtp-client

SMTP-клиент для Kotlin Multiplatform, сделанный ради одной вещи: **отправить письмо из сервиса на
Kotlin/Native**, не таща за собой JVM.

На JVM задача решена давно (Jakarta Mail, Simple Java Mail). На Kotlin/Native отправить письмо
сегодня нечем — остаётся дёрнуть `sendmail` через `system()` или обернуть libcurl. Эта библиотека
закрывает пробел: чистая реализация RFC 5321 поверх сокетов, с STARTTLS, AUTH и разбором
расширений ESMTP.

> **Статус: M0 закрыта.** Есть сборка, ktlint, CI и первый работающий кусок протокола — разбор
> ответа сервера. Отправить письмо ещё нельзя: сессия делается на M2, транспорт на M3, TLS на M4.

## Что планируется

- сабмишн по RFC 6409: порт 587 со `STARTTLS` и порт 465 с implicit TLS;
- `AUTH`: `PLAIN`, `LOGIN`, `CRAM-MD5`, `SCRAM-SHA-1/256`, `XOAUTH2`, `OAUTHBEARER`;
- расширения: `PIPELINING`, `SIZE`, `8BITMIME`, `SMTPUTF8`, `DSN`, `ENHANCEDSTATUSCODES`,
  `CHUNKING`;
- платформы по порядку: `linuxX64` → `jvm` → `linuxArm64`, `mingwX64`, `macos*` → apple → Node.

Вне скоупа: прямая доставка на MX (нужен DNS-резолвер с MX-запросами, которого в Kotlin/Native
нет), приём почты, IMAP/POP.

## Как это устроено

Ядро не знает про ввод-вывод: парсер ответов, сериализатор команд, разбор расширений и машина
состояний — функции над строками и байтами. Сокеты, TLS и SASL подключаются как реализации портов.
Благодаря этому 90% тестов идут без сети, а тест пишется раньше кода — что здесь не пожелание, а
принятый процесс.

Ktor используется только как транспорт TCP (`ktor-network`) и заперт в одном модуле-адаптере.
TLS свой: на Kotlin/Native `ktor-network-tls` не работает вовсе — почему и что с этим делать,
разобрано в ресёрче.

## Документация

- [docs/research/research-architecture.md](docs/research/research-architecture.md) — почему всё
  устроено именно так, что проверено, чем платим;
- [docs/api/protocol-smtp.md](docs/api/protocol-smtp.md) — контракт SMTP на проводе;
- [docs/rfc/](docs/rfc/) — 42 копии RFC, на которые ссылаются тесты;
- [BACKLOG.md](BACKLOG.md) — вехи и задачи;
- [docs/README.md](docs/README.md) — карта документации;
- [CONTRIBUTING.md](CONTRIBUTING.md) — как здесь работают: тест по RFC → код → GATE.

## Лицензия

[MIT](LICENSE).
