package net.stemmaweb.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import org.checkerframework.checker.nullness.qual.NonNull;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * Provides a model for a reading outside of the database. Can be parsed into a
 * json object.
 * 
 * @author PSE FS 2015 Team2
 */

@XmlRootElement
@JsonInclude(Include.NON_DEFAULT)
public class ReadingModel implements Comparable<ReadingModel> {

    /**
     * The internal ID of the reading
     */
    private String id;              // dn1
    /**
     * The ID of the section to which this reading belongs
     */
    private String section;
    /**
     * True if the reading appears in all witnesses
     */
    private Boolean is_common = false;       // dn2
    /**
     * True if the reading is a collation end node
     */
    private Boolean is_end = false;          // dn3
    /**
     * True if the reading is a 'lacuna' node, representing missing text
     */
    private Boolean is_lacuna = false;       // dn4
    /**
     * True if the reading has been set as canonical / lemma text for editorial purposes
     */
    private Boolean is_lemma = false;        // dn5
    /**
     * True if the reading is an emendation
     */
    private Boolean is_emendation = false;
    /**
     * True if the reading text is nonsensical
     */
    private Boolean is_nonsense = false;     // dn6
    private Boolean is_ph = false;           // dn7
    /**
     * True if the reading is a collation start node
     */
    private Boolean is_start = false;        // dn8
    /**
     * True if the reading's grammatical form does not make sense in context
     */
    private Boolean grammar_invalid; // dn0
    /**
     * True if the reading is a partial word that should be joined directly to the next reading
     */
    private Boolean join_next = false;       // dn9
    /**
     * True if the reading is a partial word that should be joined directly to the prior reading
     */
    private Boolean join_prior = false;      // dn10
    /**
     * The language of the reading text
     */
    private String language;        // dn11
    private String lexemes;         // dn12
    /**
     * The canonically-spelled form of the reading text
     */
    private String normal_form;     // dn1
    /**
     * The graph rank of this reading, in its collation
     */
    private Long rank;              // dn14
    /**
     * The text of the reading
     */
    private String text;            // dn15
    private String orig_reading;    // meant for use with duplicated readings, not saved
    private String display;         // HTML rendering of token, for graph/image display
    /**
     * The user-supplied annotation or comment for this reading
     */
    private String annotation;      // general purpose saving of information
    /**
     * Any additional user-supplied JSON data for this reading
     */
    private String extra;
    /**
     * The authority for an emendation reading
     */
    private String authority;

    /* === Calculated read-only values === */
    /**
     * The list of witnesses to which this reading belongs. Read-only.
     */
    private List<String> witnesses;

    /**
     * The list of readings that this one is representing in a normalised graph.
     */
    private List<ReadingModel> represented;

    /**
     * Generates a model from a Neo4j Node
     * @param node - The node with label READING from which the model should take its values
     */
    public ReadingModel(Node node) {
        try (Transaction tx = node.getGraphDatabase().beginTx()) {
            if (node.hasProperty("grammar_invalid"))
                this.setGrammar_invalid((Boolean) node.getProperty("grammar_invalid"));
            this.setId(String.valueOf(node.getId()));
            this.setSection(node.getProperty("section_id").toString());
            if (node.hasProperty("is_common"))
                this.setIs_common((Boolean) node.getProperty("is_common"));
            if (node.hasProperty("is_end"))
                this.setIs_end((Boolean) node.getProperty("is_end"));
            if (node.hasProperty("is_lacuna"))
                this.setIs_lacuna((Boolean) node.getProperty("is_lacuna"));
            if (node.hasProperty("is_lemma"))
                this.setIs_lemma((Boolean) node.getProperty("is_lemma"));
            if (node.hasProperty("is_nonsense"))
                this.setIs_nonsense((Boolean) node.getProperty("is_nonsense"));
            if (node.hasProperty("is_ph"))
                this.setIs_ph((Boolean) node.getProperty("is_ph"));
            if (node.hasProperty("is_start"))
                this.setIs_start((Boolean) node.getProperty("is_start"));
            if (node.hasProperty("join_next"))
                this.setJoin_next((Boolean) node.getProperty("join_next"));
            if (node.hasProperty("join_prior"))
                this.setJoin_prior((Boolean) node.getProperty("join_prior"));
            if (node.hasProperty("language"))
                this.setLanguage(node.getProperty("language").toString());
            if (node.hasProperty("lexemes"))
                this.setLexemes(node.getProperty("lexemes").toString());
            if (node.hasProperty("normal_form"))
                this.setNormal_form(node.getProperty("normal_form").toString());
            if (node.hasProperty("rank"))
                this.setRank(Long.parseLong(node.getProperty("rank").toString()));
            if (node.hasProperty("text"))
                this.setText(node.getProperty("text").toString());
            if (node.hasProperty("display"))
                this.setDisplay(node.getProperty("display").toString());
            if (node.hasProperty("annotation"))
                this.setAnnotation(node.getProperty("annotation").toString());
            if (node.hasProperty("extra")) {
                String jsonData = node.getProperty("extra").toString();
                try {
                    // Try to parse it, before we actually attempt to use it
                    new JSONObject(jsonData);
                    this.setExtra(jsonData);
                } catch (JSONException e) {
                    // Emit a warning, but carry on
                    System.err.println("Invalid JSON string in reading extra parameter: " + jsonData);
                }
            }
            if (node.hasLabel(Nodes.EMENDATION)) {
                this.setIs_emendation(true);
                // We don't check whether this property exists, because it darn well should
                this.setAuthority(node.getProperty("authority").toString());
            }
            // Get the witnesses
            HashSet<String> collectedWits = new HashSet<>();
            List<Relationship> seq = new ArrayList<>();
            // If we are operating under normalization, we need to look at the NSEQUENCE links rather than
            // the SEQUENCE links, but in this case the SEQUENCE links will be redundant so there is no
            // harm in looking at them anyway.
            node.getRelationships(ERelations.SEQUENCE, Direction.BOTH).forEach(seq::add);
            node.getRelationships(ERelations.NSEQUENCE, Direction.BOTH).forEach(seq::add);
            for (Relationship r : seq) {
                for (String prop : r.getPropertyKeys()) {
                    String[] sigla = (String[]) r.getProperty(prop);
                    if (prop.equals("witnesses")) {
                        collectedWits.addAll(Arrays.asList(sigla));
                    } else {
                        Arrays.stream(sigla).forEach(x -> collectedWits.add(String.format("%s (%s)", x, prop)));
                    }
                }
            }
            this.witnesses = new ArrayList<>(collectedWits);
            this.witnesses.sort(String::compareTo);
            // Get any represented readings
            for (Relationship r : node.getRelationships(ERelations.REPRESENTS, Direction.OUTGOING)) {
                this.addRepresented(new ReadingModel(r.getEndNode()));
            }
            tx.success();
        }
    }

