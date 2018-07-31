package listeners;

import jdbc.DBManager;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

public class MyContextListener implements ServletContextListener {
    public void contextInitialized(ServletContextEvent servletContextEvent) {
        System.out.println("Application started");
    }

    public void contextDestroyed(ServletContextEvent servletContextEvent) {
        DBManager.shutDownPool();
        System.out.println("Pool was shutdowned");
    }
}
