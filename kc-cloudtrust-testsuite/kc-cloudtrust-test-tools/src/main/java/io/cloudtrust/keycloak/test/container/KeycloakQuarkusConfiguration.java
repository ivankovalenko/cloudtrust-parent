package io.cloudtrust.keycloak.test.container;

import io.cloudtrust.exception.CloudtrustRuntimeException;
import io.cloudtrust.keycloak.test.util.TestSuiteParameters;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jboss.logging.Logger;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KeycloakQuarkusConfiguration {
    private static final Logger log = Logger.getLogger(KeycloakQuarkusConfiguration.class);
    private static final String DEFAULT_SEARCH_PATH = "./target";

    private final List<File> jarFiles = new ArrayList<>();
    private final List<File> classpath = new ArrayList<>();
    private final List<String> buildArguments = new ArrayList<>();
    private final List<String> execArguments = new ArrayList<>();
    private final Map<String, String> environment = new HashMap<>();
    private final Map<String, String> runProperties = new HashMap<>();
    private String hostname = null;
    private Integer bindHttpsPort = null;
    private Integer bindHttpPort = null;
    private String adminRealm = null;
    private String adminUsername = null;
    private String adminSecurity = null;
    private String adminClientId = null;
    private Path keycloakPath = null;
    private long startTimeout = TimeUnit.MINUTES.toMillis(2);
    private boolean captureOutput = false;
    private Exception startException;

    public static class KeycloakQuarkusConfigurationBuilder {
        private final KeycloakQuarkusConfiguration cfg = new KeycloakQuarkusConfiguration();

        private KeycloakQuarkusConfigurationBuilder() {
            cfg.bindHttpsPort = TestSuiteParameters.get().getEnvAsInt("auth.server.https.port", null);
            cfg.bindHttpPort = TestSuiteParameters.get().getEnvAsInt("auth.server.http.port", 8180);
        }

        public KeycloakQuarkusConfigurationBuilder setKeycloakPath(String path) {
            if (path == null) {
                return this.searchKeycloakPath(DEFAULT_SEARCH_PATH);
            } else {
                cfg.keycloakPath = Paths.get(path);
            }
            return this;
        }

        public KeycloakQuarkusConfigurationBuilder setKeycloakPath(Path path) {
            if (path == null) {
                return this.searchKeycloakPath(DEFAULT_SEARCH_PATH);
            } else {
                cfg.keycloakPath = path;
            }
            return this;
        }

        public KeycloakQuarkusConfigurationBuilder searchKeycloakPath(String path) {
            return searchKeycloakPath(Paths.get(path));
        }

        public KeycloakQuarkusConfigurationBuilder searchKeycloakPath(Path path) {
            cfg.keycloakPath = cfg.findKeycloakPath(path.toFile());
            if (cfg.keycloakPath == null) {
                throw new CloudtrustRuntimeException("Can't find keycloak instance in " + path);
            }
            log.info("Detected keycloak home at " + cfg.keycloakPath);
            return this;
        }

        public KeycloakQuarkusConfigurationBuilder addModulesJar(List<String> filenames) {
            if (filenames != null) {
                filenames.forEach(this::addModuleJar);
            }
            return this;
        }

        public KeycloakQuarkusConfigurationBuilder addModuleJar(String filename) {
            return addModuleJar(new File(filename));
        }

        public KeycloakQuarkusConfigurationBuilder addModuleJar(File jarFile) {
            if (!jarFile.exists()) {
                boolean found = false;
                Pair<String, String> splitted = splitVersion(jarFile.getName());
                if (splitted != null) {
                    File[] possibleFiles = jarFile.getParentFile().listFiles((dir, name) -> splitted.equals(splitVersion(name)));
                    if (possibleFiles != null && possibleFiles.length == 1) {
                        jarFile = possibleFiles[0];
                        log.info("Found alternative: " + jarFile.getAbsolutePath());
                        found = true;
                    }
                }
                if (!found) {
                    log.info(jarFile.getAbsolutePath() + " doesn't exist... No alternative version of " + jarFile.getName() + " found");
                }
            }
            cfg.jarFiles.add(jarFile);
            return this;
        }

        private final Pattern versionPattern = Pattern.compile("^(.+)(-\\d+.\\d+.\\d+)([\\.\\-].+)$");

        private Pair<String, String> splitVersion(String name) {
            Matcher m = versionPattern.matcher(name);
            if (m.find()) {
                return Pair.of(m.group(1), m.group(3));
            }
            return null;
        }

        public KeycloakQuarkusConfigurationBuilder addClasspath(List<String> classpath) {
            if (classpath != null) {
                classpath.forEach(this::addClasspath);
            }
            return this;
        }

        public KeycloakQuarkusConfigurationBuilder addClasspath(String filename) {
            String suffix = ".class";
            if (!filename.endsWith(suffix)) {
                suffix = ".jar";
                if (!filename.endsWith(suffix)) {
                    throw new CloudtrustRuntimeException(filename + " is not a class file");
                }
            }
            File file = new File(filename);
            if (!file.exists()) {
                throw new CloudtrustRuntimeException("addClasspath: can't find class/jar " + filename);
            }
            cfg.classpath.add(file);
            return this;
        }

        public KeycloakQuarkusConfigurationBuilder addBuildArgument(String arg) {
            cfg.buildArguments.add(arg);
            return this;
        }

        public KeycloakQuarkusConfigurationBuilder addExecArgument(String arg) {
            cfg.execArguments.add(arg);
            return this;
        }

        public KeycloakQuarkusConfigurationBuilder addProperty(String key, String value) {
            cfg.runProperties.put(key, value);
            return this;
        }

        public KeycloakQuarkusConfigurationBuilder addProperty(String key, int value) {
            return this.addProperty(key, String.valueOf(value));
        }

        public KeycloakQuarkusConfigurationBuilder addProperties(Map<String, String> properties) {
            if (properties != null) {
                cfg.runProperties.putAll(properties);
            }
            return this;
        }

        public KeycloakQuarkusConfigurationBuilder addEnvironment(String key, String value) {
            cfg.environment.put(key, value);
            return this;
        }

        public KeycloakQuarkusConfigurationBuilder setHostname(String hostname) {
            cfg.hostname = hostname;
            return this;
        }

        public KeycloakQuarkusConfigurationBuilder setBindHttpPort(Integer bindHttpPort) {
            cfg.bindHttpPort = bindHttpPort;
            return this;
        }

        public KeycloakQuarkusConfigurationBuilder setBindHttpsPort(Integer bindHttpsPort) {
            cfg.bindHttpsPort = bindHttpsPort;
            return this;
        }

        public KeycloakQuarkusConfigurationBuilder setAdminRealm(String realm) {
            cfg.adminRealm = realm;
            return this;
        }

        public KeycloakQuarkusConfigurationBuilder setAdminUsername(String username) {
            cfg.adminUsername = username;
            return this;
        }

        public KeycloakQuarkusConfigurationBuilder setAdminSecurity(String security) {
            cfg.adminSecurity = security;
            return this;
        }

        public KeycloakQuarkusConfigurationBuilder setAdminClient(String clientId) {
            cfg.adminClientId = clientId;
            return this;
        }

        public KeycloakQuarkusConfigurationBuilder setStartTimeoutMillis(long timeout) {
            cfg.startTimeout = timeout;
            return this;
        }

        public KeycloakQuarkusConfiguration build() {
            return this.cfg;
        }
    }

    private KeycloakQuarkusConfiguration() {
        try {
            this.keycloakPath = findKeycloakPath(new File(DEFAULT_SEARCH_PATH));
        } catch (CloudtrustRuntimeException cre) {
            // Ignore when initializing default configuration
        }
    }

    public static KeycloakQuarkusConfigurationBuilder createBuilder() {
        return new KeycloakQuarkusConfigurationBuilder();
    }

    public Path getKeycloakPath() {
        return this.keycloakPath;
    }

    public Path resolve(String other) {
        return this.keycloakPath.resolve(other);
    }

    private Path findKeycloakPath(File dir) {
        for (File child : dir.listFiles()) {
            if (child.isDirectory()) {
                if (isKeycloakHome(child)) {
                    return child.toPath();
                }
                Path res = findKeycloakPath(child);
                if (res != null) {
                    return res;
                }
            }
        }
        return null;
    }

    private boolean isKeycloakHome(File dir) {
        if (!dir.getName().startsWith("keycloak")) {
            return false;
        }
        if (!(new File(dir, "providers").isDirectory() && new File(dir, "conf").isDirectory())) {
            return false;
        }
        File bin = new File(dir, "bin");
        return bin.isDirectory() && new File(bin, "kc.sh").isFile();
    }

    public Collection<File> getModuleJarFiles() {
        return this.jarFiles;
    }

    public Collection<File> getClasspath() {
        return this.classpath;
    }

    public List<String> getBuildArguments() {
        return this.buildArguments;
    }

    public List<String> getExecArguments() {
        return this.execArguments;
    }

    public Map<String, String> getProperties() {
        return this.runProperties;
    }

    public Map<String, String> getEnvironment() {
        return this.environment;
    }

    public String getHostname() {
        return StringUtils.defaultIfBlank(this.hostname, "localhost");
    }

    public Integer getBindHttpPort() {
        return this.bindHttpPort == null ? 8080 : this.bindHttpPort;
    }

    public Integer getBindHttpsPort() {
        return this.bindHttpsPort;
    }

    public String getAdminRealm() {
        return StringUtils.defaultIfBlank(this.adminRealm, "master");
    }

    public String getAdminUsername() {
        return StringUtils.defaultIfBlank(this.adminUsername, "admin");
    }

    public String getAdminSecurity() {
        return StringUtils.defaultIfBlank(this.adminSecurity, "admin");
    }

    public String getAdminClientId() {
        return StringUtils.defaultIfBlank(this.adminClientId, "admin-cli");
    }

    public long getStartTimeout() {
        return this.startTimeout;
    }

    public boolean isCaptureOutput() {
        return this.captureOutput;
    }

    public void setCaptureOutput(boolean captureOutput) {
        this.captureOutput = captureOutput;
    }

    public String getBaseUrl() {
        if (this.getBindHttpsPort() != null) {
            return "https://" + this.getHostname() + ":" + this.getBindHttpsPort();
        }
        return "http://" + this.getHostname() + ":" + this.getBindHttpPort();
    }

    public Exception getKeycloakStartException() {
        return startException;
    }

    public void setKeycloakStartException(Exception ex) {
        this.startException = ex;
    }
}
