package net.stemmaweb.model;

import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

/**
 * Provides a model for a user outside of the database. Can be parsed into a
 * json object.
 * 
 * @author PSE FS 2015 Team2
 */

@XmlRootElement
@JsonInclude(Include.NON_NULL)
public class UserModel {
    private String id;
    private String role;
    private Boolean active = true;
    private String email = "NOT YET SET";

    public UserModel() {}

    public UserModel(Node node) {
        try (Transaction tx = node.getGraphDatabase().beginTx()) {
            setId(node.getProperty("id").toString());
            if (node.hasProperty("role"))
                setRole(node.getProperty("role").toString());
            if (node.hasProperty("active"))
                setActive((Boolean) node.getProperty("active"));
            if (node.hasProperty("email"))
                setEmail(node.getProperty("email").toString());
            tx.success();
        }
    }

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
