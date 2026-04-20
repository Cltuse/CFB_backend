package com.facility.booking.support;

import com.facility.booking.annotation.OperationLog;
import com.facility.booking.common.Result;
import com.facility.booking.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
public class OperationLogResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Autowired
    private OperationLogService operationLogService;

    @Autowired
    private OperationLogContextResolver operationLogContextResolver;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return returnType.hasMethodAnnotation(OperationLog.class);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        OperationLog operationLog = returnType.getMethodAnnotation(OperationLog.class);
        if (operationLog == null || isFailure(body)) {
            return body;
        }

        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpServletRequest = servletRequest.getServletRequest();
            Long operatorId = operationLogContextResolver.resolveOperatorId(httpServletRequest);
            String operatorName = operationLogContextResolver.resolveOperatorName(httpServletRequest);
            String ipAddress = operationLogContextResolver.resolveIpAddress(httpServletRequest);
            Long targetId = operationLogContextResolver.resolveTargetId(httpServletRequest, body);
            String detail = operationLogContextResolver.resolveDetail(httpServletRequest, operationLog.detail(), operationLog.operationType(), body);
            operationLogService.saveOperationLog(operatorId, operatorName, operationLog.operationType(), targetId, detail, ipAddress);
        }

        return body;
    }

    private boolean isFailure(Object body) {
        return body instanceof Result<?> result && !result.isSuccess();
    }
}
