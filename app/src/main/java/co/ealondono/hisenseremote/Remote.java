package co.ealondono.hisenseremote;

/** Un medio de transporte para enviar teclas al televisor. Las llamadas se hacen en un hilo de fondo. */
public interface Remote {
    /** Envía la tecla. Lanza excepción con un mensaje legible si falla. */
    void send(Key key) throws Exception;

    /** true si este transporte soporta la tecla. */
    boolean supports(Key key);

    /** Libera conexiones. */
    default void close() {}
}
