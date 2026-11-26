package isi.dan.msclientes.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

// Aspecto que intercepta métodos anotados con @LogExecutionTime y registra su duración
@Aspect
@Component
public class LoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);

    @Around("@annotation(isi.dan.msclientes.aop.LogExecutionTime)")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();

        Object proceed = joinPoint.proceed();

        long executionTime = System.currentTimeMillis() - start;

        // El MDC viaja como campos del JSON solo en esta linea: si no se quita, queda pegado al hilo
        try (var metodo = MDC.putCloseable("metodo", joinPoint.getSignature().toLongString());
             var duracion = MDC.putCloseable("duracion", String.valueOf(executionTime))) {
            logger.info("{};{}", joinPoint.getSignature().getDeclaringType()
                    + "." + joinPoint.getSignature().getName(), executionTime);
        }

        return proceed;
    }

}