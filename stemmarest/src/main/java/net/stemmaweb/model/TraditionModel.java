package net.stemmaweb.model;

import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

/**
 * 
 * @author PSE FS 2015 Team2
 */

@XmlRootElement
@JsonInclude(Include.NON_NULL)
public class TraditionModel {
    private String id;
    private String name;
    private String language;
    private String isPublic;
    private String ownerId;

    public TraditionModel() {}

    public TraditionModel(Node node) {
        try (Transaction tx = node.getGraphDatabase().beginTx()) {
            setId(node.getProperty("id").toString());
            if (node.hasProperty("name"))
                setName(node.getProperty("name").toString());
            if (node.hasProperty("language"))
                setLanguage(node.getProperty("language").toString());
            // TODO make boolean
            if (node.hasProperty("isPublic"))
                setIsPublic(node.getProperty("isPublic").toString());
            // TODO necessary?
            if (node.hasProperty("ownerId"))
                setOwnerId(node.getProperty("ownerId").toString());
            tx.success();
        }
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public String getLanguage() {
        return language;
    }
    public void setLanguage(String language) {
        this.language = language;
    }
    public String getIsPublic() {
        return isPublic;
    }
    public void setIsPublic(String isPublic) {
        this.isPublic = isPublic;
    }
    public String getOwnerId() {
        return ownerId;
    }
    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }
}
