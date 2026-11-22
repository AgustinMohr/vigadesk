package isi.dan.msclientes.exception;

import org.springframework.web.bind.annotation.RestControllerAdvice;

import dan.common.error.BaseExceptionHandler;

/**
 * Manejo global de errores de ms-clientes. El formato y el mapeo viven en dan-common; las excepciones
 * de dominio heredan de ApiException. Una RuntimeException ya no se trata como error de negocio (400):
 * es un 500 generico.
 */
@RestControllerAdvice
public class RestControllerException extends BaseExceptionHandler {
}
