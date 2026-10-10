package ru.warehouse.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.warehouse.auth.dto.AuthDtos.*;
import ru.warehouse.auth.entity.Role;
import ru.warehouse.auth.entity.User;
import ru.warehouse.auth.repository.RoleRepository;
import ru.warehouse.auth.repository.UserRepository;
import ru.warehouse.common.dto.PageResponse;
import ru.warehouse.common.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/** Управление пользователями и ролями (разд. 4 ТЗ: это зона администратора). */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_BYTES = 72; // ограничение BCrypt

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse create(CreateUserRequest req, UUID actorId) {
        String login = normalizeLogin(req.login());
        if (userRepository.existsByLogin(login)) {
            throw ApiException.conflict("LOGIN_ALREADY_EXISTS", "Логин уже занят: " + login);
        }
        validatePassword(req.password());

        User user = new User();
        user.setLogin(login);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setFullName(req.fullName().trim());
        user.setEmail(blankToNull(req.email()));
        user.setPhone(blankToNull(req.phone()));
        user.setRoles(resolveRoles(req.roles()));
        user = userRepository.save(user);
        log.info("Создан пользователь {} ({}), инициатор {}", user.getLogin(), user.getId(), actorId);
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("login"));
        return PageResponse.from(userRepository.findAll(pageable).map(UserService::toResponse));
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        return toResponse(find(id));
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest req, UUID actorId) {
        User user = find(id);

        // Считаем администраторов ДО изменений (иначе автосброс контекста исказит подсчёт)
        boolean wasActiveAdmin = user.isActive() && hasAdminRole(user);
        long activeAdmins = wasActiveAdmin ? userRepository.countActiveAdmins() : 0;

        if (req.fullName() != null) user.setFullName(req.fullName().trim());
        if (req.email() != null) user.setEmail(blankToNull(req.email()));
        if (req.phone() != null) user.setPhone(blankToNull(req.phone()));
        if (req.active() != null) user.setActive(req.active());
        if (req.roles() != null) {
            if (req.roles().isEmpty()) {
                throw ApiException.validation("У пользователя должна остаться хотя бы одна роль");
            }
            user.setRoles(resolveRoles(req.roles()));
        }

        // Нельзя оставить систему без активного администратора (иначе некому управлять пользователями)
        boolean isActiveAdmin = user.isActive() && hasAdminRole(user);
        if (wasActiveAdmin && !isActiveAdmin && activeAdmins <= 1) {
            throw ApiException.conflict("LAST_ADMIN", "Нельзя отключить или лишить роли последнего администратора");
        }
        user.touch();
        log.info("Изменён пользователь {} ({}), инициатор {}", user.getLogin(), user.getId(), actorId);
        return toResponse(user);
    }

    @Transactional
    public void resetPassword(UUID id, String newPassword, UUID actorId) {
        validatePassword(newPassword);
        User user = find(id);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.touch();
        log.info("Сброшен пароль пользователя {} ({}), инициатор {}", user.getLogin(), user.getId(), actorId);
    }

    @Transactional
    public void changeOwnPassword(UUID userId, ChangePasswordRequest req) {
        User user = find(userId);
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw ApiException.validation("Текущий пароль указан неверно");
        }
        validatePassword(req.newPassword());
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.touch();
        log.info("Пользователь {} сменил пароль", user.getLogin());
    }

    /** Данные текущего пользователя для GET /me. Отключённый или удалённый, то есть 401. */
    @Transactional(readOnly = true)
    public UserResponse me(UUID id) {
        User user = userRepository.findWithRolesById(id)
                .filter(User::isActive)
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHORIZED", "Учётная запись недоступна"));
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles() {
        return roleRepository.findAllByOrderByCodeAsc().stream()
                .map(r -> new RoleResponse(r.getCode(), r.getName())).toList();
    }

    /** Создаёт первого администратора, если пользователей ещё нет. */
    @Transactional
    public void bootstrapAdmin(String login, String password, String fullName) {
        if (userRepository.count() > 0) return;
        if (login == null || login.isBlank() || password == null || password.isBlank()) {
            log.warn("В auth_db нет пользователей, а BOOTSTRAP_ADMIN_* не заданы: войти в систему будет нельзя");
            return;
        }
        validatePassword(password);
        User admin = new User();
        admin.setLogin(normalizeLogin(login));
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setFullName(fullName == null || fullName.isBlank() ? "Администратор" : fullName);
        admin.setRoles(resolveRoles(Set.of("ADMIN")));
        userRepository.save(admin);
        log.warn("Создан администратор по умолчанию '{}'. СМЕНИТЕ ПАРОЛЬ (POST /api/v1/me/password)", admin.getLogin());
    }

    // ================= вспомогательное =================

    public static UserResponse toResponse(User u) {
        List<String> roles = u.getRoles().stream().map(Role::getCode).sorted().toList();
        return new UserResponse(u.getId(), u.getLogin(), u.getFullName(), u.getEmail(), u.getPhone(),
                u.isActive(), roles, u.getCreatedAt());
    }

    private User find(UUID id) {
        return userRepository.findWithRolesById(id)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Пользователь не найден"));
    }

    private boolean hasAdminRole(User user) {
        return user.getRoles().stream().anyMatch(r -> "ADMIN".equals(r.getCode()));
    }

    private Set<Role> resolveRoles(Collection<String> codes) {
        Set<String> wanted = codes.stream()
                .map(c -> c.trim().toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        List<Role> found = roleRepository.findByCodeIn(wanted);
        if (found.size() != wanted.size()) {
            wanted.removeAll(found.stream().map(Role::getCode).collect(Collectors.toSet()));
            throw ApiException.validation("Неизвестные роли: " + wanted);
        }
        return new HashSet<>(found);
    }

    private static String normalizeLogin(String login) {
        return login.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw ApiException.validation("Пароль должен содержать не менее " + MIN_PASSWORD_LENGTH + " символов");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw ApiException.validation("Пароль слишком длинный (не более " + MAX_PASSWORD_BYTES + " байт)");
        }
    }

    @Transactional(readOnly = true)
    public List<WorkerInfo> activeWorkers() {
        return userRepository.findActiveByRoleCode("WAREHOUSE_WORKER").stream()
                .map(u -> new WorkerInfo(u.getId(), u.getLogin(), u.getFullName())).toList();
    }
}