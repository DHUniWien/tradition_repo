/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.stemmaweb.services;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

//import org.apache.log4j.Logger;

import org.neo4j.graphdb.GraphDatabaseService;

/**
 *
 * @author marijn
 */
public class ApplicationContextListener implements ServletContextListener {

    private static final String DB_ENV = System.getenv("STEMMAREST_HOME");
    private static final String DB_PATH = DB_ENV == null ? "/var/lib/stemmarest" : DB_ENV;
    // final static Logger logger = Logger.getLogger(ApplicationContextListener.class);
    private ServletContext context = null;

    public void contextDestroyed(ServletContextEvent event) {
        this.context = event.getServletContext();
        try {
            GraphDatabaseService db = (GraphDatabaseService) this.context.getAttribute("neo4j");
            if (db != null) {
                db.shutdown();
                JVMHelper.immolativeShutdown();
            }
            System.out.println("Stemmarest webapp - Neo4j shutdown finished!");
        } catch (Exception e) {
            // logger.debug("This is debug: shut down error");
            // logger.error("failed!", e);
            e.printStackTrace();
        }
    }

    public void contextInitialized(ServletContextEvent event) {
        GraphDatabaseService db = new GraphDatabaseServiceProvider(DB_PATH).getDatabase();
        this.context = event.getServletContext();
        this.context.setAttribute("neo4j", db);
        //Output a simple message to the server's console
        // logger.debug("This is debug - Listener: context initialized");
        DatabaseService.createRootNode(db);


    }


}