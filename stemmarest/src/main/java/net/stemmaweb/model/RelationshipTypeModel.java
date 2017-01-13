package net.stemmaweb.model;

/**
 * This model describes the properties of a particular relationship type.
 * The relationship types are child nodes of a tradition; each reading
 * relationship must carry a property "type" that includes a serialization
 * of one of those nodes.
 *
 * @author tla
 */
public class RelationshipTypeModel implements Comparable<RelationshipTypeModel> {

    private String  thename;
    private int     bindlevel;
    private Boolean is_weak;
    private Boolean is_colocation;
    private Boolean is_transitive;
    private Boolean is_generalizable;
    private Boolean use_regular;

    public String name() {
        return thename;
    }

    public void set_name(String aname) {
        this.thename = aname;
    }

    public int bindlevel() {
        return bindlevel;
    }

    public void set_bindlevel(int bindlevel) {
        this.bindlevel = bindlevel;
    }

    public Boolean is_colocation() {
        return is_colocation;
    }

    public void set_colocation(Boolean is_colocation) {
        this.is_colocation = is_colocation;
    }

    public Boolean is_weak() {
        return is_weak;
    }

    public void set_weak(Boolean is_weak) {
        this.is_weak = is_weak;
    }

    public Boolean is_transitive() {
        return is_transitive;
    }

    public void set_transitive(Boolean is_transitive) {
        this.is_transitive = is_transitive;
    }

    public Boolean is_generalizable() {
        return is_generalizable;
    }

    public void set_generalizable(Boolean is_generalizable) {
        this.is_generalizable = is_generalizable;
    }

    public Boolean use_regular() {
        return use_regular;
    }

    public void setUse_regular(Boolean use_regular) {
        this.use_regular = use_regular;
    }

    @Override
    public int compareTo( RelationshipTypeModel o) {
        return bindlevel - o.bindlevel();
    }
}
