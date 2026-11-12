# Пункт 1: XFF и глобальный DoS входа

## Что изменено
| Файл | Изменение |
|---|---|
| gateway/.../ClientIpFilter.java (новый) | Определяет IP клиента, затирает входящий X-Client-IP, передаёт сервисам свой. X-Forwarded-For учитывается только от прокси из `app.gateway.trusted-proxies` |
| gateway/.../application.yaml | Новое свойство `app.gateway.trusted-proxies` (`GATEWAY_TRUSTED_PROXIES`), по умолчанию пусто |
| auth/.../throttle/ClientIp.java | Читает X-Client-IP (только при loopback-соединении, формат проверяется), X-Forwarded-For не читает. `resolve(request, hops)` оставлен как deprecated-обёртка |
| auth/.../controller/AuthController.java | Убрано поле `trustedProxyHops` (`app.security.trusted-proxy-hops`), вызов `ClientIp.resolve(http)` |
| auth/.../throttle/ClientIpTest.java | **Заменяет** прежний файл: старые тесты проверяли X-Forwarded-For и hops |
| auth/.../throttle/LoginThrottleTest.java | Последний тест переписан под X-Client-IP, остальные без изменений |
| gateway/.../ClientIpFilterTest.java (новый) | Тесты фильтра |

AuthService, LoginThrottle{Service,Properties,Config,Cleanup}, CorsConfig и application.yaml auth не менялись. Тесты не запускались: в среде анализа нет Maven.
Пути указаны от корня репозитория, при отличии имён модулей копируйте по имени класса.
Свойство `app.security.trusted-proxy-hops` больше не читается; если оно где-то задано, его можно удалить.

## Установка
Скопировать файлы поверх проекта. Миграций нет.

## Проверка
1. До правки (диагностика отчёта): 6 неверных входов через gateway с разными `X-Forwarded-For`, затем
   `SELECT throttle_key FROM login_throttle WHERE throttle_key LIKE 'i:%';` в auth_db.
   Один ключ `i:127.0.0.1` при разных XFF подтверждает отчёт.
2. После правки, без trusted-proxies: тот же запрос с `X-Forwarded-For: 9.9.9.9` и `X-Client-IP: 8.8.8.8` даёт ключ с реальным адресом соединения (при curl с той же машины: `i:127.0.0.1` или `i:0:0:0:0:0:0:0:1`). Подделка не действует.
3. `GATEWAY_TRUSTED_PROXIES=127.0.0.1`, запрос с `X-Forwarded-For: 9.9.9.9`: ключ `i:9.9.9.9`.
4. Два разных реальных клиента не блокируют друг друга: 30 неудач с одного IP не мешают входу с другого.
5. Прямой вызов auth-service (порт 8081) с локальной машины и поддельным `X-Client-IP` возможен только с loopback.

## Ограничения
- Если auth-service запущен в контейнере (`SERVER_ADDRESS=0.0.0.0`), соединение от gateway не loopback, и X-Client-IP будет игнорироваться (все в одном счётчике). Тогда нужен список доверенных адресов и на стороне auth.
- За nginx в `GATEWAY_TRUSTED_PROXIES` указать его адрес, а в nginx использовать `proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;`.
- Лимит по IP (30) остаётся общим для всех клиентов за одним NAT.
