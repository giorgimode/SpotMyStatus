package com.giorgimode.spotmystatus.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    User findByState(UUID state);

    List<User> findAllByTeamId(String teamId);
}