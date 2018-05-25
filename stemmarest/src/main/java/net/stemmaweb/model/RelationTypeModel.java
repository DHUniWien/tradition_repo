package net.stemmaweb.model;

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import org.neo4j.graphdb.*;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * This model describes the properties of a particular relationship type.
 * The relationship types are child nodes of a tradition; each reading
 * relationship must carry a property "type" that includes a serialization
 * of one of those nodes.
 *
 * @author tla
 */

@XmlRootElement
public class RelationTypeModel implements Comparable<RelationTypeModel> {

    /**
     * The name of the relationship type (e.g. "grammatical")
     */
    private String  thename;
    /**
     * A short description of what this relationship type signifies
     */
    private String  description;
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
        this.description = "A type of reading relation";
        this.bindlevel = 10;
        this.is_colocation = true;
        this.is_weak = false;
        this.is_transitive = false;
        this.is_generalizable = true;
        this.use_regular = true;
    }

    public RelationTypeModel (Node n) {
        try (Transaction tx = n.getGraphDatabase().beginTx()) {
            if (n.hasProperty("name"))
                this.setName(n.getProperty("name").toString());
            if (n.hasProperty("description"))
                this.setDescription(n.getProperty("description").toString());
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
            tx.success();
        }
    }

    public String getName() {
        return thename;
    }

    public void setName(String aname) {
        this.thename = aname;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

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
     */
    public Node instantiate (Node traditionNode) {
        return match_relation_node(traditionNode, false);
    }

    /**
     * Update the Neo4J node corresponding to this relation type model.
     * @param traditionNode - The tradition to which this model belongs
     */
    public Node update (Node traditionNode) {
        return match_relation_node(traditionNode, true);
    }

    /**
     * Look up and return the Neo4J node with the given relation type name.
     * @param traditionNode - The tradition on which to perform the lookup
     * @return - The correspondingly named RELATION_TYPE node, or null
     */
    public Node lookup (Node traditionNode) {
        GraphDatabaseService db = traditionNode.getGraphDatabase();
        Node relTypeNode = null;
        try (Transaction tx = db.beginTx()) {
            // First see if there is a type with this name
            for (Relationship r : traditionNode.getRelationships(ERelations.HAS_RELATION_TYPE, Direction.OUTGOING)) {
                if (r.getEndNode().getProperty("name").toString().equals(this.thename)) {
                    relTypeNode = r.getEndNode();
                    break;
                }
            }
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return relTypeNode;
    }

    private Node match_relation_node(Node traditionNode, Boolean allow_update) {
        GraphDatabaseService db = traditionNode.getGraphDatabase();
        Node relType = this.lookup(traditionNode);
        try (Transaction tx = db.beginTx()) {
            if (relType == null) {
                // Create the node if it doesn't exist
                relType = db.createNode(Nodes.RELATION_TYPE);
                this.update_reltype(relType);
                traditionNode.createRelationshipTo(relType, ERelations.HAS_RELATION_TYPE);
            } else {
                // Check that the node matches our values, if it does exist
                if (!(this.description.equals(relType.getProperty("description"))
                        && this.bindlevel == (int) relType.getProperty("bindlevel")
                        && this.is_colocation == relType.getProperty("is_colocation")
                        && this.is_weak == relType.getProperty("is_weak")
                        && this.is_transitive == relType.getProperty("is_transitive")
                        && this.is_generalizable == relType.getProperty("is_generalizable")
                        && this.use_regular == relType.getProperty("use_regular"))) {
                    if (allow_update) this.update_reltype(relType);
                    else throw new Exception("Another relation type by this name already exists");
                }
            }
            tx.success();
            return relType;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // To be used inside a transaction!
    private void update_reltype (Node relType) {
        relType.setProperty("name", this.getName());
        relType.setProperty("description", this.getDescription());
        relType.setProperty("bindlevel", this.getBindlevel());
        relType.setProperty("is_colocation", this.getIs_colocation());
        relType.setProperty("is_weak", this.getIs_weak());
        relType.setProperty("is_transitive", this.getIs_transitive());
        relType.setProperty("is_generalizable", this.getIs_generalizable());
        relType.setProperty("use_regular", this.getUse_regular());
    }

    @Override
    public int compareTo( RelationTypeModel o) {
        return bindlevel - o.getBindlevel();
    }
}
