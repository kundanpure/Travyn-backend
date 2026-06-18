package com.travyn.common.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Aspect
@Component
@Slf4j
public class LoggingAspect {

    @Around("execution(* com.travyn..service..*(..))")
    public Object logServiceMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String argSummary = Arrays.stream(joinPoint.getArgs())
                .map(a -> a == null ? "null" : a.getClass().getSimpleName())
                .reduce((a, b) -> a + ", " + b)
                .orElse("");

        log.debug("→ {}.{}() called with [{}]", className, methodName, argSummary);

        try {
            Object result = joinPoint.proceed();
            log.debug("← {}.{}() returned successfully", className, methodName);
            return result;
        } catch (Exception ex) {
            log.error("✕ {}.{}() threw {}: {}", className, methodName,
                    ex.getClass().getSimpleName(), ex.getMessage());
            throw ex;
        }
    }
}
