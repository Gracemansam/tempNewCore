package com.newCore;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.data.domain.Persistable;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
@Entity
@Data
@Table(name = "base_module")
@EqualsAndHashCode(of = "name", callSuper = false)
@ToString(of = {"id", "name"})
public class Module implements Serializable, Persistable<Long> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(unique = true)
    private String name;

    @NotNull
    @Column(unique = true)
    private String basePackage;

    private String description;

    //@Pattern(regexp = "\\d+\\..*")
    private String version;

    private ZonedDateTime buildTime;

    @NotNull
    private Boolean active = true;

    private String artifact;

    private String umdLocation;

    private String moduleMap;

    private Boolean inError;

    private Boolean installOnBoot;

    /*private int status;

    private int archived;

    private Boolean processConfig;*/

    private Integer priority = 100;

    @OneToOne(mappedBy = "module", cascade = CascadeType.REMOVE, fetch = FetchType.LAZY)
    @JsonIgnore
    private ModuleArtifact moduleArtifact;

    @OneToMany(mappedBy = "module", cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE})
    private Set<WebModule> webModules = new HashSet<>();

    @OneToMany(mappedBy = "module", cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE}, fetch = FetchType.LAZY)
    @JsonIgnore
    private Set<Menu> menus = new HashSet<>();

    @OneToMany(mappedBy = "module", cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE})
    @JsonIgnore
    private Set<Authority> bundledAuthorities = new HashSet<>();

    @OneToMany(mappedBy = "module", cascade = {CascadeType.ALL})
    @JsonIgnore
    private Set<ModuleDependency> dependencies = new HashSet<>();

    @OneToMany(mappedBy = "module", cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE})
    @JsonIgnore
    private Set<Form> templates = new HashSet<>();
    @Override
    public boolean isNew() {
        return id == null;
    }
    public enum Type {ERROR, SUCCESS, WARNING}
    @Transient
    private Type type;
    @Transient
    private String message;

    @OneToMany(mappedBy = "module", cascade = {CascadeType.ALL})
    private Set<Permission> Permissions = new HashSet<>();
}

