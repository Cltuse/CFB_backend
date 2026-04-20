package com.facility.booking.repository;

import com.facility.booking.entity.UserFacilityBehavior;
import com.facility.booking.entity.UserFacilityBehaviorId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserFacilityBehaviorRepository extends JpaRepository<UserFacilityBehavior, UserFacilityBehaviorId> {

    List<UserFacilityBehavior> findByUserId(Long userId);

    List<UserFacilityBehavior> findByFacilityId(Long facilityId);

    UserFacilityBehavior findByUserIdAndFacilityId(Long userId, Long facilityId);

    @Query("SELECT DISTINCT u.userId FROM UserFacilityBehavior u")
    List<Long> findAllUserIds();

    @Query("SELECT DISTINCT u.facilityId FROM UserFacilityBehavior u")
    List<Long> findAllFacilityIds();

    List<UserFacilityBehavior> findByUserIdAndCategoryName(Long userId, String categoryName);
}
