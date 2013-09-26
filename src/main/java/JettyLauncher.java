import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;

import java.net.URL;
import java.security.ProtectionDomain;

public class JettyLauncher {
    public static void main(String[] args) throws Exception {
        int port = System.getenv("PORT") != null ? Integer.parseInt(System.getenv("PORT")) : 8080;
        Server server = new Server(port);
        WebAppContext context  = new WebAppContext();
        ProtectionDomain domain   = JettyLauncher.class.getProtectionDomain();
        URL location = domain.getCodeSource().getLocation();

        context.setContextPath("/");
        context.setDescriptor(location.toExternalForm() + "/WEB-INF/web.xml");
        context.setServer(server);
        context.setWar(location.toExternalForm());

        server.setHandler(context);
        server.start();
        server.join();
    }
}
