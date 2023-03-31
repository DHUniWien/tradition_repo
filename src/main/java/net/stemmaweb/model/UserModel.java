package net.stemmaweb.model;

import javax.xml.bind.annotation.XmlRootElement;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import net.stemmaweb.services.GraphDatabaseServiceProvider;

/**
 * Provides a model for a user outside of the database. Can be parsed into a
 * json object.
 * 
 * @author PSE FS 2015 Team2
 */

@XmlRootElement
@JsonInclude(Include.NON_NULL)
public class UserModel {
    /**
     * ID of the user in question - either an email address or an OAuth ID token.
     */
    private String id;
    /**
     * Passphrase hash of the user in question.
     */
    private String passphrase = "NOT YET SET";
    /**
     * Role of the user in question. Valid values are 'user' and 'admin'.
     */
    private String role;
    /**
     * True if the user is active in the database and may be assigned new traditions.
     */
    private Boolean active = true;
    /**
     * Email address of the user in the database.
     */
    private String email = "NOT YET SET";

    public UserModel() {}

    public UserModel(Node node) {
        GraphDatabaseService db = new GraphDatabaseServiceProvider().getDatabase();
//    	try (Transaction tx = node.getGraphDatabase().beginTx()) {
        try (Transaction tx = db.beginTx()) {
            setId(node.getProperty("id").toString());
            if (node.hasProperty("passphrase"))
                setPassphrase(node.getProperty("passphrase").toString());
            if (node.hasProperty("role"))
                setRole(node.getProperty("role").toString());
            if (node.hasProperty("active"))
                setActive((Boolean) node.getProperty("active"));
            if (node.hasProperty("email"))
                setEmail(node.getProperty("email").toString());
            tx.close();
        }
    }

    public String getPassphrase () { return passphrase; }
    public void setPassphrase(String passphrase) {this.passphrase = passphrase; }
    public String getRole() {
        return role;
    }
    public void setRole(String role) {
        this.role = role;
    }
    public String getId() {
        return id;
    }
    public void setId(String id) { this.id = id; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }
}
