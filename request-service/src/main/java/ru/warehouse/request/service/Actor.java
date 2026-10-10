package ru.warehouse.request.service;

import java.util.UUID;

/** Кто выполняет операцию: пользователь и признаки ролей кладовщика и администратора (из JWT). */
public record Actor(UUID userId, boolean storekeeper, boolean admin) {
    /** Видит все заявки: кладовщик (работает с ними) и администратор (только просмотр). */
    public boolean seesAll() {
        return storekeeper || admin;
    }
}