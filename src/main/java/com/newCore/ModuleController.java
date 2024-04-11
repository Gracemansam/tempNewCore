package com.newCore;

import com.newCore.PluginManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/modules")
public class ModuleController {

    @Autowired
    private PluginManager moduleService;

    @PostMapping("/upload")
    public ResponseEntity<ModuleResponse> uploadModule(@RequestParam("file") MultipartFile file) {
        try {
            ModuleResponse module = moduleService.uploadModuleData(file);
            return new ResponseEntity<>(module, HttpStatus.OK);
        } catch (IOException e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    @PostMapping("/modules/install")
    public ResponseEntity<ModuleResponse> installModule(@RequestBody Module module,
                                                        @RequestParam(value = "install", required = false) Boolean install,
                                                        @RequestParam(value = "multi", required = false) Boolean multi) {
        try {
            ModuleResponse response = moduleService.installModule(module, install, multi);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}
