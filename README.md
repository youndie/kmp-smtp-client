# kmp-smtp-client

SMTP-клиент для Kotlin Multiplatform, сделанный ради одной вещи: **отправить письмо из сервиса на
Kotlin/Native**, не таща за собой JVM.

На JVM задача решена давно (Jakarta Mail, Simple Java Mail). На Kotlin/Native отправить письмо
сегодня нечем — остаётся дёрнуть `sendmail` через `system()` или обернуть libcurl. Эта библиотека
закрывает пробел: чистая реализация RFC 5321 поверх сокетов, с STARTTLS, AUTH и разбором
расширений ESMTP.

> **Статус: функциональность закрыта, релиза ещё не было.** Вехи M0–M9 сделаны: протокол, сессия,
> TLS, аутентификация, расширения ESMTP, построение письма. 173 теста на `linuxX64`, из них шесть
> против настоящего TLS-сервера в контейнере. Публикация настроена, но не выполнена —
> см. [RELEASING.md](RELEASING.md).

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

## Быстрый старт

```kotlin
dependencies {
    implementation("io.github.youndie:smtp-client:0.1.0")
    implementation("io.github.youndie:smtp-transport-ktor:0.1.0")  // сокеты
    implementation("io.github.youndie:smtp-tls-openssl:0.1.0")     // TLS на Kotlin/Native
    implementation("io.github.youndie:smtp-tls-jvm:0.1.0")         // TLS на JVM
    implementation("io.github.youndie:smtp-sasl:0.1.0")            // аутентификация
    implementation("io.github.youndie:smtp-mime:0.1.0")            // построение письма
}
```

Сабмишн через релей — порт 587, `STARTTLS`, `AUTH`:

```kotlin
val transport = connectSmtp("smtp.example.com", 587)
val session = openSmtpSession(transport, SmtpClientConfig(clientIdentity = "my-service.example.com"))

session.startTls(OpenSslTlsProvider, TlsConfig(serverName = "smtp.example.com"))
session.authenticate(PlainMechanism(username = "user", password = "secret"))

val result = session.send(envelope, body)
session.quit()
```

Полный рабочий пример — [examples/send](examples/send/src/commonMain/kotlin/Main.kt); он
компилируется каждой сборкой, поэтому не может протухнуть незаметно.

<details>
<summary>Без TLS, как это выглядит целиком</summary>

```kotlin
val transport = connectSmtp("smtp.example.com", 587)
val session = openSmtpSession(transport, SmtpClientConfig(clientIdentity = "my-service.example.com"))

val result = session.send(
    envelope = Envelope(
        sender = Mailbox.parse("noreply@example.com"),
        recipients = listOf(Mailbox.parse("user@example.com")),
    ),
    body = listOf("Subject: hello", "", "text"),
)

println(result.accepted)   // кому доставлено
println(result.rejected)   // кому отказано и с каким кодом
session.quit()
```

</details>

## Документация

- [docs/research/research-architecture.md](docs/research/research-architecture.md) — почему всё
  устроено именно так, что проверено, чем платим;
- [docs/api/protocol-smtp.md](docs/api/protocol-smtp.md) — контракт SMTP на проводе;
- [docs/rfc/](docs/rfc/) — 42 копии RFC, на которые ссылаются тесты;
- [BACKLOG.md](BACKLOG.md) — вехи и задачи;
- [docs/README.md](docs/README.md) — карта документации;
- [CONTRIBUTING.md](CONTRIBUTING.md) — как здесь работают: тест по RFC → код → GATE, и как гонять
  E2E против сервера из [docker-compose.yml](docker-compose.yml).

## Лицензия

[MIT](LICENSE).
