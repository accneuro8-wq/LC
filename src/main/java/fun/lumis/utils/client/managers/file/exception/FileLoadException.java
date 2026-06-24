package fun.lumis.utils.client.managers.file.exception;

public class FileLoadException extends FileProcessingException {
    public FileLoadException(String message) {
        super(message);
    }

    public FileLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
