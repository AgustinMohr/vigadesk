package isi.dan.msclientes.exception;

// Excepción lanzada cuando no se encuentra un cliente por ID
public class ClienteNotFoundException extends Exception{
    public ClienteNotFoundException(String msg){
        super(msg);
    }
}
