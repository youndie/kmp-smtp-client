---
id: smtp-transport-ktor
title: smtp-transport-ktor — TCP-транспорт
type: service
status: active
module: :smtp-transport-ktor
tech_stack: [Kotlin Multiplatform, ktor-network]
targets: [linuxX64, macosArm64, jvm]
owner: unassigned
depends_on:
  - smtp-core
publishes:
  - io.github.youndie:smtp-transport-ktor
---

# smtp-transport-ktor

## 1. Зона ответственности

Реализация порта `SmtpTransport` поверх `ktor-network`: соединиться, отдать строку, прочитать
строку, закрыться. Единственное место в проекте, где вообще встречается слово ktor
([research D1](../research/research-architecture.md)).

**Чем не занимается:** протоколом. Модуль не знает ни одной команды SMTP и не разбирает ни одного
ответа — он режет поток на строки и всё. Обратное тоже верно: `:smtp-core` не знает про сокеты.

**TLS сюда не попадёт.** `ktor-network-tls` на Kotlin/Native — заглушка, падающая в рантайме
([research 1.1](../research/research-architecture.md)); TLS делается отдельным модулем на M4.
Подключение `ktor-network-tls` в этот модуль — ошибка, а не удобство.

## 2. Контракт

Порт — `SmtpTransport` в `:smtp-core`, `src/commonMain/kotlin/io/github/youndie/smtp/transport/`.
Умышленно узкий: строки на вход и на выход, никаких `ByteReadChannel` и `Source`
([research D8](../research/research-architecture.md)).

## 2а. Ключевые файлы (якоря кода)

| Файл | Что там |
|---|---|
| `smtp-core/src/commonMain/.../transport/SmtpTransport.kt` | порт и `SmtpTransportException` |
| `src/commonMain/.../transport/ktor/KtorTcpTransport.kt` | реализация |
| `src/commonTest/.../KtorTcpTransportTest.kt` | тесты против сервера в том же процессе |
| `src/commonTest/.../SmtpE2eTest.kt` | разговор с настоящим сервером из `docker-compose.yml` |

## 3. Как устроено

Чтение и запись идут через два независимых канала Ktor, поэтому ответ читается, пока группа команд
ещё пишется. Это не оптимизация: без такого разделения PIPELINING упирается в дедлок, как только
группа команд перестаёт помещаться в TCP-окно (`docs/rfc/rfc2920.txt:183`).

`SelectorManager` создаётся на соединение и закрывается последним: на нативных таргетах он владеет
рабочим потоком, на котором крутится опрос.

## 4. Зависимости

| Тип | Имя | Для чего |
|---|---|---|
| Модуль | [smtp-core](smtp-core.md) | порт `SmtpTransport` |
| Библиотека | `io.ktor:ktor-network` | сокеты и селектор |

## 5. Тесты

```bash
./gradlew :smtp-transport-ktor:macosArm64Test          # юнит-тесты, сервер поднимается в процессе
docker compose up -d                                    # SMTP-сервер для E2E
SMTP_E2E_HOST=127.0.0.1 ./gradlew :smtp-transport-ktor:linuxX64Test
```

Без `SMTP_E2E_HOST` E2E-тест печатает `SKIPPED` и выходит: `kotlin.test` пропускать не умеет.
Чтобы пропуск не превратился в тихий гейт, в CI выставлен `SMTP_E2E_REQUIRED=1` — тогда
отсутствие адреса сервера это провал, а не тишина.

## 6. Сознательные ограничения / грабли

* **`SmtpTransport.write` принимает строку, а не байты.** Для `BDAT` и `BINARYMIME`
  (`docs/rfc/rfc3030.txt`) этого не хватит — порт придётся расширять на M-66. Сейчас байтового
  метода нет намеренно: его нечем протестировать.
* **`readUTF8Line` из Ktor устарел** в 3.5 в пользу `readLine`; на `-Werror` старый вызов роняет
  сборку. Если после обновления Ktor модуль перестанет собираться — смотреть сюда.
* **Mailpit принимает почти всё.** E2E доказывает, что сервер согласен, а не что клиент строг;
  проверка против настоящего Postfix заведена как M-37.
