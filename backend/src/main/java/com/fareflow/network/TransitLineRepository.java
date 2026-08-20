package com.fareflow.network;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransitLineRepository extends JpaRepository<TransitLine, Long> {
    List<TransitLine> findByActiveTrue();
}
