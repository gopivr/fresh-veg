package com.fresveg.account.infrastructure.persistence;

import com.fresveg.account.domain.Role;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    @Query("select r.code from Role r join UserRole ur on ur.roleId = r.id where ur.userId = :userId order by r.code")
    List<String> codesForUser(@Param("userId") UUID userId);
}
