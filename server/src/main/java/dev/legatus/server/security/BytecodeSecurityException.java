package dev.legatus.server.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when uploaded bytecode contains prohibited symbols or capabilities,
 * such as filesystem access or host process execution (ADR-0009).
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class BytecodeSecurityException extends RuntimeException {

    private final String prohibitedSymbol;

    public BytecodeSecurityException(String message) {
        super(message);
        this.prohibitedSymbol = "UNKNOWN";
    }

    public BytecodeSecurityException(String prohibitedSymbol, String message) {
        super(message);
        this.prohibitedSymbol = prohibitedSymbol;
    }

    public String getProhibitedSymbol() {
        return prohibitedSymbol;
    }
}
