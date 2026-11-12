package ru.warehouse.auth.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.warehouse.auth.entity.RefreshToken;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /** Блокировка строки сериализует параллельные обновления одним и тем же токеном (две вкладки, повтор запроса). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenHash = :hash")
    Optional<RefreshToken> findByHashForUpdate(@Param("hash") String hash);

    /** Отзыв всей цепочки сессии. Уже отозванные не трогаем (сохраняется первая причина). */
    @Modifying(flushAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now, t.revokeReason = :reason "
            + "where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") OffsetDateTime now,
                     @Param("reason") String reason);

    /** Отзыв всех сессий пользователя (блокировка, смена ролей, смена пароля). */
    @Modifying(flushAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now, t.revokeReason = :reason "
            + "where t.userId = :userId and t.revokedAt is null")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") OffsetDateTime now,
                         @Param("reason") String reason);

    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") OffsetDateTime before);
}
