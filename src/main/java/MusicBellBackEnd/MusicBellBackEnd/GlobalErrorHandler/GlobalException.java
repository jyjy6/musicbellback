package MusicBellBackEnd.MusicBellBackEnd.GlobalErrorHandler;

import org.springframework.http.HttpStatus;



public class GlobalException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus httpStatus;

    public GlobalException(String message, String errorCode, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public GlobalException(String message, String errorCode) {
        this(message, errorCode, HttpStatus.BAD_REQUEST);
    }

    // getters
    public String getErrorCode() { return errorCode; }
    public HttpStatus getHttpStatus() { return httpStatus; }
}