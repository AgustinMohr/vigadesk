package isi.dan.ms_productos.exception;

import org.springframework.web.bind.annotation.RestControllerAdvice;

import dan.common.error.BaseExceptionHandler;

/**
 * Manejo global de errores de ms-productos. El formato y el mapeo viven en dan-common; las excepciones
 * de dominio (ProductoNotFoundException, InsufficientStockException) heredan de ApiException.
 */
@RestControllerAdvice
public class RestControllerException extends BaseExceptionHandler {
}
