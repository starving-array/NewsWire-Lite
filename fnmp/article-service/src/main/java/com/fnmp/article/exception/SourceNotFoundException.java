package com.fnmp.article.exception;

public class SourceNotFoundException extends RuntimeException {
    public SourceNotFoundException(String name) {
        super("Source not found: " + name);
    }
}