package org.koaks.codereview.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BizException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public BizException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static BizException badRequest(String message) {
        return new BizException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static BizException notFound(String what) {
        return new BizException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " not found");
    }

    public static BizException conflict(String message) {
        return new BizException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public static BizException unauthorized(String message) {
        return new BizException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }

}
