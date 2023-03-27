package net.stemmaweb.model;

import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.qmino.miredot.annotations.MireDotIgnore;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.services.DatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;

import java.util.ArrayList;
import java.util.Arrays;

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

    // Properties
    /**
     * ID of the tradition
     */
    private String id;
    /**
     * Name of the tradition
     */
    private String name;
    /**
     * Language of the tradition
     */
    private String language;
    /**
     * Direction of the tradition (LR, RL, or BI)
     */
    private Direction direction;
    /**
     * Whether the tradition should be viewable by other users
     */
    private Boolean is_public;
    @MireDotIgnore
    private Integer stemweb_jobid;
    /**
     * User ID of the tradition's owner
     */
    private String owner;

    /**
     * The list of witness sigla belonging to this tradition
     */
    private ArrayList<String> witnesses;

    @MireDotIgnore
    // Derived from relationships
    private ArrayList<String> reltypes;

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
            if (node.hasProperty("is_public"))
                setIs_public((Boolean) node.getProperty("is_public"));
            if (node.hasProperty("stemweb_jobid"))
                setStemweb_jobid(Integer.parseInt(node.getProperty("stemweb_jobid").toString()));

            Relationship ownerRel = node.getSingleRelationship(ERelations.OWNS_TRADITION,
                    org.neo4j.graphdb.Direction.INCOMING);
            if( ownerRel != null ) {
                setOwner(ownerRel.getStartNode().getProperty("id").toString());
            }

            witnesses = new ArrayList<>();
            DatabaseService.getRelated(node, ERelations.HAS_WITNESS).forEach(
                    x -> witnesses.add(x.getProperty("sigil").toString()));
            // For now this is hard-coded
            reltypes = new ArrayList<>(Arrays.asList("grammatical", "spelling", "other", "punctuation",
                    "lexical", "orthographic", "uncertain"));

            tx.close();
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
    public Boolean getIs_public() { return is_public; }
    public void setIs_public(Boolean is_public) {
        this.is_public = is_public;
    }
    public String getOwner() {
        return owner;
    }
    public void setOwner(String owner) {
        this.owner = owner;
    }
    public Integer getStemweb_jobid () { return stemweb_jobid; }
    public void setStemweb_jobid (int stemweb_jobid ) { this.stemweb_jobid = stemweb_jobid; }
    public ArrayList<String> getWitnesses() { return witnesses; }
    @SuppressWarnings("unused")
    public ArrayList<String> getReltypes() { return reltypes; }
    @SuppressWarnings("unused")
    public void setReltypes(ArrayList<String> reltypes) { this.reltypes = reltypes; }

}
