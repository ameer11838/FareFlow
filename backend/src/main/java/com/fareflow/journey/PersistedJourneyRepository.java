package com.fareflow.journey;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PersistedJourneyRepository extends JpaRepository<PersistedJourney, Long> {
}
