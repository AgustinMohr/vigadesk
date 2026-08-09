package isi.dan.ms_productos.exception;

// Excepción lanzada cuando no se encuentra un producto por ID
public class ProductoNotFoundException extends RuntimeException{
    public ProductoNotFoundException(Long id){
        super("Producto "+id+" no encontrado");
    }
}
