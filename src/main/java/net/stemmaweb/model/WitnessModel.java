package net.stemmaweb.model;

import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

/**
 * This model holds a witness. The sigil is also the witness name, e.g. 'Mk10'
 * @author PSE FS 2015 Team2
 */

@XmlRootElement
@JsonInclude(Include.NON_NULL)
public class WitnessModel implements Comparable<WitnessModel> {
    /**
     * The Neo4J node ID of the witness
     */
    private String id;
    /**
     * The sigil of the witness
     */
    private String sigil;

    @SuppressWarnings("unused")     // It's used by response.readEntity(GenericType blah)
    public WitnessModel() {
    }
    /**
     * Generates a model from a Neo4j Node
     * @param node - the witness node to initialize from
     */
    public WitnessModel(Node node) {
        try (Transaction tx = node.getGraphDatabase().beginTx()) {
            id = String.valueOf(node.getId());
            if (node.hasProperty("sigil"))
                sigil = (String) node.getProperty("sigil");
            tx.success();
        }
    }

    public String getId() { return id; }
    public String getSigil() {
        return sigil;
    }
    public void setSigil(String id) {
        this.sigil = id;
    }

    @Override
    public int compareTo(@NonNull WitnessModel wm) {
        return this.sigil.compareTo(wm.sigil);
    }

}
