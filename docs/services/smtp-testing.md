---
id: smtp-testing
title: smtp-testing — сценарный транспорт
type: service
status: active
module: :smtp-testing
tech_stack: [Kotlin Multiplatform]
targets: [linuxX64, macosArm64, jvm]
owner: unassigned
depends_on:
  - smtp-core
publishes:
  - io.github.youndie:smtp-testing
---

# smtp-testing

## 1. Зона ответственности

`ScriptedTransport` — реализация `SmtpTransport`, проигрывающая записанный разговор: что говорит
сервер и что обязан написать клиент, по шагам.

Публикуется наравне с остальными модулями: тем, кто строит что-то поверх библиотеки, он нужен ровно
так же, как нам.

## 2. Контракт

Порт `SmtpTransport` из [smtp-core](smtp-core.md).

## 2а. Ключевые файлы (якоря кода)

| Файл | Что там |
|---|---|
| `src/commonMain/.../testing/ScriptedTransport.kt` | сценарий, сборщик, проверки |

## 3. Как устроено

Сценарий — плоский список шагов: `serverSays`, `clientWrites`, `serverCloses`, `serverHangs`.
Любое расхождение — `AssertionError` с текстом, где видно и ожидаемое, и написанное (`CRLF`
показывается как `\r\n`, чтобы сообщение осталось однострочным).

`serverHangs` не спит, а `awaitCancellation()`: под виртуальным временем `runTest` тест на таймаут
проходит мгновенно.

## 4. Зависимости

| Тип | Имя | Для чего |
|---|---|---|
| Модуль | [smtp-core](smtp-core.md) | порт `SmtpTransport` |
| Библиотека | `kotlinx-coroutines-core` | `awaitCancellation` |

## 5. Сознательные ограничения / грабли

* **`assertScriptCompleted()` вызывается руками.** Забыть его — значит проверить только начало
  разговора; тест на это есть в самом модуле.
* **Сценарий строго последовательный.** PIPELINING (M-60) допускает запись нескольких команд до
  чтения — тогда понадобится шаг «клиент пишет группу», иначе сценарии станут ложью.
