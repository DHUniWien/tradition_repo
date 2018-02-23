package net.stemmaweb.model;

import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import org.neo4j.graphdb.Node;

/**
 * This model holds a witness. The sigil is also the witness name, e.g. 'Mk10'
 * @author PSE FS 2015 Team2
 */

@XmlRootElement
@JsonInclude(Include.NON_NULL)
public class WitnessModel {
    /**
     * The sigil of the witness
     */
    private String sigil;

    @SuppressWarnings("unused")     // It's used by response.getEntity(GenericType blah)
    public WitnessModel() {
    }
    /**
     * Generates a model from a Neo4j Node
     * @param node - the witness node to initialize from
     */
    public WitnessModel(Node node) {
        if (node.hasProperty("sigil"))
            sigil = (String) node.getProperty("sigil");
    }

    public String getSigil() {
        return sigil;
    }
    public void setSigil(String id) {
        this.sigil = id;
    }
}
