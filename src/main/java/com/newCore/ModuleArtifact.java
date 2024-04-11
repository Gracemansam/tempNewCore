package com.newCore;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.ToString;

import org.springframework.data.domain.Persistable;


import java.io.Serializable;

@Entity
@Data
@ToString(of = "id")
@Table(name = "base_module_artifact")
public class ModuleArtifact implements Serializable, Persistable<Long> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    private Long id;

    @OneToOne
    @NotNull
    private Module module;

    @NotNull
    private byte[] data;

    @Override
    public boolean isNew() {
        return id == null;
    }
}
