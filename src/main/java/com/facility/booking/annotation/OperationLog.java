package com.facility.booking.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解
 * 
 * 用于标记需要记录操作日志的方法。通过AOP切面自动捕获方法调用，
 * 记录用户操作行为，便于系统审计和追踪。
 * 
 * 使用示例：
 * 
 * @OperationLog(operationType = "CREATE", detail = "创建用户")
 *                             public User createUser(UserRequest request) { ...
 *                             }
 * 
 * @Target(ElementType.METHOD) 标识该注解只能用于方法上
 * @Retention(RetentionPolicy.RUNTIME) 运行时保留，便于通过反射获取注解信息
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /**
     * 操作类型
     * 
     * 用于分类操作类型，如：CREATE(创建), UPDATE(更新), DELETE(删除),
     * QUERY(查询), LOGIN(登录), LOGOUT(登出)等
     * 
     * @return 操作类型字符串
     */
    String operationType();

    /**
     * 操作详情描述
     * 
     * 对操作的具体描述，默认为空字符串
     * 
     * @return 操作详情
     */
    String detail() default "";
}