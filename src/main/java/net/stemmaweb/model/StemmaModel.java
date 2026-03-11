package net.stemmaweb.model;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.swagger.v3.oas.annotations.media.Schema;
import net.stemmaweb.exporter.DotExporter;
import net.stemmaweb.rest.ERelations;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

import javax.ws.rs.core.Response;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * A model for the stemma object and its representation.
 */
@XmlRootElement
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StemmaModel {

    /**
     * The unique numerical identifier of the stemma within the database.
     */
    @Schema(description = "The unique numerical identifier of the stemma within the database.")
    private Long stemmaid;
    /**
     * The name of the stemma. Need not be unique within a tradition.
     */
    @Schema(description = "The name of the stemma. Need not be unique within a tradition.")
    private String name;
    /**
     * True if this is an undirected tree, rather than a directed stemma.
     */
    @Schema(description = "True if this is an undirected tree, rather than a directed stemma.")
    private Boolean is_undirected;
    /**
     * True if the stemma indicates witness contamination / conflation.
     */
    @Schema(description = "True if the stemma indicates witness contamination / conflation.")
    private Boolean is_contaminated;
    @Schema(hidden = true)
    private Integer from_jobid;
    /**
     * A string that holds the dot specification of the stemma or tree topology.
     */
    @Schema(description = "A string that holds the dot specification of the stemma or tree topology.")
    private String dot;
    /**
     * A string that holds the Newick specification of the tree topology.
     */
    @Schema(description = "A string that holds the Newick specification of the tree topology.")
    private String newick;

    public StemmaModel () {}

    public StemmaModel(Node stemmaNode) {
        GraphDatabaseService db = stemmaNode.getGraphDatabase();
        try (Transaction tx = db.beginTx()) {
            stemmaid = stemmaNode.getId();
            name = stemmaNode.getProperty("name").toString();
            is_undirected = !stemmaNode.hasRelationship(ERelations.HAS_ARCHETYPE);
            is_contaminated = stemmaNode.hasProperty("is_contaminated");
            if (stemmaNode.hasProperty("from_jobid"))
                from_jobid = (Integer) stemmaNode.getProperty("from_jobid");

            // Generate the dot as well.
            DotExporter writer = new DotExporter(db);
            try (Response export = writer.writeNeo4JStemma(stemmaid, false)) {
                dot = export.getEntity().toString();
            }
            tx.success();
        }
    }

    public Long getStemmaid() { return this.stemmaid; }
    public void setStemmaid(Long stemmaid) { this.stemmaid = stemmaid; }

    public String getName() { return this.name; }
    public void setName(String name) { this.name = name; }

    public String getDot () { return this.dot; }
    public void setDot(String dot) { this.dot = dot; }

    @JsonGetter("from_jobid")
    public Integer getJobid () { return this.from_jobid; }
    public Boolean cameFromJobid() { return this.from_jobid != null && this.from_jobid != 0; }
    @JsonSetter("from_jobid")
    public void setJobid(Integer jobid) { this.from_jobid = jobid; }

    public String getNewick() { return this.newick; }
    public void setNewick(String n) { this.newick = n; }

    /* Read-only accessors */
    public Boolean getIs_undirected () { return this.is_undirected; }
    public Boolean getIs_contaminated () { return this.is_contaminated; }

}
