package net.stemmaweb.model;

import javax.xml.bind.annotation.XmlRootElement;

import org.neo4j.graphdb.Relationship;

/**
 * Provides a model for a relationship outside of the database. Can be parsed
 * into a json object.
 * 
 * @author PSE FS 2015 Team2
 */

@XmlRootElement
public class RelationModel {

    @SuppressWarnings("unused")
    private enum Significance {
        no,
        maybe,
        yes
    }

    /**
     * The ID of the first reading in the relationship
     */
    private String source;              // source
    /**
     * The ID of the second reading in the relationship
     */
    private String target;              // target
    /**
     * The internal database ID of this relationship
     */
    private String id;                  // id
    /**
     * True if the source reading could be guessed by a scribe who saw the target reading in an exemplar
     */
    private Boolean a_derivable_from_b = false;  // de0
    /**
     * A numeric value to designate the impact that this variation has on the meaning of the text
     */
    private Long alters_meaning = 0L;      // de1
    /**
     * User supplied annotation or comment on the relatinoship
     */
    private String annotation;          // de2
    /**
     * True if the target reading could be guessed by a scribe who saw the source reading in an exemplar
     */
    private Boolean b_derivable_from_a = false;  // de3
    /**
     * Gosh I don't remember
     */
    private String displayform;         // de4
    /**
     * I don't remember this either
     */
    private String extra;               // de5
    /**
     * True if the editor believes this variation has stemmatic / genealogical significance
     */
    private Significance is_significant = Significance.no; // de6
    /**
     * True if this variation is unlikely to have arisen in two branches of the stemma coincidentally
     */
    private Boolean non_independent = false;     // de7
    /**
     * The extent to which this relationship should be applied more widely. Valid values are {@code local} and {@code document}.
     */
    private String scope;               // de10
    /**
     * The type of relationship (e.g. {@code spelling}, {@code transposition}, {@code grammatical}
     */
    private String type;                // de11

    public RelationModel(){

    }

    /**
     * Creates a relationshipModel directly from a Relationship from Neo4J db
     * @param rel - The relationship node to initialize from
     */
    public RelationModel(Relationship rel){
        source = rel.getStartNode().getId() + "";
        target = rel.getEndNode().getId() + "";

        Iterable<String> properties = rel.getPropertyKeys();
        id = Long.toString(rel.getId());
        for (String property : properties) {
            switch (property) {
                case "a_derivable_from_b":
                    a_derivable_from_b = (Boolean) rel.getProperty("a_derivable_from_b");
                    break;
                case "alters_meaning":
                    alters_meaning = 0L;
                    if (rel.getProperty("alters_meaning") != null)
                        alters_meaning = (Long) rel.getProperty("alters_meaning");
                    break;
                case "annotation":
                    annotation = rel.getProperty("annotation").toString();
                    break;
                case "b_derivable_from_a":
                    b_derivable_from_a = (Boolean) rel.getProperty("b_derivable_from_a");
                    break;
                case "displayform":
                    displayform = rel.getProperty("displayform").toString();
                    break;
                case "extra":
                    extra = rel.getProperty("extra").toString();
                    break;
                case "is_significant":
                    is_significant = Significance.valueOf(rel.getProperty("is_significant").toString());
                    break;
                case "non_independent":
                    non_independent = (Boolean) rel.getProperty("non_independent");
                    break;
                case "scope":
                    scope = rel.getProperty("scope").toString();
                    break;
                case "type":
                    type = rel.getProperty("type").toString();
                    break;
                default:
                    break;
            }
        }
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

    public Boolean getA_derivable_from_b() {
        return a_derivable_from_b;
    }

    public void setA_derivable_from_b(Boolean a_derivable_from_b) {
        this.a_derivable_from_b = a_derivable_from_b;
    }

    public Long getAlters_meaning() {
        return alters_meaning;
    }

    public void setAlters_meaning(Long alters_meaning) {
        this.alters_meaning = alters_meaning;
    }

    public String getAnnotation() {
        return annotation;
    }

    public void setAnnotation(String annotation) {
        this.annotation = annotation;
    }

    public Boolean getB_derivable_from_a() {
        return b_derivable_from_a;
    }

    public void setB_derivable_from_a(Boolean b_derivable_from_a) {
        this.b_derivable_from_a = b_derivable_from_a;
    }

    public String getDisplayform() {
        return displayform;
    }

    public void setDisplayform(String displayform) {
        this.displayform = displayform;
    }

    public String getExtra() {
        return extra;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }

    public String getIs_significant() {
        return is_significant == null ? "" : is_significant.toString();
    }

    public void setIs_significant(String is_significant) {
        if(!is_significant.equals(""))
            this.is_significant = Significance.valueOf(is_significant);
    }

    public Boolean getNon_independent() {
        return non_independent;
    }

    public void setNon_independent(Boolean non_independent) {
        this.non_independent = non_independent;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

}
