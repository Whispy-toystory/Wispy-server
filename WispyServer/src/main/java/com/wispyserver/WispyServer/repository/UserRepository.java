package com.wispyserver.WispyServer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.wispyserver.WispyServer.entity.User;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByDeviceId(String deviceId);

    boolean existsByDeviceId(String deviceId);

    Optional<User> findByRefreshToken(String refreshToken);

}
