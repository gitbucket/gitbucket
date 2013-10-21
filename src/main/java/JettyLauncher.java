import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.webapp.WebAppContext;

import java.io.IOException;
import java.net.URL;
import java.security.ProtectionDomain;

public class JettyLauncher {
    public static void main(String[] args) throws Exception {
        String host = null;
        int port = 8080;
        String contextPath = "/";
        boolean forceHttps = false;

        for(String arg: args) {
            if(arg.startsWith("--") && arg.contains("=")) {
                String[] dim = arg.split("=");
                if(dim.length >= 2) {
                    if(dim[0].equals("--host")) {
                        host = dim[1];
                    } else if(dim[0].equals("--port")) {
                        port = Integer.parseInt(dim[1]);
                    } else if(dim[0].equals("--prefix")) {
                        contextPath = dim[1];
                    } else if(dim[0].equals("--https") && (dim[1].equals("1") || dim[1].equals("true"))) {
                        forceHttps = true;
                    }
                }
            }
        }

        Server server = new Server();

        CustomConnector connector = new CustomConnector(forceHttps);
        if(host != null) {
            connector.setHost(host);
        }
        connector.setMaxIdleTime(1000 * 60 * 60);
        connector.setSoLingerTime(-1);
        connector.setPort(port);
        server.addConnector(connector);

        WebAppContext context = new WebAppContext();
        ProtectionDomain domain = JettyLauncher.class.getProtectionDomain();
        URL location = domain.getCodeSource().getLocation();

        context.setContextPath(contextPath);
        context.setDescriptor(location.toExternalForm() + "/WEB-INF/web.xml");
        context.setServer(server);
        context.setWar(location.toExternalForm());

        server.setHandler(context);
        server.start();
        server.join();
    }
}

class CustomConnector extends SelectChannelConnector {
    boolean mForceHttps;

    public CustomConnector(boolean forceHttps) {
        mForceHttps = forceHttps;
    }

    @Override
    public void customize(final EndPoint endpoint, final Request request) throws IOException {
        if (mForceHttps) {
            request.setScheme("https");
            super.customize(endpoint, request);
        }
    }
}
