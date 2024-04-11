package com.newCore;

import lombok.Data;
import org.apache.commons.io.FileUtils;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.jar.Manifest;
@Component
public class ModuleManager {

    private final Path rootLocation;
    private final static  String MODULE_PATH = "modules";

    public ModuleManager() {
        this.rootLocation = Paths.get(MODULE_PATH);
    }


    @Data
    public static class VersionInfo {
        public static final VersionInfo UNKNOWN = new VersionInfo();

        public static final String UNKNOWN_VALUE = "unknown";

        public Manifest manifest;
        public Date buildTime;
        public String version = UNKNOWN_VALUE;
        public String projectName = UNKNOWN_VALUE;
        public boolean available;
    }

    private String setFileNameAndCreateDirectories(String filename, String module){
        if (filename.endsWith(File.separator)) {
            filename = filename.substring(0, filename.length() - 1) + ".jar";
        }
        if (!filename.endsWith(".jar")) {
            filename = filename + ".jar";
        }
        if (!Files.exists(rootLocation.resolve(module))) {
            try {
                Files.createDirectories(rootLocation.resolve(module));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return filename;
    }

    public URL getURL(String file) {
        try {
            return rootLocation.resolve(file).toUri().toURL();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String store(String module, MultipartFile file) {
        module = module.toLowerCase();
        String filename = module + File.separator + StringUtils.cleanPath(file.getOriginalFilename());
        filename = this.setFileNameAndCreateDirectories(filename, module);

        try {
            if (file.isEmpty()) {
                throw new RuntimeException("Failed to store empty file " + filename);
            }

            if (filename.contains("..")) {
                throw new RuntimeException("Cannot store file with relative path outside current directory " + filename);
            }

            try (InputStream inputStream = file.getInputStream()) {
                FileUtils.copyInputStreamToFile(inputStream, this.rootLocation.resolve(filename).toFile());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file " + filename, e);
        }
        return filename;
    }
}
