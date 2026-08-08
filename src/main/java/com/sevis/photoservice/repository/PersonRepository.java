package com.sevis.photoservice.repository;

import com.sevis.photoservice.model.Person;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersonRepository extends JpaRepository<Person, Long> {

    List<Person> findByUserId(Long userId);

    Optional<Person> findByIdAndUserId(Long id, Long userId);
}
