CREATE TABLE roles (
                       id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       code VARCHAR(50)  NOT NULL UNIQUE,   -- USER / STOREKEEPER / WAREHOUSE_WORKER / ADMIN
                       name VARCHAR(100) NOT NULL
);

CREATE TABLE users (
                       id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       version       BIGINT       NOT NULL DEFAULT 0,       -- оптимистическая блокировка
                       login         VARCHAR(100) NOT NULL,
                       password_hash VARCHAR(255) NOT NULL,                 -- только хэш, пароль в открытом виде не хранится
                       full_name     VARCHAR(255) NOT NULL,
                       email         VARCHAR(255),
                       phone         VARCHAR(50),
                       is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
                       created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Логин нечувствителен к регистру: «Admin» и «admin» не могут существовать одновременно
CREATE UNIQUE INDEX uq_users_login_lower ON users (lower(login));

CREATE TABLE user_roles (
                            user_id UUID NOT NULL REFERENCES users (id),
                            role_id UUID NOT NULL REFERENCES roles (id),
                            PRIMARY KEY (user_id, role_id)
);