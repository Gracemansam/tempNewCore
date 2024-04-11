package com.newCore;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
@Repository

public interface ModuleArtifactRepository extends JpaRepository<ModuleArtifact,Long> {

    Optional<ModuleArtifact> findByModule(Module module);
}
