---
id: feature-send-message
title: Отправка письма через сабмишн-релей
type: feature
status: active
owner: unassigned
involved_services:
  - smtp-client
  - smtp-core
  - smtp-transport-ktor
api:
  - protocol-smtp
tags: [core]
---

# Отправка письма через сабмишн-релей

## 1. Суть

Сервис на Kotlin/Native отдаёт письмо своему релею и получает внятный ответ: кому доставлено, кому
отказано и стоит ли повторять. Без JVM, без `sendmail`, без обёртки над libcurl.

Сейчас закрыт разговор по открытому каналу без аутентификации: соединение, приветствие, `EHLO`,
транзакция, `QUIT`. TLS — M4, `AUTH` — M5.

## 2. Бизнес-ограничения

* **Частичный отказ — это результат, а не исключение.** Если из трёх получателей принят один,
  вызывающий обязан узнать, кто именно принят: иначе повтор превращается в дубль.
* **Временный и постоянный отказ различимы.** 4xx имеет смысл повторить, 5xx — нет
  (`docs/rfc/rfc5321.txt:2642`).
* **Ожидания не короче минимумов RFC** (`docs/rfc/rfc5321.txt:3610`). Урезать может только
  потребитель и осознанно.
* **Список расширений действителен только внутри фазы.** После `STARTTLS` прежний список
  недействителен, и попытка им воспользоваться — ошибка, а не устаревшие данные
  (`docs/rfc/rfc3207.txt:210`).
* **Строка из одной точки в теле письма не обрывает письмо** (`docs/rfc/rfc5321.txt:3423`).

## 3. Как это работает

```
KtorTcpTransport.connect ──▶ openSmtpSession ──▶ 220 ──▶ EHLO (или HELO) ──▶ Capabilities
                                                                    │
                          session.send(envelope, body) ◀────────────┘
                                    │
              MAIL FROM ──▶ RCPT TO × n ──▶ DATA ──▶ тело + CRLF.CRLF ──▶ DeliveryResult
                                    │
                          все отказаны? ──▶ RSET, DATA не отправляется
```

## 4. Якоря кода

| Модуль | Код |
|---|---|
| [smtp-client](../services/smtp-client.md) | `src/commonMain/.../client/SmtpSession.kt` — сессия, транзакция, таймауты |
| [smtp-core](../services/smtp-core.md) | `src/commonMain/.../protocol/` — команды, ответы, адреса, прозрачность |
| [smtp-transport-ktor](../services/smtp-transport-ktor.md) | `src/commonMain/.../transport/ktor/KtorTcpTransport.kt` |
| [smtp-testing](../services/smtp-testing.md) | `src/commonMain/.../testing/ScriptedTransport.kt` — сценарии для тестов |

## 5. Сценарии

### Сценарий: письмо принято одним получателем
* **Дано:** сервер приветствует `220` и отвечает `250` на `EHLO`.
* **Когда:** вызывается `send` с одним получателем.
* **Тогда:** уходят `MAIL FROM`, `RCPT TO`, `DATA`, тело и точка; `DeliveryResult.accepted`
  содержит получателя, `rejected` пуст, `acceptance` несёт ответ `250`.
* **Автоматизирован:** `SmtpSessionTest.sending a message walks MAIL then RCPT then DATA`

### Сценарий: часть получателей отвергнута
* **Дано:** сервер отвечает `250` на первого получателя и `550 5.1.1` на второго.
* **Когда:** вызывается `send` с обоими.
* **Тогда:** письмо всё равно отправляется принятому; `rejected` содержит второго с кодом `550` и
  расширенным кодом `5.1.1`. Исключения нет.
* **Автоматизирован:** `SmtpSessionTest.a rejected recipient is data rather than an exception`

### Сценарий: отвергнуты все получатели
* **Дано:** сервер отвечает `550` единственному получателю.
* **Когда:** вызывается `send`.
* **Тогда:** уходит `RSET`, `DATA` **не** отправляется, `acceptance` пуст, соединение остаётся
  пригодным для следующего письма.
* **Автоматизирован:** `SmtpSessionTest.with every recipient rejected the transaction is reset and DATA is never sent`

### Сценарий: отправитель отвергнут навсегда
* **Дано:** сервер отвечает `550 5.7.1` на `MAIL FROM`.
* **Когда:** вызывается `send`.
* **Тогда:** бросается `SmtpRefusedException` с `command = "MAIL FROM"` и `isPermanent = true`.
* **Автоматизирован:** `SmtpSessionTest.a refused MAIL FROM is a permanent failure`

### Сценарий: сервер занят
* **Дано:** сервер отвечает `451 4.3.0` на `MAIL FROM`.
* **Тогда:** `SmtpRefusedException.isTransient = true` — повтор осмыслен.
* **Автоматизирован:** `SmtpSessionTest.a 4xx refusal is transient and worth retrying`

### Сценарий: сервер старый и не знает EHLO
* **Дано:** сервер отвечает `500` на `EHLO`.
* **Тогда:** уходит `HELO`, сессия открывается, список расширений пуст.
* **Автоматизирован:** `SmtpSessionTest.a server that does not understand EHLO gets HELO`

### Сценарий: сервер молчит
* **Дано:** сервер не присылает приветствия.
* **Тогда:** через `SmtpTimeouts.greeting` (по умолчанию 5 минут) бросается `SmtpTimeoutException`.
* **Автоматизирован:** `SmtpSessionTest.a silent server trips the greeting timeout`

### Сценарий: письмо доезжает до настоящего сервера
* **Дано:** поднят `docker-compose.yml`, тело письма содержит строку из одной точки.
* **Тогда:** сервер принимает письмо целиком, и в нём видно содержимое после точки.
* **Автоматизирован:** `SmtpE2eTest.a real server accepts a message`

## 6. Что не входит в скоуп

* **Прямая доставка на MX** — нужен DNS-резолвер с MX-запросами, которого в Kotlin/Native нет
  ([риск 4](../research/research-architecture.md)).
* **Построение письма** — тело передаётся готовыми строками RFC 5322; MIME это M9.
* **PIPELINING** — команды идут по одной; группировка это M-60.
* **Очередь и повторы** — библиотека сообщает, что отказ временный, но не решает за потребителя,
  когда повторять.

## 7. Известные особенности

* **`QUIT` не проверяет ответ.** Сессия заканчивается в любом случае, и исключение здесь только
  замаскировало бы то, чем на самом деле занимался вызывающий.
* **`Capabilities`, полученные до `STARTTLS`, после рукопожатия бросают исключение.** Это не баг:
  клиент, доверяющий списку расширений «до TLS», уговаривается на downgrade
  (`docs/rfc/rfc3207.txt:210`).
* **Пустой список получателей не запрещён:** уйдут `MAIL FROM` и сразу `RSET`. Отдельной проверки
  нет намеренно — поведение предсказуемо и безвредно.
