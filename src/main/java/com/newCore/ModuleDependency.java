package com.newCore;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.ToString;
import org.springframework.data.domain.Persistable;

import java.io.Serializable;

@Entity
@Data
@Table(name = "base_module_dependencies")
@ToString(of = {"id", "version", "module", "dependency"})
public class ModuleDependency implements Serializable, Persistable<Long> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "module_id", nullable = false)
    @NotNull
    private Module module;

    @ManyToOne
    @NotNull
    @JoinColumn(name = "dependency_id", nullable = false)
    private Module dependency;

    @Column(name = "version", nullable = false)
    @NotNull
    private String version;

    @Override
    public boolean isNew() {
        return id == null;
    }

    //private String name;
}
