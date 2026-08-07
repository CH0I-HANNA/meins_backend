package com.mcm.onboarding.domain.tag.repository;

import com.mcm.onboarding.domain.tag.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {

    Optional<Tag> findByTagCode(String tagCode);

    boolean existsByTagCode(String tagCode);

    boolean existsByAuthCode(String authCode);

    List<Tag> findByProductId(Long productId);
}
