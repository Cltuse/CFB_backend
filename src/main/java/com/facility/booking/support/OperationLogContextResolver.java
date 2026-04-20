package com.facility.booking.support;

import com.facility.booking.common.Result;
import com.facility.booking.security.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.reflect.Method;
import java.util.Map;

@Component
public class OperationLogContextResolver {

    private final CurrentUserService currentUserService;

    public OperationLogContextResolver(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    public Long resolveOperatorId(HttpServletRequest request) {
        Long currentUserId = currentUserService.getCurrentUserId();
        if (currentUserId != null) {
            return currentUserId;
        }

        return resolveRequestId(request, "operatorId", "adminId", "maintainerId", "reportedBy", "publisherId", "verifiedBy");
    }

    public String resolveOperatorName(HttpServletRequest request) {
        String currentUserName = currentUserService.getCurrentUserName();
        if (currentUserName != null && !currentUserName.isBlank()) {
            return currentUserName;
        }

        return null;
    }

    public String resolveIpAddress(HttpServletRequest request) {
        String[] headerNames = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"};
        for (String headerName : headerNames) {
            String value = request.getHeader(headerName);
            if (value != null && !value.trim().isEmpty() && !"unknown".equalsIgnoreCase(value)) {
                int index = value.indexOf(',');
                return index >= 0 ? value.substring(0, index).trim() : value.trim();
            }
        }
        return request.getRemoteAddr();
    }

    public Long resolveTargetId(HttpServletRequest request, Object body) {
        Long targetId = resolveRequestId(request, "id", "targetId", "reservationId", "facilityId", "categoryId", "userId", "noticeId", "feedbackId", "maintenanceId");
        if (targetId != null) {
            return targetId;
        }

        Object data = body;
        if (body instanceof Result<?> result) {
            data = result.getData();
        }

        return extractIdFromObject(data);
    }

    public String resolveDetail(HttpServletRequest request, String detail, String operationType, Object body) {
        if (detail != null && !detail.trim().isEmpty()) {
            return detail;
        }
        if (body instanceof Result<?> result && result.getMessage() != null && !result.getMessage().trim().isEmpty()) {
            return result.getMessage();
        }
        return operationType + " " + request.getMethod() + " " + request.getRequestURI();
    }

    private Long resolveRequestId(HttpServletRequest request, String... names) {
        Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (variables instanceof Map<?, ?> uriVariables) {
            for (String name : names) {
                Long value = toLong(uriVariables.get(name));
                if (value != null) {
                    return value;
                }
            }
        }

        for (String name : names) {
            Long value = toLong(request.getParameter(name));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Long extractIdFromObject(Object data) {
        if (data == null) {
            return null;
        }
        if (data instanceof Map<?, ?> map) {
            for (String key : new String[]{"id", "targetId", "reservationId", "facilityId", "categoryId", "userId"}) {
                Long value = toLong(map.get(key));
                if (value != null) {
                    return value;
                }
            }
            return null;
        }
        for (String methodName : new String[]{"getId", "getTargetId", "getReservationId", "getFacilityId", "getCategoryId", "getUserId"}) {
            try {
                Method method = data.getClass().getMethod(methodName);
                Long value = toLong(method.invoke(data));
                if (value != null) {
                    return value;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }
}
