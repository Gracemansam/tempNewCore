package com.newCore;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;
import org.springframework.data.domain.Persistable;


import java.io.Serializable;

@Table(name = "base_form")
@Entity
@Data
@EqualsAndHashCode(of = "id")
@ToString(of = "name")
public class Form implements Serializable, Persistable<String> {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(
            name = "UUID",
            strategy = "org.hibernate.id.UUIDGenerator"
    )
    private String id;

    @NotNull
    private String name;
//    @Type(type = "jsonb-node")
//    @Column(columnDefinition = "jsonb")
//    private JsonNode form;

    private String path;

    @JsonIgnore
    private Integer priority = 1;

    @ManyToOne
    @NotNull
    @JsonIgnore
    private Module module;

    @Override
    public boolean isNew() {
        return id == null;
    }
}
