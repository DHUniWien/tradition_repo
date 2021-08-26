package net.stemmaweb.model;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.qmino.miredot.annotations.MireDotIgnore;
import net.stemmaweb.exporter.DotExporter;
import net.stemmaweb.rest.ERelations;
import org.neo4j.graphdb.Direction;
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
     * The name (identifier) of the stemma. Must be unique within a tradition.
     */
    private String identifier;
    /**
     * True if this is an undirected tree, rather than a directed stemma.
     */
    private Boolean is_undirected;
    /**
     * True if the stemma indicates witness contamination / conflation.
     */
    private Boolean is_contaminated;
    @MireDotIgnore
    private Integer from_jobid;
    /**
     * A string that holds the dot specification of the stemma or tree topology.
     */
    private String dot;
    /**
     * A string that holds the Newick specification of the tree topology.
     */
    private String newick;

    public StemmaModel () {}

    public StemmaModel(Node stemmaNode) {
        GraphDatabaseService db = stemmaNode.getGraphDatabase();
        try (Transaction tx = db.beginTx()) {
            identifier = stemmaNode.getProperty("name").toString();
            is_undirected = !stemmaNode.hasRelationship(ERelations.HAS_ARCHETYPE);
            is_contaminated = stemmaNode.hasProperty("is_contaminated");
            if (stemmaNode.hasProperty("from_jobid"))
                from_jobid = (Integer) stemmaNode.getProperty("from_jobid");

            // Generate the dot as well.
            Node traditionNode = stemmaNode.getSingleRelationship(ERelations.HAS_STEMMA, Direction.INCOMING).getStartNode();
            DotExporter writer = new DotExporter(db);
            Response export = writer.writeNeo4JStemma(traditionNode.getProperty("id").toString(), identifier, false);
            dot = export.getEntity().toString();
            tx.success();
        }
    }

    public String getIdentifier () { return this.identifier; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }

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
