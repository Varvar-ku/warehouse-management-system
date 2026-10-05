CREATE TABLE warehouses (
                            id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            lock_version BIGINT       NOT NULL DEFAULT 0,        -- оптимистическая блокировка
                            code         VARCHAR(50)  NOT NULL UNIQUE,
                            name         VARCHAR(255) NOT NULL,
                            address      TEXT,
                            is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
                            created_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            updated_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sections (
                          id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          lock_version       BIGINT       NOT NULL DEFAULT 0,
                          warehouse_id       UUID         NOT NULL REFERENCES warehouses (id),
                          code               VARCHAR(50)  NOT NULL,
                          name               VARCHAR(255) NOT NULL,
                          section_type       VARCHAR(50),                      -- тип хранения (стеллаж, пол и т. п.)
                          length_min         NUMERIC(18, 4),                   -- размеры в миллиметрах
                          length_max         NUMERIC(18, 4),
                          width_min          NUMERIC(18, 4),
                          width_max          NUMERIC(18, 4),
                          height_min         NUMERIC(18, 4),
                          height_max         NUMERIC(18, 4),
                          max_capacity       NUMERIC(18, 4),
                          capacity_unit_code VARCHAR(20),                      -- единица вместимости (код из catalog-service)
                          zone               VARCHAR(100),
                          rack               VARCHAR(100),
                          status             VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
                          priority           INT          NOT NULL DEFAULT 0,
                          is_unassigned_zone BOOLEAN      NOT NULL DEFAULT FALSE,
                          created_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- код уникален в пределах склада, а не глобально (п. 18 ТЗ: несколько складов)
                          CONSTRAINT uq_sections_warehouse_code UNIQUE (warehouse_id, code),
                          CONSTRAINT ck_sections_status CHECK (status IN ('ACTIVE', 'BLOCKED', 'ARCHIVED')),
                          CONSTRAINT ck_sections_length CHECK (length_min IS NULL OR length_max IS NULL OR length_min <= length_max),
                          CONSTRAINT ck_sections_width  CHECK (width_min  IS NULL OR width_max  IS NULL OR width_min  <= width_max),
                          CONSTRAINT ck_sections_height CHECK (height_min IS NULL OR height_max IS NULL OR height_min <= height_max),
    -- вместимость и её единица задаются только вместе
                          CONSTRAINT ck_sections_capacity CHECK ((max_capacity IS NULL) = (capacity_unit_code IS NULL)
                              AND (max_capacity IS NULL OR max_capacity > 0))
);

-- в каждом складе ровно одна зона «Нераспределено»
CREATE UNIQUE INDEX uq_sections_unassigned_zone ON sections (warehouse_id) WHERE is_unassigned_zone;
CREATE INDEX idx_sections_warehouse_status ON sections (warehouse_id, status);

-- Ограничения секции: пустой набор = «любой»
CREATE TABLE section_allowed_product_types (
                                               section_id        UUID        NOT NULL REFERENCES sections (id),
                                               product_type_code VARCHAR(50) NOT NULL,
                                               PRIMARY KEY (section_id, product_type_code)
);

CREATE TABLE section_allowed_materials (
                                           section_id    UUID         NOT NULL REFERENCES sections (id),
                                           material_code VARCHAR(150) NOT NULL,
                                           PRIMARY KEY (section_id, material_code)
);

CREATE TABLE placement_rules (
                                 id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 lock_version      BIGINT       NOT NULL DEFAULT 0,
                                 rule_version      INT          NOT NULL DEFAULT 1,    -- бизнес-версия: растёт при каждом изменении правила
                                 name              VARCHAR(255) NOT NULL,
                                 priority          INT          NOT NULL DEFAULT 0,
                                 is_active         BOOLEAN      NOT NULL DEFAULT TRUE,
                                 is_fallback       BOOLEAN      NOT NULL DEFAULT FALSE, -- резервное правило (применяется, если точного соответствия нет)
    -- JSON хранится как текст: правила читаются целиком и разбираются в Java, поиска внутри JSON нет.
    -- CHECK гарантирует, что в колонке всегда корректный JSON-объект (PostgreSQL 16+).
                                 conditions_json   TEXT         NOT NULL DEFAULT '{}' CHECK (conditions_json IS JSON OBJECT),
    target_section_id UUID         NOT NULL REFERENCES sections (id),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_placement_rules_target ON placement_rules (target_section_id);

-- История версий: поступление ссылается на (rule_id, version), поэтому старые версии не удаляются
CREATE TABLE placement_rule_versions (
                                         id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                         rule_id    UUID        NOT NULL REFERENCES placement_rules (id),
                                         version    INT         NOT NULL,
                                         snapshot   TEXT        NOT NULL CHECK (snapshot IS JSON OBJECT),
    changed_by UUID,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_rule_version UNIQUE (rule_id, version)
);