    public ReadingModel() {
    }

    public Boolean getGrammar_invalid() {
        return grammar_invalid;
    }

    public void setGrammar_invalid(Boolean grammar_invalid) {
        this.grammar_invalid = grammar_invalid;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public Boolean getIs_common() {
        return is_common;
    }

    public void setIs_common(Boolean is_common) {
        this.is_common = is_common;
    }

    public Boolean getIs_end() {
        return is_end;
    }

    public void setIs_end(Boolean is_end) {
        this.is_end = is_end;
    }

    public Boolean getIs_lacuna() {
        return is_lacuna;
    }

    public void setIs_lacuna(Boolean is_lacuna) {
        this.is_lacuna = is_lacuna;
    }

    public Boolean getIs_lemma() {
        return is_lemma;
    }

    public void setIs_lemma(Boolean is_lemma) {
        this.is_lemma = is_lemma;
    }

    public Boolean getIs_emendation() {
        return is_emendation;
    }

    @SuppressWarnings("WeakerAccess")
    public void setIs_emendation(Boolean is_emendation) { this.is_emendation = is_emendation; }

    public Boolean getIs_nonsense() {
        return is_nonsense;
    }

    public void setIs_nonsense(Boolean is_nonsense) {
        this.is_nonsense = is_nonsense;
    }

    public Boolean getIs_ph() {
        return is_ph;
    }

    public void setIs_ph(Boolean is_ph) {
        this.is_ph = is_ph;
    }

    public Boolean getIs_start() {
        return is_start;
    }

    public void setIs_start(Boolean is_start) {
        this.is_start = is_start;
    }

    public Boolean getJoin_next() {
        return join_next;
    }

    public void setJoin_next(Boolean join_next) {
        this.join_next = join_next;
    }

    public Boolean getJoin_prior() {
        return join_prior;
    }

    public void setJoin_prior(Boolean join_prior) {
        this.join_prior = join_prior;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getLexemes() { return lexemes; }

    public void setLexemes(String lexemes) { this.lexemes = lexemes; }

    public String getNormal_form() {
        return normal_form;
    }

    public void setNormal_form(String normal_form) {
        this.normal_form = normal_form;
    }

    public Long getRank() {
        return rank;
    }

    public void setRank(Long rank) {
        this.rank = rank;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getDisplay() {
        return display;
    }

    public void setDisplay(String text) {
        this.display = text;
    }

    public String getAnnotation() {
        return annotation;
    }

    public void setAnnotation(String annotation) {
        this.annotation = annotation;
    }

    public String getOrig_reading() {
        return orig_reading;
    }

    public void setOrig_reading(String orig_reading) {
        this.orig_reading = orig_reading;
    }

    public List<String> getWitnesses() {
        return this.witnesses;
    }

    @Override
    public int compareTo(@NonNull ReadingModel readingModel) {
        Long compareRank = readingModel.getRank();
        return (int) (this.rank - compareRank);
    }

    @JsonIgnore
    public String normalized() { return normal_form == null ? text : normal_form; }

    public String getExtra() {
        return extra;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }

    public String getAuthority() {
        return authority;
    }

    public void setAuthority(String authority) {
        this.authority = authority;
    }

    public List<ReadingModel> getRepresented() { return represented; }

    private void addRepresented(ReadingModel rm) {
        if (represented == null) represented = new ArrayList<>();
        represented.add(rm);
    }
}
