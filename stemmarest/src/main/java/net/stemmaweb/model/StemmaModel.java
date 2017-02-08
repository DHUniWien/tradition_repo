package net.stemmaweb.model;

import com.fasterxml.jackson.annotation.JsonInclude;
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

    private String identifier;
    private Boolean is_undirected;
    private Boolean is_contaminated;
    private Long from_jobid;
    private String dot;

    public StemmaModel () {

    }

    public StemmaModel(Node stemmaNode) {
        GraphDatabaseService db = stemmaNode.getGraphDatabase();
        try (Transaction tx = db.beginTx()) {
            identifier = stemmaNode.getProperty("name").toString();
            is_undirected = !stemmaNode.hasRelationship(ERelations.HAS_ARCHETYPE);
            is_contaminated = stemmaNode.hasProperty("is_contaminated");
            if (stemmaNode.hasProperty("from_jobid"))
                from_jobid = (Long) stemmaNode.getProperty("from_jobid");

            // Generate the dot as well.
            Node traditionNode = stemmaNode.getSingleRelationship(ERelations.HAS_STEMMA, Direction.INCOMING).getStartNode();
            DotExporter writer = new DotExporter(db);
            Response export = writer.parseNeo4JStemma(traditionNode.getProperty("id").toString(), identifier, false);
            dot = export.getEntity().toString();
            tx.success();
        }
    }

    // This should be read-only; we shouldn't need to construct a stemma model for a query. So far.
    public String getIdentifier () { return this.identifier; }
    public String getDot () { return this.dot; }
    public Long getFrom_jobid () { return this.from_jobid; }
    public Boolean getIs_undirected () { return this.is_undirected; }
    public Boolean getIs_contaminated () { return this.is_contaminated; }

}
