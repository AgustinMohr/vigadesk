package isi.dan.ms_productos.exception;

import dan.common.error.RecursoNoEncontradoException;

// Excepción lanzada cuando no se encuentra un producto por ID
public class ProductoNotFoundException extends RecursoNoEncontradoException {
    public ProductoNotFoundException(Long id) {
        super("Producto " + id + " no encontrado");
    }
}
