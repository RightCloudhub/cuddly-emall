package com.example.mall.web.error;

public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
