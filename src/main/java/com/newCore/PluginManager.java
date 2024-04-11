package com.newCore;

import com.newCore.yml.ModuleConfig;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;

import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.web.multipart.MultipartFile;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;


import java.io.*;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.ParseException;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.context.ApplicationContext;
import org.yaml.snakeyaml.constructor.Constructor;

import static com.newCore.Module.Type.ERROR;
import static com.newCore.Module.Type.SUCCESS;


@Component
@Slf4j
public class PluginManager {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PluginManager.class);
    private Path rootLocation;
    @Autowired
    private ModuleManager moduleManager;
    @Autowired
    private ModuleRepository moduleRepository;
    @Autowired
    private  ApplicationContext applicationContext;
    @Autowired
    private ModuleDependencyRepository moduleDependencyRepository;
    @Autowired
    private ModuleArtifactRepository moduleArtifactRepository;

    private  SimpMessageSendingOperations messagingTemplate;
    private final static  String MODULE_PATH = "modules";

    public PluginManager() {
        this.rootLocation = Paths.get(MODULE_PATH);
    }

    @Autowired
    public PluginManager(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public boolean isInstalled(String name) {
        Map<String, Module> moduleBeans = applicationContext.getBeansOfType(Module.class);
        return moduleBeans.values().stream()
                .anyMatch(module -> module.getName().equals(name));
    }





    public static void loadModuleConfig(InputStream zip, String name, List<ModuleConfig> configs) {
        BufferedReader in = null;
        try (ZipInputStream zin = new ZipInputStream(zip)) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (entry.getName().endsWith(".jar")) {
                    loadModuleConfig(zin, name, configs);
                }
                if (entry.getName().equals(name)) {
                    in = new BufferedReader(
                            new InputStreamReader(zin));
                    Yaml yaml = new Yaml(new Constructor(ModuleConfig.class));

                    configs.add(yaml.load(in));
                }
                zin.closeEntry();
            }
        } catch (IOException e) {
            e.printStackTrace();
           LOG.error("Could not load module.yml");
        }finally {
            if(in != null){
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    public boolean isInstalled(Module module) {
        // Retrieve all beans of type Module from the application context
        Map<String, Module> moduleBeans = applicationContext.getBeansOfType(Module.class);

        // Check if any module bean has the same name as the provided module
        return moduleBeans.values().stream()
                .anyMatch(m -> m.getName().equals(module.getName()));
    }
    @Transactional
    public ModuleResponse installModule(Module module, Boolean install, Boolean multi) {
        if (!module.isNew()) {
            List<ModuleDependency> dependencies = moduleDependencyRepository.findDependencies(module);
            dependencies.forEach(dependency -> {
                if (isInstalled(dependency.getDependency()) ){
                    installModule(dependency.getDependency(), false, multi);
                }
            });
        } else {
            ModuleResponse moduleResponse = installDependencies(module, multi);
            if (moduleResponse.getType().equals(ModuleResponse.Type.ERROR)) {
                return moduleResponse;
            }

        }
        return bootstrapModule(module, install, multi);
    }

    @Transactional
    public synchronized ModuleResponse bootstrapModule(final Module module, Boolean install, Boolean multi) {
        LOG.debug("Bootstrap...");
        if (install == null || (module.getInstallOnBoot() != null && module.getInstallOnBoot())) {
            install = true;
        }
        ModuleResponse response = new ModuleResponse();
        response.setModule(module);
        LOG.debug("\tStarting module {}", module.getName());

        if (isInstalled(module)) {
            LOG.debug("Nothing to do, module {} already installed and running", module.getName());
            response.setMessage(String.format("Nothing to do, module %s already installed and running", module.getName()));
            response.setType(ModuleResponse.Type.SUCCESS);
            return response;
        }

        module.setInError(true);
        if (!module.isNew()) {
            moduleRepository.save(module);
        }

        try {
            final Path moduleRuntimePath = Paths.get(MODULE_PATH, "runtime", module.getName());
            if (Files.exists(moduleRuntimePath)) {
                FileUtils.deleteDirectory(moduleRuntimePath.toFile());
            }

            Optional<ModuleArtifact> moduleArtifact = moduleArtifactRepository.findByModule(module);
            if (moduleArtifact.isPresent()) {
                byte[] data = moduleArtifact.get().getData();
                Path tmpFile = Files.createTempFile("", ".jar");
                try (FileOutputStream fileOutputStream = new FileOutputStream(tmpFile.toFile());
                     ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(data)) {
                    IOUtils.copy(byteArrayInputStream, fileOutputStream);
                }
                copyPathFromJar(tmpFile.toUri().toURL(), "/", moduleRuntimePath);
                Files.delete(tmpFile);
            } else {
                try {
                    readFile(module.getArtifact()).close();
                } catch (FileNotFoundException e) {
                    response.setMessage(String.format("Module artifact for %s not found", module.getName()));
                    response.setType(ModuleResponse.Type.ERROR);
                    return response;
                }
                copyPathFromJar(moduleManager.getURL(module.getArtifact()), "/", moduleRuntimePath);
            }

            addClassPathUrl(moduleRuntimePath.toUri().toURL(), ModuleLifecycle.class.getClassLoader());

        } catch (Exception e) {
            LOG.error("Error during module bootstrapping: {}", e.getMessage(), e);
            response.setMessage(String.format("Error during module bootstrapping: %s", e.getMessage()));
            response.setType(ModuleResponse.Type.ERROR);
            return response;
        }

        ClassPathScanningCandidateComponentProvider provider =
                new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AssignableTypeFilter(ModuleLifecycle.class));
        Set<BeanDefinition> beans = provider.findCandidateComponents(module.getBasePackage());
        for (BeanDefinition bd : beans) {
            try {
                Class<?> clz = ModuleLifecycle.class.getClassLoader().loadClass(bd.getBeanClassName());
                Object object = clz.newInstance();
                ((ModuleLifecycle) object).preInstall();
            } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
                LOG.error("Error during pre-installation: {}", e.getMessage(), e);
            }
        }
        printEntityManager();
        removeDuplicateRepositories();
        LOG.info("Module {} successfully installed.", module.getName());

        // Notify about module installation if not multi
        if (multi != null && !multi) {
            messagingTemplate.convertAndSend("/topic/modules-changed", module.getName());
        }

        // Update module state and save changes
        module.setInError(false);
        module.setInstallOnBoot(false);
        moduleRepository.save(module);

        // Prepare response indicating successful installation
        response.setMessage(String.format("Module %s successfully installed", module.getName()));
        response.setType(ModuleResponse.Type.SUCCESS);
        return response;
    }
    private void printEntityManager() {

        String[] entityManagerFactoryBeanNames = applicationContext.getBeanNamesForType(LocalContainerEntityManagerFactoryBean.class);
        for (String beanName : entityManagerFactoryBeanNames) {
            if (beanName.contains("@")) {
                LOG.info("Removing: {}", beanName);
                removeBean(beanName);
            }
        }
    }

    private void removeDuplicateRepositories() {
        ApplicationContext applicationContext = ApplicationContextProvider.getApplicationContext();

        // Remove duplicate repositories
        String[] repositoryBeanNames = applicationContext.getBeanNamesForType(Repository.class);
        removeBeansWithDynamicProxy(repositoryBeanNames);

        // Remove duplicate EntityManager beans
        String[] entityManagerBeanNames = applicationContext.getBeanNamesForType(EntityManager.class);
        removeBeansWithDynamicProxy(entityManagerBeanNames);
    }

    private void removeBeansWithDynamicProxy(String[] beanNames) {
        for (String beanName : beanNames) {
            if (beanName.contains("@")) {
                LOG.info("Removing: {}", beanName);
                removeBean(beanName);
            }
        }
    }


    static void addClassPathUrl(URL url, ClassLoader classLoader) {
        try {
            Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            method.setAccessible(true);
            method.invoke(classLoader, url);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void removeBean(String beanName) {
      //  LOG.debug("Removing: {}", beanName);
        ApplicationContext applicationContext = ApplicationContextProvider.getApplicationContext();
        if (applicationContext instanceof ConfigurableApplicationContext) {
            ConfigurableListableBeanFactory beanFactory = ((ConfigurableApplicationContext) applicationContext).getBeanFactory();
            if (beanFactory instanceof BeanDefinitionRegistry) {
                BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
                if (registry.containsBeanDefinition(beanName)) {
                    registry.removeBeanDefinition(beanName);
                }
            }
        }
    }

    public static void copyPathFromJar(final URL jarPath, final String path, final Path target) throws Exception {
        Map<String, String> env = new HashMap<>();
        String absPath = jarPath.toString();
        URI uri = URI.create("jar:" + absPath);
        try (FileSystem zipfs = FileSystems.newFileSystem(uri, env)) {
            Path pathInZipfile = zipfs.getPath(path);
            Files.walkFileTree(pathInZipfile, new SimpleFileVisitor<Path>() {

                private Path currentTarget;

                @Override
                public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
                    currentTarget = target.resolve(pathInZipfile.relativize(dir)
                            .toString());
                    if (!Files.exists(currentTarget)) {
                        Files.createDirectories(currentTarget);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                    Files.copy(file, target.resolve(pathInZipfile.relativize(file)
                            .toString()), StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }





        // Additional logic...
        // You'll need to provide implementations for methods such as `removeDuplicateJ

    private ModuleResponse installDependencies(Module module, Boolean multi) {
        ModuleConfig moduleConfig = getModuleConfig(module);
        if (moduleConfig != null) {
            Map<String, String> dependencies = moduleConfig.getDependencies();
            final boolean[] error = {false};
            final ModuleResponse[] response = {new ModuleResponse()};
            response[0].setType(ModuleResponse.Type.ERROR);
            dependencies.keySet().stream()
                    .filter(n -> !n.equals("BaseModule"))
                    .forEach(name -> {
                        response[0].setMessage(String.format("Dependency %s not installed; cannot install module", name));
                        response[0] = moduleRepository.findByName(name).flatMap(dependency -> {
                            ModuleResponse response1 = new ModuleResponse();
                            response1.setMessage(String.format("Dependency %s not installed; cannot install module", name));
                            response1.setType(ModuleResponse.Type.SUCCESS);
                            if (isInstalled(dependency)) {
                                response1 = installModule(module, false, multi);
                            }
                            return Optional.of(response1);
                        }).orElse(response[0]);
                        if (response[0].getType().equals(ModuleResponse.Type.ERROR)) {
                            error[0] = true;
                        }
                    });
            if (error[0]) {
                return response[0];
            }
        }
        ModuleResponse response = new ModuleResponse();
        response.setType(ModuleResponse.Type.SUCCESS);
        return response;
    }

    public ModuleConfig getModuleConfig(Module module) {
        List<ModuleConfig> moduleConfigs = new ArrayList<>();
        try {
            loadModuleConfig(readFile(module.getArtifact()), "module.yml", moduleConfigs);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return !moduleConfigs.isEmpty() ? moduleConfigs.get(0) : null;
    }





    public InputStream readFile(String file) throws FileNotFoundException {
        return new FileInputStream(rootLocation.resolve(file).toFile());
    }

    public URL getURL(String file) {
        try {
            return rootLocation.resolve(file).toUri().toURL();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return null;
    }




    public ModuleResponse uploadModuleData(MultipartFile file) throws IOException {
        List<ModuleConfig> configs = new ArrayList<>();
        Module module = new Module();
        InputStream inputStream = null;
        try {
            inputStream = file.getInputStream();
            loadModuleConfig(inputStream, "module.yml", configs);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if (inputStream != null){
                inputStream.close();
            }
        }

        if (configs.size() > 0) {
            ModuleConfig config = configs.get(0);
            /*String yaml = MAPPER.writeValueAsString(config);
            if(!ConfigSchemaValidator.isValid(yaml)){
                module.setType(ERROR);
                module.setMessage("module.yml not well formed and validation failed");
                return module;
            }*/

            String fileName = moduleManager.store(config.getName(), file);
            loadModuleConfig(readFile(fileName), "module.yml", configs);
            ModuleManager.VersionInfo versionInfo = readVersionInfo(readFile(fileName));
            module.setArtifact(fileName);
            module.setName(config.getName());
            module.setVersion(versionInfo.version);
            module.setDescription(versionInfo.projectName);
            module.setUmdLocation(config.getUmdLocation());
            module.setBasePackage(config.getBasePackage());
            module.setPermissions(config.getPermissions()
                    .stream()
                    .map(permission -> {permission.setModuleName(config.getName()); return permission;})
                    .collect(Collectors.toSet()));
            module.setPriority(config.getPriority());
            if(!config.getDependencies().isEmpty()){
                config.getDependencies().forEach((k, v)->{
                    if(!isInstalled(k)){
                        module.setType(ERROR);
                        module.setMessage(module.getName() + " depends on " + k +" "+ v);
                    } else if (isInstalled(k)){
                        Optional<Module> optionalModule = moduleRepository.findByNameAndActive(k, true);
                        if(optionalModule.isPresent()){
                            Module module1 = optionalModule.get();
                            if(Integer.valueOf(module1.getVersion().replace(".", "")) > Integer.valueOf(v.replace(".", ""))){
                                module.setType(SUCCESS);
                                module.setMessage(module.getName() + " [ depends on " + k +" "+ v +" which is a lower version]");
                                String desc = module.getDescription() + " " + module.getName() + " [ depends on " + k +" "+ v +" which is a lower version]";
                                module.setDescription(desc);
                            } else  if(Integer.valueOf(module1.getVersion().replace(".", "")) > Integer.valueOf(v.replace(".", ""))){
                                module.setType(ERROR);
                                module.setMessage(module.getName() + " depends on " + k +" "+ v);
                            }
                        }
                    }/*else  if(!moduleRepository.findByNameAndVersionAndActive(k, v, true).isPresent()){
                        module.setType(ERROR);
                        module.setMessage(module.getName() + " depends on " + k +" "+ v);
                    }*/
                });
            }
        }
        boolean install = false;
        boolean multi = false;
        return installModule(module,install,multi);
    }

    ModuleManager.VersionInfo readVersionInfo(InputStream jarIs) throws IOException {
        ModuleManager.VersionInfo versionInfo = ModuleManager.VersionInfo.UNKNOWN;

        JarInputStream jarStream = new JarInputStream(jarIs);
        Manifest manifest = jarStream.getManifest();
        if (manifest != null) {
            Attributes attr = manifest.getMainAttributes();

            versionInfo = new ModuleManager.VersionInfo();
            versionInfo.manifest = manifest;
            versionInfo.available = true;
            versionInfo.projectName = StringUtils.defaultString (
                    attr.getValue("Implementation-Title"), ModuleManager.VersionInfo.UNKNOWN_VALUE
            );
            versionInfo.version = StringUtils.defaultString(
                    attr.getValue("Implementation-Version"), ModuleManager.VersionInfo.UNKNOWN_VALUE
            );

            String buildTime = attr.getValue("Build-Time");

            if (buildTime != null) {
                try {
                    versionInfo.buildTime = DateUtils.parseDate(buildTime, "yyyyMMdd-HHmm",
                            "yyyy-MM-dd'T'HH:mm:ss'Z'");
                } catch (ParseException ignored) {

                }
            }
            jarStream.close();
        }
        return versionInfo;
    }

}
