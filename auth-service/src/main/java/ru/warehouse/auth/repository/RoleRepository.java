// RoleRepository.java
package ru.warehouse.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.warehouse.auth.entity.Role;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    List<Role> findByCodeIn(Collection<String> codes);
    List<Role> findAllByOrderByCodeAsc();
}