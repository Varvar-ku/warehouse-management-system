// Actor.java
package ru.warehouse.request.service;

import java.util.UUID;

/** Кто выполняет операцию: пользователь и признак роли кладовщика (из JWT). */
public record Actor(UUID userId, boolean storekeeper) {}