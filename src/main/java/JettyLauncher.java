import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.SecuredRedirectHandler;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.eclipse.jetty.server.session.DefaultSessionCache;
import org.eclipse.jetty.server.session.FileSessionDataStore;
import org.eclipse.jetty.server.session.SessionCache;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.webapp.WebAppContext;

import java.io.File;
import java.net.InetAddress;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toSet;

public class JettyLauncher {

    private interface Defaults {

        String CONNECTORS = "http";
        String HOST = "0.0.0.0";

        int HTTP_PORT = 8080;
        int HTTPS_PORT = 8443;

        boolean REDIRECT_HTTPS = false;
    }

    private interface Connectors {

        String HTTP = "http";
        String HTTPS = "https";
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");

        String connectors = getEnvironmentVariable("gitbucket.connectors");
        String host = getEnvironmentVariable("gitbucket.host");
        String port = getEnvironmentVariable("gitbucket.port");
        String securePort = getEnvironmentVariable("gitbucket.securePort");
        String keyStorePath = getEnvironmentVariable("gitbucket.keyStorePath");
        String keyStorePassword = getEnvironmentVariable("gitbucket.keyStorePassword");
        String keyManagerPassword = getEnvironmentVariable("gitbucket.keyManagerPassword");
        String redirectHttps = getEnvironmentVariable("gitbucket.redirectHttps");
        String contextPath = getEnvironmentVariable("gitbucket.prefix");
        String tmpDirPath = getEnvironmentVariable("gitbucket.tempDir");
        String jettyIdleTimeout = getEnvironmentVariable("gitbucket.jettyIdleTimeout");
        boolean saveSessions = false;

        for(String arg: args) {
            if (arg.equals("--save_sessions")) {
                saveSessions = true;
            }
            if (arg.equals("--disable_news_feed")) {
                System.setProperty("gitbucket.disableNewsFeed", "true");
            }
            if (arg.equals("--disable_cache")) {
                System.setProperty("gitbucket.disableCache", "true");
            }
            if(arg.startsWith("--") && arg.contains("=")) {
                String[] dim = arg.split("=", 2);
                if(dim.length == 2) {
                    switch (dim[0]) {
                        case "--connectors":
                            connectors = dim[1];
                            break;
                        case "--host":
                            host = dim[1];
                            break;
                        case "--port":
                            port = dim[1];
                            break;
                        case "--secure_port":
                            securePort = dim[1];
                            break;
                        case "--key_store_path":
                            keyStorePath = dim[1];
                            break;
                        case "--key_store_password":
                            keyStorePassword = dim[1];
                            break;
                        case "--key_manager_password":
                            keyManagerPassword = dim[1];
                            break;
                        case "--redirect_https":
                            redirectHttps = dim[1];
                            break;
                        case "--prefix":
                            contextPath = dim[1];
                            break;
                        case "--gitbucket.home":
                            System.setProperty("gitbucket.home", dim[1]);
                            break;
                        case "--temp_dir":
                            tmpDirPath = dim[1];
                            break;
                        case "--plugin_dir":
                            System.setProperty("gitbucket.pluginDir", dim[1]);
                            break;
                        case "--jetty_idle_timeout":
                            jettyIdleTimeout = dim[1];
                            break;
                    }
                }
            }
        }

        if (contextPath != null && !contextPath.startsWith("/")) {
            contextPath = "/" + contextPath;
        }

        final String hostName = InetAddress.getByName(fallback(host, Defaults.HOST)).getHostName();

        final Server server = new Server();

        final Set<String> connectorsSet = Stream.of(fallback(connectors, Defaults.CONNECTORS)
            .toLowerCase().split(",")).map(String::trim).collect(toSet());

        final List<ServerConnector> connectorInstances = new ArrayList<>();

        final HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setSendServerVersion(false);
        if (connectorsSet.contains(Connectors.HTTPS)) {
            httpConfig.setSecurePort(fallback(securePort, Defaults.HTTPS_PORT, Integer::parseInt));
        }
        if (jettyIdleTimeout != null && jettyIdleTimeout.trim().length() != 0) {
            httpConfig.setIdleTimeout(Long.parseLong(jettyIdleTimeout.trim()));
        } else {
            httpConfig.setIdleTimeout(300000L); // default is 5min
        }

        if (connectorsSet.contains(Connectors.HTTP)) {
            final ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(httpConfig));
            connector.setHost(hostName);
            connector.setPort(fallback(port, Defaults.HTTP_PORT, Integer::parseInt));

            connectorInstances.add(connector);
        }

