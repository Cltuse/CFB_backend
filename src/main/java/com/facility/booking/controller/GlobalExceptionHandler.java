package com.facility.booking.controller;

import com.facility.booking.common.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Object>> handleAllExceptions(Exception ex) {
        System.err.println("Global Exception Handler caught exception: " + ex.getClass().getSimpleName());
        System.err.println("Error message: " + ex.getMessage());
        ex.printStackTrace();
        
        // 根据不同的异常类型返回不同的错误信息
        if (ex instanceof SQLException) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("数据库错误: " + ex.getMessage()));
        } else if (ex instanceof NullPointerException) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("空指针异常，请联系系统管理员"));
        } else if (ex instanceof IllegalArgumentException) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error("参数错误: " + ex.getMessage()));
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error("系统错误: " + ex.getMessage()));
        }
    }
}