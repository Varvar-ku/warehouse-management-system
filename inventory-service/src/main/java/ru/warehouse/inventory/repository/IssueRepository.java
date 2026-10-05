// IssueRepository.java
package ru.warehouse.inventory.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.warehouse.inventory.entity.Issue;
import java.util.Optional;
import java.util.UUID;

public interface IssueRepository extends JpaRepository<Issue, UUID>, JpaSpecificationExecutor<Issue> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Issue i where i.id = :id")
    Optional<Issue> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = "select nextval('issue_number_seq')", nativeQuery = true)
    long nextNumber();

    boolean existsByRequestIdAndStatus(UUID requestId, Issue.Status status);
}