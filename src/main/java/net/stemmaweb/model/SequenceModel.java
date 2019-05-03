package net.stemmaweb.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.neo4j.graphdb.Relationship;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.*;

/**
 * Provides a model for a reading sequence link outside of the database. Can be
 * parsed into a json object.
 *
 * @author tla
 */

@XmlRootElement
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SequenceModel {
    /**
     * The relationship type of this sequence: SEQUENCE, LEMMA_TEXT, or EMENDED
     */
    private String type;
    /**
     * The ID of the first reading in the relationship
     */
    private String source;
    /**
     * The ID of the second reading in the relationship
     */
    private String target;
    /**
     * The internal database ID of this relationship
     */
    private String id;
    /**
     * The list of "main" witnesses along this sequence route
     */
    private List<String> witnesses;
    /**
     * Any layer witnesses that travel along this sequence route
     */
    private Map<String, List<String>> layers;

    @SuppressWarnings("WeakerAccess")
    public SequenceModel() {
        super();
        this.witnesses = new ArrayList<>();
    }
    /**
     * Generates a model from a Neo4j Relationship
     * @param rel - the sequence relationship to initialize from
     */
    public SequenceModel(Relationship rel) {
        this();
        type = rel.getType().toString();
        source = rel.getStartNode().getId() + "";
        target = rel.getEndNode().getId() + "";
        id = Long.toString(rel.getId());

        for (String p : rel.getPropertyKeys()) {
            if (p.equals("witnesses"))
                setWitnesses(Arrays.asList((String[]) rel.getProperty("witnesses")));
            else {
                if (layers == null)
                    setLayers(new HashMap<>());
                layers.put(p, Arrays.asList((String[]) rel.getProperty(p)));
            }
        }
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<String> getWitnesses() {
        return witnesses;
    }

    public void setWitnesses(List<String> witnesses) {
        this.witnesses = witnesses;
    }

    public Map<String, List<String>> getLayers() {
        return layers;
    }

    public void setLayers(Map<String, List<String>> layers) {
        this.layers = layers;
    }

}
