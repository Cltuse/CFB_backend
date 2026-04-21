package com.facility.booking.repository;

import com.facility.booking.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    List<User> findByRoleOrderByRealNameAsc(String role);

    @Query("SELECT u FROM User u WHERE u.realName LIKE %:keyword% OR u.username LIKE %:keyword%")
    List<User> findByRealNameLikeOrUsernameLike(@Param("keyword") String keyword);
}
