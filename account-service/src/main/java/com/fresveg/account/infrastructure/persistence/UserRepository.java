package com.fresveg.account.infrastructure.persistence;

import com.fresveg.account.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByOidcIssuerAndOidcSubject(String issuer, String subject);

    @Modifying
    @Query(value = """
            INSERT INTO account.users (user_id, oidc_issuer, oidc_subject, display_name, email, created_by, updated_by)
            VALUES (:id, :issuer, :subject, :name, :email, :id, :id)
            ON CONFLICT (oidc_issuer, oidc_subject) DO NOTHING
            """, nativeQuery = true)
    int provision(@Param("id") UUID id, @Param("issuer") String issuer, @Param("subject") String subject,
            @Param("name") String name, @Param("email") String email);

    @Modifying
    @Query(value = """
            INSERT INTO account.customer_profiles (customer_id, user_id, full_name, created_by, updated_by)
            VALUES (gen_random_uuid(), :userId, :name, :userId, :userId)
            """, nativeQuery = true)
    void createCustomer(@Param("userId") UUID userId, @Param("name") String name);

    @Modifying
    @Query(value = """
            INSERT INTO account.user_preferences (preference_id, user_id, created_by, updated_by)
            VALUES (gen_random_uuid(), :userId, :userId, :userId)
            """, nativeQuery = true)
    void createPreferences(@Param("userId") UUID userId);

    @Modifying
    @Query(value = """
            INSERT INTO account.user_roles (user_role_id, user_id, role_id, created_by, updated_by)
            SELECT gen_random_uuid(), :userId, role_id, :userId, :userId FROM account.roles WHERE code = 'CUSTOMER'
            """, nativeQuery = true)
    void assignCustomerRole(@Param("userId") UUID userId);
}
