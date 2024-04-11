package com.newCore;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Data
@Entity
@EqualsAndHashCode
@NoArgsConstructor
@RequiredArgsConstructor
@Table(name = "base_permission")
public class Permission  {
    @Id
    @Column(name = "id", updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NonNull
    private String name;

    private String description;

    @JsonIgnore
    private int archived=0;

    @Column(name = "module_name")
    private String moduleName;

    /*@ManyToOne
    @JsonIgnore
    private Menu menu;*/

    /*@OneToMany(mappedBy = "permissionByPermissionId")
    @ToString.Exclude
    @JsonIgnore
    public List<RolePermission> rolePermissionsById;*/

    @PrePersist
    public void update(){
        archived = 0;
        if(name ==null) {
            name = description.replace(" ", "_") + "_" + LocalDateTime.now();
        }
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_name", referencedColumnName = "name", insertable = false, updatable = false)
    @JsonIgnore
    private Module module;
}
