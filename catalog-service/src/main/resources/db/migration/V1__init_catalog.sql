-- Единицы измерения
CREATE TABLE units (
                       id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),  -- gen_random_uuid() встроена в PostgreSQL с 13-й версии
                       code VARCHAR(20) NOT NULL UNIQUE,
                       name VARCHAR(50) NOT NULL
);

-- Типы деталей
CREATE TABLE product_types (
                               id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               code        VARCHAR(50)  NOT NULL UNIQUE,
                               name        VARCHAR(150) NOT NULL,
                               description TEXT,
                               is_active   BOOLEAN NOT NULL DEFAULT TRUE
);

-- Определения атрибутов: задают тип значения (строка/число/логика/справочник)
CREATE TABLE attribute_definitions (
                                       id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                       code      VARCHAR(100) NOT NULL UNIQUE,
                                       name      VARCHAR(150) NOT NULL,
                                       data_type VARCHAR(20)  NOT NULL
                                           CONSTRAINT ck_attr_def_type CHECK (data_type IN ('STRING', 'NUMBER', 'BOOLEAN', 'REFERENCE')),
                                       is_active BOOLEAN NOT NULL DEFAULT TRUE
);

-- Допустимые значения для атрибутов типа REFERENCE
CREATE TABLE attribute_options (
                                   attribute_id UUID NOT NULL REFERENCES attribute_definitions (id),
                                   value        VARCHAR(150) NOT NULL,
                                   PRIMARY KEY (attribute_id, value)
);

-- Номенклатура
CREATE TABLE products (
                          id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          version         BIGINT       NOT NULL DEFAULT 0,      -- оптимистическая блокировка
                          sku             VARCHAR(100) NOT NULL UNIQUE,
                          name            VARCHAR(255) NOT NULL,
                          product_type_id UUID         NOT NULL REFERENCES product_types (id),
                          unit_id         UUID         NOT NULL REFERENCES units (id),
                          description     TEXT,
                          is_active       BOOLEAN      NOT NULL DEFAULT TRUE,   -- FALSE = в архиве
                          created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Значения атрибутов конкретной номенклатуры
CREATE TABLE product_attributes (
                                    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                    product_id     UUID         NOT NULL REFERENCES products (id),
                                    attribute_code VARCHAR(100) NOT NULL REFERENCES attribute_definitions (code),
                                    value_string   TEXT,
                                    value_number   NUMERIC(18, 4),
                                    value_boolean  BOOLEAN,
                                    value_unit_id  UUID REFERENCES units (id),
    -- один атрибут на товар
                                    CONSTRAINT uq_product_attribute UNIQUE (product_id, attribute_code),
    -- заполнено ровно одно значение
                                    CONSTRAINT ck_one_value CHECK (num_nonnulls(value_string, value_number, value_boolean) = 1)
);

CREATE INDEX idx_products_type ON products (product_type_id);
CREATE INDEX idx_products_name ON products (lower(name));
CREATE INDEX idx_product_attributes_code ON product_attributes (attribute_code);