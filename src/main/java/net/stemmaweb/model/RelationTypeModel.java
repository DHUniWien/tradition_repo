package net.stemmaweb.model;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.json.JSONException;
import org.json.JSONObject;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.xml.bind.annotation.XmlRootElement;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.services.DatabaseService;

/**
 * This model describes the properties of a particular relationship type.
 * The relationship types are child nodes of a tradition; each reading
 * relationship must carry a property "type" that includes a serialization
 * of one of those nodes.
 *
 * @author tla
 */

@XmlRootElement
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RelationTypeModel implements Comparable<RelationTypeModel> {

    /**
     * The name of the relationship type (e.g. "grammatical")
     */
    private String  thename;
    private Boolean defaultsettings; // undocumented; use this for Stemmaweb legacy defaults
    /**
     * A short description of what this relationship type signifies
     */
    private String  description;
    /**
     * A JSON-formatted string field made available for client applications to specify display behaviour
     * for the relation type. This should be of the format
     * {@code {"com.example.myapp": {"color": "blue", "width", "3px"}, "com.example.yourapp": {"lang": "fr"}, ...}}
     * where the key is a namespaced string indicating the application, and the value is whatever JSON object
     * that application would expect.
     */
    private String  display;
    /**
     * How tightly the relationship binds. A lower number indicates a closer binding.
     * If A and B are related at bindlevel 0, and B and C at bindlevel 1, it implies
     * that A and C have the same relationship as B and C do.
     */
    private int     bindlevel;
    /**
     * Whether this relationship should be replaced silently by a stronger type if
     * requested. This is used primarily for the internal 'collated' relationship, only
     * to be used by parsers.
     */
    private Boolean is_weak;
    /**
     * Whether this relationship implies that the readings in question occur in the
     * same "place" in the text.
     */
    private Boolean is_colocation;
    /**
     * Whether this relationship type is transitive - that is, if A is related to B and C
     * via this type, is B also related to C via the same type?
     */
    private Boolean is_transitive;
    /**
     * Whether this relationship can have a non-local scope.
     */
    private Boolean is_generalizable;
    /**
     * Whether, when a relationship has a non-local scope, the search for other relatable
     * pairs should be made on the regularized form of the reading.
     */
    private Boolean use_regular;

    public RelationTypeModel () {
        this("noname");
    }

    public RelationTypeModel (String name) {
        this.thename = name;
        // Set some defaults
        // this.defaultsettings = false;
        this.description = "A type of reading relation";
        this.display = "{}";
        this.bindlevel = 10;
        this.is_colocation = true;
        this.is_weak = false;
        this.is_transitive = false;
        this.is_generalizable = true;
        this.use_regular = true;
    }

    public RelationTypeModel (Node n) {
        this();
        if (n.hasProperty("name"))
        	this.setName(n.getProperty("name").toString());
        if (n.hasProperty("description"))
        	this.setDescription(n.getProperty("description").toString());
        if (n.hasProperty("display"))
        	this.setDisplay(n.getProperty("display").toString());
        if (n.hasProperty("bindlevel"))
        	this.setBindlevel((int) n.getProperty("bindlevel"));
        if (n.hasProperty("is_colocation"))
        	this.setIs_colocation((Boolean) n.getProperty("is_colocation"));
        if (n.hasProperty("is_weak"))
        	this.setIs_weak((Boolean) n.getProperty("is_weak"));
        if (n.hasProperty("is_transitive"))
        	this.setIs_transitive((Boolean) n.getProperty("is_transitive"));
        if (n.hasProperty("is_generalizable"))
        	this.setIs_generalizable((Boolean) n.getProperty("is_generalizable"));
        if (n.hasProperty("use_regular"))
        	this.setUse_regular((Boolean) n.getProperty("use_regular"));
    }

    public String getName() {
        return thename;
    }

    public void setName(String aname) {
        this.thename = aname;
    }

    public Boolean getDefaultsettings() { return defaultsettings; }

    public void setDefaultsettings(Boolean defaultsettings) { this.defaultsettings = defaultsettings; }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDisplay() { return display; }

    public void setDisplay(String display) { this.display = display; }

    public int getBindlevel() {
        return bindlevel;
    }

    public void setBindlevel(int bindlevel) {
        this.bindlevel = bindlevel;
    }

    public Boolean getIs_colocation() {
        return is_colocation;
    }

    public void setIs_colocation(Boolean is_colocation) {
        this.is_colocation = is_colocation;
    }

    public Boolean getIs_weak() {
        return is_weak;
    }

    public void setIs_weak(Boolean is_weak) {
        this.is_weak = is_weak;
    }

    public Boolean getIs_transitive() {
        return is_transitive;
    }

    public void setIs_transitive(Boolean is_transitive) {
        this.is_transitive = is_transitive;
    }

    public Boolean getIs_generalizable() {
        return is_generalizable;
    }

    public void setIs_generalizable(Boolean is_generalizable) {
        this.is_generalizable = is_generalizable;
    }

    public Boolean getUse_regular() {
        return use_regular;
    }

    public void setUse_regular(Boolean use_regular) {
        this.use_regular = use_regular;
    }

    /**
     * Create the Neo4J node corresponding to this relation type model.
     * @param traditionNode - The tradition to which this model belongs
     * @return the created RelationType node
     */
    public Node instantiate (Node traditionNode, Transaction tx) throws Exception {
        return match_relation_node(traditionNode, false, tx);
    }

    /**
     * Update the Neo4J node corresponding to this relation type model.
     * @param traditionNode - The tradition to which this model belongs
     * @return the updated RelationType node
     */
    public Node update (Node traditionNode, Transaction tx) throws Exception {
        return match_relation_node(traditionNode, true, tx);
    }

    /**
     * Look up and return the Neo4J node with the given relation type name.
     * @param traditionNode - The tradition on which to perform the lookup
     * @return - The correspondingly named RELATION_TYPE node, or null
     */
    public Node lookup (Node traditionNode) {
        Node relTypeNode = null;

    	// First see if there is a type with this name
        for (Relationship r : DatabaseService.getRelationships(traditionNode, Direction.OUTGOING, ERelations.HAS_RELATION_TYPE)) {
            if (r.getEndNode().getProperty("name").toString().equals(this.thename)) {
                relTypeNode = r.getEndNode();
                break;
            }
        }

        return relTypeNode;
    }

    private Node match_relation_node(Node traditionNode, Boolean allow_update, Transaction tx)
            throws IllegalArgumentException {
    	Node relType = this.lookup(traditionNode);
        if (relType == null) {
            // Create the node if it doesn't exist
            relType = tx.createNode(Nodes.RELATION_TYPE);
            this.update_reltype(relType);
            traditionNode.createRelationshipTo(relType, ERelations.HAS_RELATION_TYPE);
        } else {
            // Check that the node matches our values, if it does exist
            if (!(this.description.equals(relType.getProperty("description"))
                    && this.display.equals(relType.getProperty("display"))
                    && this.bindlevel == (int) relType.getProperty("bindlevel")
                    && this.is_colocation == relType.getProperty("is_colocation")
                    && this.is_weak == relType.getProperty("is_weak")
                    && this.is_transitive == relType.getProperty("is_transitive")
                    && this.is_generalizable == relType.getProperty("is_generalizable")
                    && this.use_regular == relType.getProperty("use_regular"))) {
                if (allow_update) this.update_reltype(relType);
                else throw new IllegalArgumentException("Another relation type by this name already exists");
            }
        }
        return relType;
    }

    private void update_reltype (Node relType) throws IllegalArgumentException {
        relType.setProperty("name", this.getName());
        relType.setProperty("description", this.getDescription());
        // Sanity check the "display" property
        try {
            new JSONObject(this.getDisplay());

        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid display string '" + this.getDisplay() + "': " + e.getMessage());
        }
        relType.setProperty("display", this.getDisplay());
        relType.setProperty("bindlevel", this.getBindlevel());
        relType.setProperty("is_colocation", this.getIs_colocation());
        relType.setProperty("is_weak", this.getIs_weak());
        relType.setProperty("is_transitive", this.getIs_transitive());
        relType.setProperty("is_generalizable", this.getIs_generalizable());
        relType.setProperty("use_regular", this.getUse_regular());
    }

    @Override
    public int compareTo(@NonNull RelationTypeModel o) {
        return bindlevel - o.getBindlevel();
    }
}
