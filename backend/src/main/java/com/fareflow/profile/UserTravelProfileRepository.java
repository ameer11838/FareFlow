package com.fareflow.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserTravelProfileRepository extends JpaRepository<UserTravelProfile, Long> {

    Optional<UserTravelProfile> findByUserId(long userId);

    boolean existsByUserIdAndOnboardingCompletedTrue(long userId);
}
