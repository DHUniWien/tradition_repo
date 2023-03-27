package net.stemmaweb.model;

import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import net.stemmaweb.rest.ERelations;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;

/**
 * This model holds a witness. The sigil is also the witness name, e.g. 'Mk10'
 * @author PSE FS 2015 Team2
 */

@XmlRootElement
@JsonInclude(Include.NON_NULL)
public class SectionModel {
    /**
     * The internal ID of the section
     */
    private String id;
    /**
     * The name of the section
     */
    private String name;
    /**
     * The language of the section's text
     */
    private String language;
    /**
     * The graph rank of the section's end node. This is a rough indication of the length of the section.
     */
    private Long endRank;

    public SectionModel() {}

    /**
     * Generates a model from a Neo4j Node
     * @param node - the section node to initialize from
     */
    public SectionModel(Node node) {
        try (Transaction tx = node.getGraphDatabase().beginTx()) {
            setId(node.getElementId());
            if (node.hasProperty("name"))
                setName(node.getProperty("name").toString());
            // If this node has a language set, use it; otherwise fall back to the tradition language.
            if (node.hasProperty("language"))
                setLanguage(node.getProperty("language").toString());
            else {
                Node tn = node.getSingleRelationship(ERelations.PART, Direction.INCOMING).getStartNode();
                if (tn.hasProperty("language"))
                    setLanguage(tn.getProperty("language").toString());
            }
            Relationship sectionEnd = node.getSingleRelationship(ERelations.HAS_END, Direction.OUTGOING);
            setEndRank(Long.valueOf(sectionEnd.getEndNode().getProperty("rank").toString()));

            tx.close();
        }
    }

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getLanguage() {
        return language;
    }
    public void setLanguage(String language) {
        this.language = language;
    }
    public Long getEndRank() {
        return endRank;
    }
    private void setEndRank(Long rank) {
        this.endRank = rank;
    }
}
