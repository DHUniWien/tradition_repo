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
public class RelationshipModel {

    @SuppressWarnings("unused")
    private enum Significance {
        no,
        maybe,
        yes
    }

    private String source;              // source
    private String target;              // target
    private String id;                  // id
    private Boolean a_derivable_from_b = false;  // de0
    private Long alters_meaning = 0L;      // de1
    private String annotation;          // de2
    private Boolean b_derivable_from_a = false;  // de3
    private String displayform;         // de4
    private String extra;               // de5
    private Significance is_significant = Significance.no; // de6
    private Boolean non_independent = false;     // de7
    private String reading_a;           // de8
    private String reading_b;           // de9
    private String scope;               // de10
    private String type;                // de11

    public RelationshipModel(){

    }

    /**
     * Creates a relationshipModel directly from a Relationship from Neo4J db
     * @param rel - The relationship node to initialize from
     */
    public RelationshipModel(Relationship rel){
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
                case "reading_a":
                    reading_a = rel.getProperty("reading_a").toString();
                    break;
                case "reading_b":
                    reading_b = rel.getProperty("reading_b").toString();
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

    public String getReading_a() {
        return reading_a;
    }

    public void setReading_a(String reading_a) {
        this.reading_a = reading_a;
    }

    public String getReading_b() {
        return reading_b;
    }

    public void setReading_b(String reading_b) {
        this.reading_b = reading_b;
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
