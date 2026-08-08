---
id: smtp-client
title: smtp-client — сессия и транзакции
type: service
status: active
module: :smtp-client
tech_stack: [Kotlin Multiplatform, kotlinx-coroutines]
targets: [linuxX64, macosArm64, jvm]
owner: unassigned
depends_on:
  - smtp-core
publishes:
  - io.github.youndie:smtp-client
---

# smtp-client

## 1. Зона ответственности

Разговор целиком: приветствие, `EHLO` с откатом на `HELO`, транзакция `MAIL`/`RCPT`/`DATA`, `RSET`,
`QUIT`, `STARTTLS`. Плюс то, что из разговора получается: `DeliveryResult`, классификация отказов,
таймауты.

**Чем не занимается:** сокетами и TLS. Транспорт приходит интерфейсом `SmtpTransport`,
рукопожатие TLS — лямбдой в `startTls`. Модуль не знает ни одной библиотеки ввода-вывода.

## 2. Контракт

Что уходит на провод — [protocol-smtp](../api/protocol-smtp.md); что это даёт пользователю —
[feature-send-message](../features/feature-send-message.md).

## 2а. Ключевые файлы (якоря кода)

| Файл | Что там |
|---|---|
| `src/commonMain/.../client/SmtpSession.kt` | всё: сессия, транзакция, таймауты, типы результата |
| `src/commonTest/.../SmtpSessionTest.kt` | 14 сценариев поверх сценарного транспорта |

Один файл — потому что сессия, её конфигурация и её результат читаются вместе; разносить их по
файлам пока значит только прыгать между ними.

## 3. Как устроено

Сессия держит один `SmtpReplyReader` на соединение и спрашивает **его**, закончился ли ответ, — не
разбирая строку сама. Иначе многострочные ответы пришлось бы понимать в двух местах
(`docs/rfc/rfc2920.txt:193`).

Каждое ожидание обёрнуто в `withTimeout` со своим значением из `SmtpTimeouts`. Значения по
умолчанию — минимумы RFC; поле `otherCommands` названо отдельно, чтобы придуманное значение
(`EHLO`, `RSET`, `QUIT`, `STARTTLS` — для них в RFC чисел нет) не выглядело процитированным.

`startTls` принимает лямбду рукопожатия и ничего не знает про TLS: это шов, в который на M4
включится `:smtp-tls-openssl`.

## 4. Зависимости

| Тип | Имя | Для чего |
|---|---|---|
| Модуль | [smtp-core](smtp-core.md) | протокол, порт транспорта |
| Библиотека | `kotlinx-coroutines-core` | `withTimeout` |
| Модуль (тесты) | [smtp-testing](smtp-testing.md) | сценарный транспорт |

## 5. Сознательные ограничения / грабли

* **`QUIT` не проверяет ответ** — см. Quirks в [feature-send-message](../features/feature-send-message.md).
* **Команды идут по одной.** PIPELINING (M-60) потребует другой формы `send`: группировать
  придётся `MAIL`+`RCPT`+`DATA` и разбирать ответы счётом (`docs/rfc/rfc2920.txt:177`).
* **Сессия не потокобезопасна** и не станет: одно соединение — одна корутина.
* **Сброс состояния после `AUTH` (M-22) написан, но не вызывается.** Механика живёт в `startTls`;
  подключится на M5, когда появится настоящий обмен AUTH.
