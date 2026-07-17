package com.fnmp.article.exception;

import java.util.UUID;

public class DuplicateArticleException extends RuntimeException {
    public DuplicateArticleException(UUID id) {
        super("Article already exists: " + id);
    }
}