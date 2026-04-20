package com.facility.booking.service;

import com.facility.booking.entity.OperationLog;
import com.facility.booking.repository.OperationLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class OperationLogService {

    @Autowired
    private OperationLogRepository operationLogRepository;

    @Async
    public void saveOperationLog(Long operatorId, String operatorName, String operationType,
                                 Long targetId, String detail, String ipAddress) {
        try {
            OperationLog log = new OperationLog();
            log.setOperatorId(operatorId);
            log.setOperatorName(operatorName);
            log.setOperationType(operationType);
            log.setTargetId(targetId);
            log.setDetail(detail);
            log.setIpAddress(ipAddress);
            operationLogRepository.save(log);
        } catch (Exception e) {
            System.err.println("保存操作日志失败: " + e.getMessage());
        }
    }
}
