package com.fnmp.article.repository;

import com.fnmp.article.domain.Source;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SourceRepository extends JpaRepository<Source, UUID> {

    Optional<Source> findByName(String name);

    boolean existsByName(String name);
}