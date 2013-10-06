import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.webapp.WebAppContext;

import java.net.URL;
import java.security.ProtectionDomain;

public class JettyLauncher {
    public static void main(String[] args) throws Exception {
        String host = null;
        int port = 8080;
        String contextPath = "/";

        for(String arg: args){
            if(arg.startsWith("--") && arg.contains("=")){
                String[] dim = arg.split("=");
                if(dim.length >= 2){
                    if(dim[0].equals("--host")){
                        host = dim[1];
                    } else if(dim[0].equals("--port")){
                        port = Integer.parseInt(dim[1]);
                    } else if(dim[0].equals("--prefix")){
                        contextPath = dim[1];
                    }
                }
            }
        }

        Server server = new Server();

        SelectChannelConnector connector = new SelectChannelConnector();
        if(host != null){
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
