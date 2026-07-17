package com.fnmp.article.exception;

import java.util.UUID;

public class ArticleNotFoundException extends RuntimeException {
    public ArticleNotFoundException(UUID id) {
        super("Article not found: " + id);
    }
}