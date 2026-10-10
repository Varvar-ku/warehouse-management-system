// UserRepository.java
package ru.warehouse.auth.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.warehouse.auth.entity.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Один запрос вместе с ролями (для входа и чтения одного пользователя). */
    @EntityGraph(attributePaths = "roles")
    Optional<User> findByLogin(String login);

    @EntityGraph(attributePaths = "roles")
    Optional<User> findWithRolesById(UUID id);

    boolean existsByLogin(String login);

    /** Для защиты от потери последнего администратора. */
    @Query("select count(u) from User u join u.roles r where r.code = 'ADMIN' and u.active = true")
    long countActiveAdmins();

    @Query("select u from User u join u.roles r where r.code = :code and u.active = true order by u.fullName")
    List<User> findActiveByRoleCode(@Param("code") String code);
}