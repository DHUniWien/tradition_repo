package net.stemmaweb.model;

import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import net.stemmaweb.rest.ERelations;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;

/**
 * 
 * @author PSE FS 2015 Team2
 */

@XmlRootElement
@JsonInclude(Include.NON_NULL)
public class TraditionModel {

    @SuppressWarnings("unused")
    private enum Direction {
        LR,  // left-to-right text
        RL,  // right-to-left text
        BI,  // bidirectional text
    }

    private String id;
    private String name;
    private String language;
    private Direction direction;
    private Boolean isPublic;
    private Integer stemweb_jobid;
    private String ownerId;

    public TraditionModel() {}

    public TraditionModel(Node node) {
        try (Transaction tx = node.getGraphDatabase().beginTx()) {
            setId(node.getProperty("id").toString());
            if (node.hasProperty("name"))
                setName(node.getProperty("name").toString());
            if (node.hasProperty("language"))
                setLanguage(node.getProperty("language").toString());
            if (node.hasProperty("direction"))
                setDirection(node.getProperty("direction").toString());
            if (node.hasProperty("isPublic"))
                setIsPublic((Boolean) node.getProperty("isPublic"));
            if (node.hasProperty("stemweb_jobid"))
                setStemweb_jobid(Integer.valueOf(node.getProperty("stemweb_jobid").toString()));

            Relationship ownerRel = node.getSingleRelationship(ERelations.OWNS_TRADITION,
                    org.neo4j.graphdb.Direction.INCOMING);
            if( ownerRel != null ) {
                setOwnerId(ownerRel.getStartNode().getProperty("id").toString());
            }

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
    public String getDirection() { return direction == null ? "" : direction.toString(); }
    public void setDirection(String direction) {
        if (!direction.equals(""))
            this.direction = Direction.valueOf(direction);
    }
    public Boolean getIsPublic() { return isPublic; }
    public void setIsPublic(Boolean isPublic) {
        this.isPublic = isPublic;
    }
    public String getOwnerId() {
        return ownerId;
    }
    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }
    public Integer getStemweb_jobid () { return stemweb_jobid; }
    public void setStemweb_jobid (int stemweb_jobid ) { this.stemweb_jobid = stemweb_jobid; }
}
