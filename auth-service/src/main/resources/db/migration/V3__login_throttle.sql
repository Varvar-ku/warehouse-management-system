-- Счётчики неудачных попыток входа (защита от перебора пароля).
-- Ключи: 'p:<логин>|<ip>' (пара логин + IP) и 'i:<ip>' (весь IP).
CREATE TABLE login_throttle (
    throttle_key   VARCHAR(255) PRIMARY KEY,
    failed_count   INTEGER      NOT NULL,
    last_failed_at TIMESTAMPTZ  NOT NULL,
    locked_until   TIMESTAMPTZ
);

CREATE INDEX ix_login_throttle_last_failed ON login_throttle (last_failed_at);
