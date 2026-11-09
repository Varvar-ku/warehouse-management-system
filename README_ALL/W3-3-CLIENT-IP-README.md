# Волна 3, пункт 3: адрес клиента за nginx (M6)

## Проблема
`ClientIp` брал последний адрес `X-Forwarded-For`. Если перед gateway стоит nginx, gateway дописывает в конец адрес
nginx, и все пользователи оказываются в одном счётчике «30 неудач на IP»: тридцать неудачных входов блокируют вход всем.

## Что сделано (auth-service)
- `ClientIp.resolve(request, trustedHops)`: адрес клиента считается **справа**, с учётом числа доверенных прокси.
  Всё, что подставил сам клиент, остаётся левее и игнорируется. Прежний `resolve(request)` равен `trustedHops = 1`.
- `AuthController` берёт число прокси из `app.security.trusted-proxy-hops` (по умолчанию 1, поведение не меняется).
- `ClientIpTest`: без nginx, с nginx, подделанный заголовок, короткий список, запрос не с loopback, нет заголовка.

## Как включить за nginx
1. В `application.yaml` auth-service (в блок `app.security`):

       trusted-proxy-hops: 2      # nginx и gateway

2. nginx должен **дописывать** адрес, а не заменять:

       location /api/ {
           proxy_pass http://127.0.0.1:8080;
           proxy_set_header Host $host;
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
           proxy_set_header X-Forwarded-Proto $scheme;
       }

3. Gateway по умолчанию дописывает свой адрес в `X-Forwarded-For` (фильтр forwarded-заголовков Gateway MVC включён).
   Не отключайте его и не добавляйте между nginx и gateway других прокси без увеличения `trusted-proxy-hops`.

## Проверить
- Без nginx: `trusted-proxy-hops` не задавать (1); неверные входы с двух разных адресов считаются раздельно.
- С nginx: два клиента с разных адресов, 5 неверных паролей одного не блокируют второго (в `login_throttle` разные ключи `p:<логин>|<ip>`).
- Подделка: запрос с заголовком `X-Forwarded-For: 1.2.3.4` не меняет определённый адрес (в базе остаётся реальный).
- Если после включения все ключи содержат адрес nginx, значение `trusted-proxy-hops` слишком мало; если «чужой» адрес
  (левый элемент) слишком велико.