        if (connectorsSet.contains(Connectors.HTTPS)) {
            final SslContextFactory sslContextFactory = new SslContextFactory.Server();

            sslContextFactory.setKeyStorePath(requireNonNull(keyStorePath,
                "You must specify a path to an SSL keystore via the --key_store_path command line argument" +
                    " or GITBUCKET_KEYSTOREPATH environment variable."));

            sslContextFactory.setKeyStorePassword(requireNonNull(keyStorePassword,
                "You must specify a an SSL keystore password via the --key_store_password argument" +
                    " or GITBUCKET_KEYSTOREPASSWORD environment variable."));

            sslContextFactory.setKeyManagerPassword(requireNonNull(keyManagerPassword,
                "You must specify a key manager password via the --key_manager_password' argument" +
                    " or GITBUCKET_KEYMANAGERPASSWORD environment variable."));

            final HttpConfiguration httpsConfig = new HttpConfiguration(httpConfig);
            httpsConfig.addCustomizer(new SecureRequestCustomizer());

            final ServerConnector connector = new ServerConnector(server,
                new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString()),
                new HttpConnectionFactory(httpsConfig));

            connector.setHost(hostName);
            connector.setPort(fallback(securePort, Defaults.HTTPS_PORT, Integer::parseInt));

            connectorInstances.add(connector);
        }

        require(!connectorInstances.isEmpty(),
            "No server connectors could be configured, please check your --connectors command line argument" +
                " or GITBUCKET_CONNECTORS environment variable.");

        server.setConnectors(connectorInstances.toArray(new ServerConnector[0]));

        WebAppContext context = new WebAppContext();

        if(saveSessions) {
            File sessDir = new File(getGitBucketHome(), "sessions");
            if(!sessDir.exists()){
                mkdir(sessDir);
            }
            SessionHandler sessions = context.getSessionHandler();
            SessionCache cache = new DefaultSessionCache(sessions);
            FileSessionDataStore fsds = new FileSessionDataStore();
            fsds.setStoreDir(sessDir);
            cache.setSessionDataStore(fsds);
            sessions.setSessionCache(cache);
        }

        File tmpDir;
        if(tmpDirPath == null || tmpDirPath.equals("")){
            tmpDir = new File(getGitBucketHome(), "tmp");
            if(!tmpDir.exists()){
                mkdir(tmpDir);
            }
        } else {
            tmpDir = new File(tmpDirPath);
            if(!tmpDir.exists()){
                throw new java.io.FileNotFoundException(
                    String.format("temp_dir \"%s\" not found", tmpDirPath));
            } else if(!tmpDir.isDirectory()) {
                throw new IllegalArgumentException(
                    String.format("temp_dir \"%s\" is not a directory", tmpDirPath));
            }
        }
        context.setTempDirectory(tmpDir);

        // Disabling the directory listing feature.
        context.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed", "false");

        ProtectionDomain domain = JettyLauncher.class.getProtectionDomain();
        URL location = domain.getCodeSource().getLocation();

        context.setContextPath(contextPath == null ? "" : contextPath);
        context.setDescriptor(location.toExternalForm() + "/WEB-INF/web.xml");
        context.setServer(server);
        context.setWar(location.toExternalForm());

        final HandlerList handlers = new HandlerList();

        if (fallback(redirectHttps, Defaults.REDIRECT_HTTPS, Boolean::parseBoolean)) {
            handlers.addHandler(new SecuredRedirectHandler());
        }

        handlers.addHandler(addStatisticsHandler(context));

        server.setHandler(handlers);
        server.setStopAtShutdown(true);
        server.setStopTimeout(7_000);
        server.start();
        server.join();
    }

    private static File getGitBucketHome(){
        String home = System.getProperty("gitbucket.home");
        if(home != null && home.length() > 0){
            return new File(home);
        }
        home = System.getenv("GITBUCKET_HOME");
        if(home != null && home.length() > 0){
            return new File(home);
        }
        return new File(System.getProperty("user.home"), ".gitbucket");
    }

    private static String getEnvironmentVariable(String key){
        String value =  System.getenv(key.toUpperCase().replace('.', '_'));
        if (value != null && value.length() == 0){
            return null;
        } else {
            return value;
        }
    }

    private static <T, R> T fallback(R value, T defaultValue, Function<R, T> converter) {
        return value == null ? defaultValue : converter.apply(value);
    }

    private static <T> T fallback(T value, T defaultValue) {
        return fallback(value, defaultValue, identity());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static <T> T requireNonNull(T value, String message) {
        require(value != null, message);
        return value;
    }

    private static void mkdir(File dir) {
        if (!dir.mkdirs()) {
            throw new RuntimeException("Unable to create directory: " + dir);
        }
    }

    private static Handler addStatisticsHandler(Handler handler) {
        // The graceful shutdown is implemented via the statistics handler.
        // See the following: https://bugs.eclipse.org/bugs/show_bug.cgi?id=420142
        final StatisticsHandler statisticsHandler = new StatisticsHandler();
        statisticsHandler.setHandler(handler);
        return statisticsHandler;
    }
}
