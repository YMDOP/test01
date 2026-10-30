package com.love.interviewforge.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiPricingRepository extends JpaRepository<AiPricing, Long> {

    Optional<AiPricing> findFirstByModelAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            String model, String effectiveFrom);
}
