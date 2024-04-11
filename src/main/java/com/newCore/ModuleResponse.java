package com.newCore;

import lombok.Data;
;

@Data
public class ModuleResponse {
    public enum Type {ERROR, SUCCESS}

    private Type type;
    private String message;
    private Module module;
}
