package com.newCore.yml;

import com.newCore.*;
import lombok.Data;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class ModuleConfig {
    private String name;
    private String basePackage;
    private String umdLocation;
    private String moduleMap;
    private String version;
    private Integer priority = 100;
    private String resourceKey = "";
    private boolean store;
    private Map<String, String> dependencies = new HashMap<>();
    private List<WebModuleConfig> webModules = new ArrayList<>();
    private List<Authority> bundledAuthorities = new ArrayList<>();
    private List<Menu> menus = new ArrayList<>();
   private List<JsonForm> jsonForms = new ArrayList<>();
   private List<ComponentForm> componentForms = new ArrayList<>();
    private List<Dependency> deps = new ArrayList<>();
    private String summary;
    private List<Permission> permissions = new ArrayList<>();
}
