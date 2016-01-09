package net.stemmaweb.model;

import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import net.stemmaweb.rest.ERelations;
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
    private String id;
    private String name;
    private String language;
    private String baselabel;
    private String sep_char;
    private Boolean is_public;

    public SectionModel() {
        /**
         * Generates a model from a Neo4j Node
         * @param node - the section node to initialize from
         */
    }

    public SectionModel(Node node) {
        try (Transaction tx = node.getGraphDatabase().beginTx()) {
            setId(node.getProperty("id").toString());
            if (node.hasProperty("name"))
                setName(node.getProperty("name").toString());
            if (node.hasProperty("language"))
                setLanguage(node.getProperty("language").toString());
            if (node.hasProperty("baselabel"))
                setBaselabel(node.getProperty("baselabel").toString());
            if (node.hasProperty("sep_char"))
                setSepChar(node.getProperty("sep_char").toString());
            if (node.hasProperty("public"))
                setIsPublic((Boolean) node.getProperty("public"));

/*            Relationship ownerRel = node.getSingleRelationship(ERelations.OWNS_TRADITION,
                    org.neo4j.graphdb.Direction.INCOMING);
            if( ownerRel != null ) {
                setOwnerId(ownerRel.getStartNode().getProperty("id").toString());
            }
*/
            tx.success();
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
    public String getBaselabel() {
        return baselabel;
    }
    public void setBaselabel(String baselabel) {
        this.baselabel = baselabel;
    }
    public String getSepChar() { return sep_char; }
    public void setSepChar(String sep_char) { this.sep_char = sep_char; }
    public Boolean getIsPublic() { return is_public; }
    public void setIsPublic(Boolean is_public) {
        this.is_public = is_public;
    }
}
