package com.newCore;

import lombok.Data;

@Data
public class JsonForm {
    private String name;
    private String path;
    private Integer priority = 1;
}
