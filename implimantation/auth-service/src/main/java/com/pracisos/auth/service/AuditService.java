package com.pracisos.auth.service;

import com.pracisos.auth.domain.entity.AuditLog;
import com.pracisos.auth.domain.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(UUID tenantId, UUID actorId, String actorRole, String action,
                    String targetType, UUID targetId, String beforeState, String afterState,
                    String ipAddress, String userAgent) {
        AuditLog auditLog = new AuditLog(tenantId, actorId, actorRole, action,
                targetType, targetId, beforeState, afterState, ipAddress, userAgent);
        auditLogRepository.save(auditLog);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getAuditLogsByTenant(UUID tenantId) {
        return auditLogRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
    }
}
