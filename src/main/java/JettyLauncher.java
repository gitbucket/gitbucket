import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.eclipse.jetty.webapp.WebAppContext;

import java.io.File;
import java.net.URL;
import java.net.InetSocketAddress;
import java.security.ProtectionDomain;

public class JettyLauncher {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");

        String host = null;
        int port = 8080;
        InetSocketAddress address = null;
        String contextPath = "/";
        String tmpDirPath="";
        boolean forceHttps = false;

        for(String arg: args) {
            if(arg.startsWith("--") && arg.contains("=")) {
                String[] dim = arg.split("=");
                if(dim.length >= 2) {
                    switch (dim[0]) {
                        case "--host":
                            host = dim[1];
                            break;
                        case "--port":
                            port = Integer.parseInt(dim[1]);
                            break;
                        case "--prefix":
                            contextPath = dim[1];
                            if (!contextPath.startsWith("/")) {
                                contextPath = "/" + contextPath;
                            }
                            break;
                        case "--max_file_size":
                            System.setProperty("gitbucket.maxFileSize", dim[1]);
                            break;
                        case "--upload_timeout":
                            System.setProperty("gitbucket.UploadTimeout", dim[1]);
                            break;
                        case "--max_file_size_issues":
                            System.setProperty("gitbucket.maxFileSizeIssues", dim[1]);
                            break;
                        case "--upload_timeout_issues":
                            System.setProperty("gitbucket.UploadTimeoutIssues", dim[1]);
                            break;
                        case "--max_file_size_releases":
                            System.setProperty("gitbucket.maxFileSizeReleases", dim[1]);
                            break;
                        case "--upload_timeout_releases":
                            System.setProperty("gitbucket.UploadTimeoutReleases", dim[1]);
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
                        case "--validate_password":
                            System.setProperty("gitbucket.validate.password", dim[1]);
                            break;
                    }
                }
            }
        }

        if(host != null) {
            address = new InetSocketAddress(host, port);
        } else {
            address = new InetSocketAddress(port);
        }

        Server server = new Server(address);

//        SelectChannelConnector connector = new SelectChannelConnector();
//        if(host != null) {
//            connector.setHost(host);
//        }
//        connector.setMaxIdleTime(1000 * 60 * 60);
//        connector.setSoLingerTime(-1);
//        connector.setPort(port);
//        server.addConnector(connector);

        // Disabling Server header
        for (Connector connector : server.getConnectors()) {
            for (ConnectionFactory factory : connector.getConnectionFactories()) {
                if (factory instanceof HttpConnectionFactory) {
                    ((HttpConnectionFactory) factory).getHttpConfiguration().setSendServerVersion(false);
                }
            }
        }

        WebAppContext context = new WebAppContext();

        File tmpDir;
        if(tmpDirPath.equals("")){
            tmpDir = new File(getGitBucketHome(), "tmp");
            if(!tmpDir.exists()){
                tmpDir.mkdirs();
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

        context.setContextPath(contextPath);
        context.setDescriptor(location.toExternalForm() + "/WEB-INF/web.xml");
        context.setServer(server);
        context.setWar(location.toExternalForm());
        if (forceHttps) {
            context.setInitParameter("org.scalatra.ForceHttps", "true");
        }

        Handler handler = addStatisticsHandler(context);

        server.setHandler(handler);
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

    private static Handler addStatisticsHandler(Handler handler) {
        // The graceful shutdown is implemented via the statistics handler.
        // See the following: https://bugs.eclipse.org/bugs/show_bug.cgi?id=420142
        final StatisticsHandler statisticsHandler = new StatisticsHandler();
        statisticsHandler.setHandler(handler);
        return statisticsHandler;
    }
}