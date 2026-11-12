package ru.warehouse.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.warehouse.auth.entity.RefreshToken;
import ru.warehouse.auth.repository.RefreshTokenRepository;
import ru.warehouse.common.exception.ApiException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** P1-8: логика ротации и отзыва refresh-токенов. Без БД: репозиторий подменён. */
class RefreshTokenServiceTest {
    private static final Duration TTL = Duration.ofHours(12);
    private static final Duration MAX_SESSION = Duration.ofHours(24);
    private static final Duration GRACE = Duration.ofSeconds(10);

    private RefreshTokenRepository repo;
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        repo = mock(RefreshTokenRepository.class);
        when(repo.save(any(RefreshToken.class))).thenAnswer(inv -> {
            RefreshToken t = inv.getArgument(0);
            if (t.getId() == null) t.setId(UUID.randomUUID());
            return t;
        });
        service = new RefreshTokenService(repo, mock(AuthAudit.class), TTL, MAX_SESSION, GRACE);
    }

    private RefreshToken stored(OffsetDateTime familyStart, OffsetDateTime expiresAt) {
        RefreshToken t = new RefreshToken();
        t.setId(UUID.randomUUID());
        t.setUserId(UUID.randomUUID());
        t.setFamilyId(UUID.randomUUID());
        t.setFamilyStartedAt(familyStart);
        t.setTokenHash("hash");
        t.setCreatedAt(familyStart);
        t.setExpiresAt(expiresAt);
        when(repo.findByHashForUpdate(anyString())).thenReturn(Optional.of(t));
        return t;
    }

    private RefreshToken fresh() {
        OffsetDateTime now = OffsetDateTime.now();
        return stored(now.minusMinutes(5), now.plusHours(11));
    }

    @Test
    void startSession_storesOnlyHash_andSetsExpiry() {
        UUID user = UUID.randomUUID();
        RefreshTokenService.Issued issued = service.startSession(user);

        org.mockito.ArgumentCaptor<RefreshToken> captor = org.mockito.ArgumentCaptor.forClass(RefreshToken.class);
        verify(repo).save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertThat(issued.value()).isNotBlank().hasSizeGreaterThanOrEqualTo(40);
        assertThat(saved.getTokenHash()).isEqualTo(RefreshTokenService.hash(issued.value()))
                .isNotEqualTo(issued.value()).hasSize(64);
        assertThat(saved.getUserId()).isEqualTo(user);
        assertThat(saved.getExpiresAt()).isBetween(OffsetDateTime.now().plusHours(11), OffsetDateTime.now().plusHours(13));
    }

    @Test
    void rotate_unknownToken_isRejected() {
        when(repo.findByHashForUpdate(anyString())).thenReturn(Optional.empty());
        assertThrows(ApiException.class, () -> service.rotate("whatever"));
    }

    @Test
    void rotate_blankToken_isRejected_withoutTouchingDb() {
        assertThrows(ApiException.class, () -> service.rotate(" "));
        assertThrows(ApiException.class, () -> service.rotate(null));
        verify(repo, never()).findByHashForUpdate(anyString());
    }

    @Test
    void rotate_revokedToken_isRejected() {
        RefreshToken t = fresh();
        t.setRevokedAt(OffsetDateTime.now().minusMinutes(1));
        assertThrows(ApiException.class, () -> service.rotate("raw"));
        verify(repo, never()).save(any(RefreshToken.class));
    }

    @Test
    void rotate_expiredToken_isRejected() {
        OffsetDateTime now = OffsetDateTime.now();
        stored(now.minusHours(13), now.minusMinutes(1));
        assertThrows(ApiException.class, () -> service.rotate("raw"));
        verify(repo, never()).save(any(RefreshToken.class));
    }

    @Test
    void rotate_normal_issuesNewToken_inSameFamily_andMarksOldAsUsed() {
        RefreshToken old = fresh();
        RefreshTokenService.Rotation r = service.rotate("raw");

        assertThat(r.userId()).isEqualTo(old.getUserId());
        assertThat(r.issued()).isNotNull();
        assertThat(old.getUsedAt()).isNotNull();

        org.mockito.ArgumentCaptor<RefreshToken> captor = org.mockito.ArgumentCaptor.forClass(RefreshToken.class);
        verify(repo).save(captor.capture());
        RefreshToken next = captor.getValue();
        assertThat(next.getFamilyId()).isEqualTo(old.getFamilyId());
        assertThat(next.getFamilyStartedAt()).isEqualTo(old.getFamilyStartedAt());
        assertThat(next.getTokenHash()).isNotEqualTo(old.getTokenHash());
        assertThat(old.getReplacedBy()).isEqualTo(next.getId());
    }

    @Test
    void rotate_reuseAfterGrace_revokesWholeFamily() {
        RefreshToken old = fresh();
        old.setUsedAt(OffsetDateTime.now().minusMinutes(1));

        assertThrows(ApiException.class, () -> service.rotate("raw"));
        verify(repo).revokeFamily(eq(old.getFamilyId()), any(OffsetDateTime.class), eq("REUSE"));
        verify(repo, never()).save(any(RefreshToken.class));
    }

    @Test
    void rotate_reuseWithinGrace_isRaceBetweenTabs_noNewToken_noRevocation() {
        RefreshToken old = fresh();
        old.setUsedAt(OffsetDateTime.now().minusSeconds(2));

        RefreshTokenService.Rotation r = service.rotate("raw");

        assertThat(r.userId()).isEqualTo(old.getUserId());
        assertThat(r.issued()).isNull();
        verify(repo, never()).save(any(RefreshToken.class));
        verify(repo, never()).revokeFamily(any(), any(), anyString());
    }

    @Test
    void rotate_afterMaxSession_revokesFamily() {
        OffsetDateTime now = OffsetDateTime.now();
        RefreshToken old = stored(now.minusHours(25), now.plusHours(1));

        assertThrows(ApiException.class, () -> service.rotate("raw"));
        verify(repo).revokeFamily(eq(old.getFamilyId()), any(OffsetDateTime.class), eq("MAX_SESSION"));
    }

    @Test
    void rotate_newTokenExpiry_isCappedByMaxSession() {
        OffsetDateTime now = OffsetDateTime.now();
        stored(now.minusHours(23), now.plusHours(11));

        service.rotate("raw");

        org.mockito.ArgumentCaptor<RefreshToken> captor = org.mockito.ArgumentCaptor.forClass(RefreshToken.class);
        verify(repo).save(captor.capture());
        // сессия началась 23 ч назад, предел 24 ч: токен живёт не более часа (а не 12 ч)
        assertThat(captor.getValue().getExpiresAt()).isBefore(now.plusHours(1).plusMinutes(1));
    }

    @Test
    void logout_revokesFamily_once() {
        RefreshToken t = fresh();
        service.logout("raw");
        verify(repo).revokeFamily(eq(t.getFamilyId()), any(OffsetDateTime.class), eq("LOGOUT"));
    }

    @Test
    void logout_ofAlreadyRevokedToken_doesNothing() {
        RefreshToken t = fresh();
        t.setRevokedAt(OffsetDateTime.now());
        service.logout("raw");
        verify(repo, never()).revokeFamily(any(), any(), anyString());
    }

    @Test
    void logout_withoutToken_isNoop() {
        service.logout(null);
        verify(repo, never()).findByHashForUpdate(anyString());
    }
}
