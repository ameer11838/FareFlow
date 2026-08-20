package com.fareflow.network;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransitLineStopRepository extends JpaRepository<TransitLineStop, Long> {
    List<TransitLineStop> findAllByOrderByLineIdAscSequenceAsc();
}
