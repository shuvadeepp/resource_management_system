package tech.csm.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import tech.csm.model.AuditLog;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Integer> {

    List<AuditLog> findAllByOrderByAuditLogIdDesc(Pageable page);

    List<AuditLog> findByEntityNameOrderByAuditLogIdDesc(String entityName, Pageable page);

    List<AuditLog> findByAffectedRecordIdOrderByAuditLogIdDesc(Integer affectedRecordId, Pageable page);

    List<AuditLog> findByEntityNameAndAffectedRecordIdOrderByAuditLogIdDesc(String entityName,
                                                                           Integer affectedRecordId,
                                                                           Pageable page);
}
