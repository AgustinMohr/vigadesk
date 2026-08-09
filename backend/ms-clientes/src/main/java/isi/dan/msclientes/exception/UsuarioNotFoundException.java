package isi.dan.msclientes.exception;

// Excepción lanzada cuando no se encuentra un usuario de cliente por ID
public class UsuarioNotFoundException extends Exception {
    public UsuarioNotFoundException(String msg) {
        super(msg);
    }
}
