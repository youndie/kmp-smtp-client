---
id: smtp-sasl
title: smtp-sasl — механизмы аутентификации
type: service
status: active
module: :smtp-sasl
tech_stack: [Kotlin Multiplatform, KotlinCrypto]
targets: [linuxX64, macosArm64, jvm]
owner: unassigned
depends_on:
  - smtp-core
publishes:
  - io.github.youndie:smtp-sasl
---

# smtp-sasl

## 1. Зона ответственности

Механизмы SASL как автоматы над байтами: `PLAIN`, `LOGIN`, `CRAM-MD5`, `SCRAM-SHA-1`,
`SCRAM-SHA-256`, `XOAUTH2`, `OAUTHBEARER`. Плюс подготовка логина и пароля (`SaslPrep`).

**Чем не занимается:** SMTP. Base64 и обмен `334`-челленджами — это профиль SASL для SMTP
(`docs/rfc/rfc4954.txt:699`), он живёт в [smtp-client](smtp-client.md). Механизм, знающий про
base64, нельзя переиспользовать ни в одном другом протоколе.

## 2. Контракт

`SaslMechanism` из [smtp-core](smtp-core.md),
`src/commonMain/kotlin/io/github/youndie/smtp/sasl/SaslMechanism.kt`.

## 2а. Ключевые файлы (якоря кода)

| Файл | Что там |
|---|---|
| `src/commonMain/.../Mechanisms.kt` | все семь механизмов |
| `src/commonMain/.../Crypto.kt` | HMAC, `Hi` (PBKDF2), hex, нонсы |
| `src/commonMain/.../SaslPrep.kt` | подготовка логина и пароля |
| `src/commonTest/.../MechanismsTest.kt` | векторы из RFC 2195, 5802, 7677 |

## 3. Как устроено

Тесты построены на **векторах из самих RFC**: цифры из `rfc2195.txt:152`, полные обмены из
`rfc5802.txt:496` и `rfc7677.txt:126`. Вектор из спецификации ловит реализацию, ошибающуюся
одинаково в двух местах, — чего самосогласованный тест не умеет.

`Hi` (PBKDF2 на один блок) написан руками: у KotlinCrypto нет модуля KDF, а один блок — это цикл
по HMAC.

Нонсы берутся из `org.kotlincrypto.random:crypto-rand`, а не из `kotlin.random.Random`:
безопасность SCRAM опирается на непредсказуемость клиентского нонса.

## 4. Зависимости

| Тип | Имя | Для чего |
|---|---|---|
| Модуль | [smtp-core](smtp-core.md) | `SaslMechanism` |
| Библиотека | KotlinCrypto `hmac-md`, `hmac-sha1`, `hmac-sha2`, `sha1`, `sha2` | HMAC и хеши |
| Библиотека | KotlinCrypto `crypto-rand` | нонсы |

## 5. Сознательные ограничения / грабли

* **`SaslPrep` не делает нормализацию NFKC.** В common-коде Kotlin нормализации нет, а тащить
  таблицы — удвоить модуль. Следствие узкое и честное: пароль, набранный в одной форме
  нормализации и сохранённый в другой, не подойдёт. ASCII это не задевает. Заведено как M-58a.
* **Channel binding (`-PLUS`) не реализован.** Сообщение объявляет `n` — «клиент не умеет».
  Реализация потребует `tls-exporter` из TLS-слоя (`docs/rfc/rfc9266.txt`), заведено как M-56a.
* **`LOGIN` различает шаги по порядку, а не по тексту приглашения.** Текст не стандартизован, и
  сервер с переводом на другой язык сломал бы сопоставление по строке.
* **`CRAM-MD5` оставлен ради старых релеев.** MD5 давно пора на покой, а сервер обязан хранить
  пароль восстановимо; где есть выбор — `SCRAM`.
