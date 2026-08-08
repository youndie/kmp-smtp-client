# Копии RFC

42 документа, скачанные с `rfc-editor.org` 2026-08-08. Лежат в репозитории намеренно: тест в этом
проекте пишется по цитате из спецификации, и цитата должна быть проверяема offline — ссылка вида
`rfc5321.txt:3510` открывается прямо здесь.

RFC распространяются свободно в неизменном виде (BSD-подобное разрешение IETF Trust на копирование
и распространение полного текста); файлы не редактируются.

Обновление и добавление:

```bash
curl -sSO https://www.rfc-editor.org/rfc/rfc<NNNN>.txt
```

## Ядро протокола — читать первым

| RFC | Название | Зачем здесь |
|---|---|---|
| [5321](rfc5321.txt) | Simple Mail Transfer Protocol | сам протокол: команды, ответы, лимиты, таймауты, прозрачность |
| [5322](rfc5322.txt) | Internet Message Format | формат самого письма: заголовки, адреса, свёртка строк |
| [6409](rfc6409.txt) | Message Submission for Mail | сабмишн (587) как отдельный режим со своими правилами — это и есть наш сценарий |

## Расширения ESMTP

| RFC | Название | Веха |
|---|---|---|
| [1870](rfc1870.txt) | SMTP Service Extension for Message Size Declaration (`SIZE`) | M6 |
| [2034](rfc2034.txt) | SMTP Service Extension for Returning Enhanced Error Codes | M6 |
| [2920](rfc2920.txt) | SMTP Service Extension for Command Pipelining | M6 |
| [3030](rfc3030.txt) | Transmission of Large and Binary MIME Messages (`CHUNKING`/`BDAT`, `BINARYMIME`) | M6 |
| [6152](rfc6152.txt) | SMTP Service Extension for 8-bit MIME Transport (`8BITMIME`) | M6 |
| [3461](rfc3461.txt) | Delivery Status Notifications (`DSN`: `NOTIFY`, `ORCPT`, `RET`, `ENVID`) | M6 |
| [3464](rfc3464.txt) | An Extensible Message Format for Delivery Status Notifications | M6 |
| [3463](rfc3463.txt) | Enhanced Mail System Status Codes | M1 |
| [5248](rfc5248.txt) | A Registry for SMTP Enhanced Mail System Status Codes | M1 |
| [3798](rfc3798.txt) | Message Disposition Notification | справочно |
| [8689](rfc8689.txt) | SMTP Require TLS Option (`REQUIRETLS`) | вне v1 |

## TLS

| RFC | Название | Веха |
|---|---|---|
| [3207](rfc3207.txt) | SMTP Service Extension for Secure SMTP over TLS (`STARTTLS`) | M4 |
| [8314](rfc8314.txt) | Cleartext Considered Obsolete: TLS for Email Submission and Access | M4 |
| [7817](rfc7817.txt) | Updated TLS Server Identity Check Procedure for Email-Related Protocols | M4 |
| [9525](rfc9525.txt) | Service Identity in the Internet PKI Using X.509 | M4 |

Порядок чтения для M4: 8314 (какой режим выбирать), 3207 (как переключаться), 7817+9525 (что
именно сверять в сертификате). Пропуск двух последних — это и есть «TLS, который ничего не
проверяет».

## Аутентификация (SASL)

| RFC | Название | Веха |
|---|---|---|
| [4954](rfc4954.txt) | SMTP Service Extension for Authentication (`AUTH`) | M5 |
| [4422](rfc4422.txt) | Simple Authentication and Security Layer (SASL) | M5 |
| [4616](rfc4616.txt) | The `PLAIN` SASL Mechanism | M5 |
| [2195](rfc2195.txt) | `CRAM-MD5` | M5 |
| [5802](rfc5802.txt) | `SCRAM-SHA-1` / `SCRAM-SHA-1-PLUS` | M5 |
| [7677](rfc7677.txt) | `SCRAM-SHA-256` / `SCRAM-SHA-256-PLUS` | M5 |
| [7628](rfc7628.txt) | SASL-механизмы для OAuth (`OAUTHBEARER`) | M5 |
| [4013](rfc4013.txt) | SASLprep: профиль stringprep для логинов и паролей | M5 |
| [8265](rfc8265.txt) | PRECIS: логины и пароли (пришёл на смену 4013) | M5 |
| [5056](rfc5056.txt) | Channel bindings (для механизмов `-PLUS`) | M5, опционально |
| [9266](rfc9266.txt) | Channel Bindings for TLS 1.3 (`tls-exporter`) | M5, опционально |

`LOGIN` своего RFC не имеет — это де-факто механизм; спецификация только в черновике Microsoft.
Реализуется ради совместимости, документируется как таковой.

## MIME — понадобится в `:smtp-mime`

| RFC | Название |
|---|---|
| [2045](rfc2045.txt) | MIME Part One: Format of Internet Message Bodies |
| [2046](rfc2046.txt) | MIME Part Two: Media Types |
| [2047](rfc2047.txt) | MIME Part Three: Message Header Extensions for Non-ASCII Text |
| [2049](rfc2049.txt) | MIME Part Five: Conformance Criteria and Examples |
| [2183](rfc2183.txt) | `Content-Disposition` |
| [2231](rfc2231.txt) | Параметры MIME: продолжения, кодировки, языки |

## Интернационализация (EAI)

| RFC | Название |
|---|---|
| [6530](rfc6530.txt) | Overview and Framework for Internationalized Email |
| [6531](rfc6531.txt) | SMTP Extension for Internationalized Email (`SMTPUTF8`) |
| [6532](rfc6532.txt) | Internationalized Email Headers |
| [5890](rfc5890.txt) | IDNA: Definitions and Document Framework |
| [5891](rfc5891.txt) | IDNA: Protocol |
| [3492](rfc3492.txt) | Punycode |

Последние три нужны только потому, что в Kotlin/Native нет аналога `java.net.IDN` — см. research,
раздел 1.3.

## Справочно, вне скоупа v1

| RFC | Название | Почему лежит |
|---|---|---|
| [6376](rfc6376.txt) | DomainKeys Identified Mail (DKIM) Signatures | открытый вопрос 3 в research: подписывать ли исходящее |

Устаревшие версии (821, 2821 — SMTP; 822, 2822 — формат письма; 2487 — STARTTLS; 1653 — SIZE) не
скачивались намеренно: ссылаться на них в тестах нельзя, а путать их с актуальными легко.
