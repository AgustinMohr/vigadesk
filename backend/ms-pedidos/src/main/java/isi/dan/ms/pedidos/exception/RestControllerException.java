package isi.dan.ms.pedidos.exception;

import org.springframework.web.bind.annotation.RestControllerAdvice;

import dan.common.error.BaseExceptionHandler;

/**
 * Manejo global de errores de ms-pedidos. El formato y el mapeo viven en dan-common; las excepciones
 * de dominio heredan de ApiException.
 */
@RestControllerAdvice
public class RestControllerException extends BaseExceptionHandler {
}
