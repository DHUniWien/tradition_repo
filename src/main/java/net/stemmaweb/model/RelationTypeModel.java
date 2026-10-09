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
import net.stemmaweb.services.NameConflictException;

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
     * The internal (application-managed, numeric) ID of the relation type.
     */
    private String id;
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
        // No name by default, so that a request body which omits the name can be told apart
        // from one that supplies it (see RelationType.create and update() below).
        this((String) null);
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
        if (n.hasProperty("id"))
            this.setId(n.getProperty("id").toString());
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

    public String getId() {
        return id;
    }

    private void setId(String id) {
        this.id = id;
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
     * Create the Neo4J node corresponding to this relation type model. The caller is
     * responsible for having already established (e.g. via a dual-addressing resolve
     * against the URL reference) that no existing node is being renamed/updated here --
     * this always creates a brand-new node.
     *
     * @param traditionNode - The tradition to which this model belongs
     * @return the newly-created RelationType node
     * @throws NameConflictException if a relation type with this name already exists on the tradition
     * @throws IllegalArgumentException if this model has no name, its name is numeric,
     *         or the display string fails JSON validation
     */
    public Node instantiate (Node traditionNode, Transaction tx) throws Exception {
        if (this.thename == null)
            throw new IllegalArgumentException("Relation type name is required");
        Node conflict = this.lookup(traditionNode);
        if (conflict != null)
            throw new NameConflictException(
                    String.format("A relation type with name '%s' already exists", this.thename));
        if (DatabaseService.nameIsNumeric(this.thename))
            throw new IllegalArgumentException("Relation type name may not be numeric: " + this.thename);
        Node relType = DatabaseService.createNode(tx, Nodes.RELATION_TYPE);
        traditionNode.createRelationshipTo(relType, ERelations.HAS_RELATION_TYPE);
        this.update_reltype(relType);
        return relType;
    }

    /**
     * Update the Neo4J node corresponding to this relation type model -- that is, the node
     * resolved by the caller from the URL reference, not whatever node (if any) happens to
     * share this model's {@code name}. Always writes this model's values onto {@code existingNode},
     * including a rename if {@code this.thename} differs from its current name. If this model
     * has no name (e.g. the request body omitted it), the node's current name is kept.
     *
     * @param traditionNode - The tradition to which this model belongs
     * @param existingNode - The already-resolved RelationType node to update
     * @return the updated RelationType node (same as existingNode)
     * @throws NameConflictException if this model's name is already used by a *different*
     *         relation type on the tradition
     * @throws IllegalArgumentException if this model's name is numeric, or the display
     *         string fails JSON validation
     */
    public Node update (Node traditionNode, Node existingNode, Transaction tx) throws Exception {
        String currentName = existingNode.getProperty("name").toString();
        if (this.thename == null)
            this.thename = currentName;
        if (!this.thename.equals(currentName)) {
            Node conflict = this.lookup(traditionNode);
            if (conflict != null && !conflict.equals(existingNode))
                throw new NameConflictException(
                        String.format("A relation type with name '%s' already exists", this.thename));
        }
        if (DatabaseService.nameIsNumeric(this.thename))
            throw new IllegalArgumentException("Relation type name may not be numeric: " + this.thename);
        this.update_reltype(existingNode);
        return existingNode;
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
