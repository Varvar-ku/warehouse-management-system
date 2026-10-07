POSTGRES_PASSWORD: warehouse  //контейнер PostgreSQL + только для локальной разработки

api-gateway\application.yaml:40:      secret: ${JWT_SECRET:dev-only-secret-change-me-0123456789abcdef}

auth-service\application.yaml:10:    password: ${DB_PASSWORD:warehouse}
auth-service\application.yaml:29:      secret: ${JWT_SECRET:dev-only-secret-change-me-0123456789abcdef}
auth-service\application.yaml:36:    password: ${BOOTSTRAP_ADMIN_PASSWORD:Admin-2026-dev}

catalog-service\application.yaml:12:    password: ${DB_PASSWORD:warehouse}
catalog-service\application.yaml:39:      secret: ${JWT_SECRET:dev-only-secret-change-me-0123456789abcdef}

inventory-service\application.yaml:10:    password: ${DB_PASSWORD:warehouse}
inventory-service\application.yaml:30:      secret: ${JWT_SECRET:dev-only-secret-change-me-0123456789abcdef}

warehouse-service\application.yaml:10:    password: ${DB_PASSWORD:warehouse}
warehouse-service\application.yaml:32:      secret: ${JWT_SECRET:dev-only-secret-change-me-0123456789abcdef}

Admin_password: Admin-2026-dev
