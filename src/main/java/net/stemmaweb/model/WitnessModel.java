package net.stemmaweb.model;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.neo4j.graphdb.Node;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * This model holds a witness. The sigil is also the witness name, e.g. 'Mk10'
 * @author PSE FS 2015 Team2
 */

@XmlRootElement
@JsonInclude(Include.NON_NULL)
public class WitnessModel implements Comparable<WitnessModel> {
    /**
     * The internal (application-managed, numeric) ID of the witness
     */
    private String id;
    /**
     * The sigil of the witness
     */
    private String sigil;
    /**
     * The settlement (city, town etc.) where the witness is held
     */
    private String settlement;
    /**
     * The repository (library, archive etc.) that holds the witness
     */
    private String repository;
    /**
     * The shelfmark or other identifier of the witness within its repository. (The TEI guidelines
     * call this idno, but it is not to be confused with the numeric ID of the witness.)
     */
    private String shelfmark;
    /**
     * A free-text description of the witness
     */
    private String description;
    /**
     * The date on which the witness was produced
     */
    private String productionDate;
    /**
     * The place where the witness was produced, which is not necessarily the settlement where it is held
     */
    private String productionPlace;
    /**
     * The person who produced the witness
     */
    private String productionPerson;

    // The names of the witness node properties that hold the descriptive metadata
    private static final String[] METADATA_PROPERTIES = {"settlement", "repository", "shelfmark", "description",
            "production_date", "production_place", "production_person"};

    @SuppressWarnings("unused")     // It's used by response.readEntity(GenericType blah)
    public WitnessModel() {
    }
    /**
     * Generates a model from a Neo4j Node
     * @param node - the witness node to initialize from
     */
    public WitnessModel(Node node) {
        id = node.getProperty("id").toString();
        if (node.hasProperty("sigil"))
            sigil = (String) node.getProperty("sigil");
        settlement = (String) node.getProperty("settlement", null);
        repository = (String) node.getProperty("repository", null);
        shelfmark = (String) node.getProperty("shelfmark", null);
        description = (String) node.getProperty("description", null);
        productionDate = (String) node.getProperty("production_date", null);
        productionPlace = (String) node.getProperty("production_place", null);
        productionPerson = (String) node.getProperty("production_person", null);
    }

    /**
     * Applies the descriptive metadata in this model to a witness node. A field that is null is left
     * unchanged on the node; a field that is the empty string is removed from it. The sigil is not
     * touched.
     * @param node - the witness node to update; must be in a transaction
     */
    public void applyMetadataTo(Node node) {
        String[] values = {settlement, repository, shelfmark, description,
                productionDate, productionPlace, productionPerson};
        for (int i = 0; i < METADATA_PROPERTIES.length; i++) {
            if (values[i] == null) continue;
            if (values[i].isEmpty())
                node.removeProperty(METADATA_PROPERTIES[i]);
            else
                node.setProperty(METADATA_PROPERTIES[i], values[i]);
        }
    }

    public String getId() { return id; }
    public String getSigil() {
        return sigil;
    }
    public void setSigil(String id) {
        this.sigil = id;
    }

    public String getSettlement() { return settlement; }
    public void setSettlement(String settlement) { this.settlement = settlement; }
    public String getRepository() { return repository; }
    public void setRepository(String repository) { this.repository = repository; }
    public String getShelfmark() { return shelfmark; }
    public void setShelfmark(String shelfmark) { this.shelfmark = shelfmark; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    @JsonProperty("production_date")
    public String getProductionDate() { return productionDate; }
    @JsonProperty("production_date")
    public void setProductionDate(String productionDate) { this.productionDate = productionDate; }
    @JsonProperty("production_place")
    public String getProductionPlace() { return productionPlace; }
    @JsonProperty("production_place")
    public void setProductionPlace(String productionPlace) { this.productionPlace = productionPlace; }
    @JsonProperty("production_person")
    public String getProductionPerson() { return productionPerson; }
    @JsonProperty("production_person")
    public void setProductionPerson(String productionPerson) { this.productionPerson = productionPerson; }

    @Override
    public int compareTo(@NonNull WitnessModel wm) {
        return this.sigil.compareTo(wm.sigil);
    }

}
