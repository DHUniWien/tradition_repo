package net.stemmaweb.model;

import org.checkerframework.checker.nullness.qual.NonNull;
import javax.xml.bind.annotation.XmlRootElement;

import org.neo4j.graphdb.Node;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import org.neo4j.graphdb.Transaction;

/**
 * Provides a model for a reading outside of the database. Can be parsed into a
 * json object.
 * 
 * @author PSE FS 2015 Team2
 */

@XmlRootElement
@JsonInclude(Include.NON_NULL)
public class ReadingModel implements Comparable<ReadingModel> {

    private Boolean grammar_invalid; // dn0
    private String id;              // dn1
    private Boolean is_common = false;       // dn2
    private Boolean is_end = false;          // dn3
    private Boolean is_lacuna = false;       // dn4
    private Boolean is_lemma = false;        // dn5
    private Boolean is_nonsense = false;     // dn6
    private Boolean is_ph = false;           // dn7
    private Boolean is_start = false;        // dn8
    private Boolean join_next = false;       // dn9
    private Boolean join_prior = false;      // dn10
    private String language;        // dn11
    private String lexemes;         // dn12
    private String normal_form;     // dn13
    private Long rank;              // dn14
    private String text;            // dn15

    /**
     * Generates a model from a Neo4j Node
     * @param node -
     */
    public ReadingModel(Node node) {
        try (Transaction tx = node.getGraphDatabase().beginTx()) {
            if (node.hasProperty("grammar_invalid"))
                this.setGrammar_invalid((Boolean) node.getProperty("grammar_invalid"));
            this.setId(String.valueOf(node.getId()));
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

    public String getLexemes() {
        return lexemes;
    }

    public void setLexemes(String lexemes) {
        this.lexemes = lexemes;
    }

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

    @Override
    public int compareTo(@NonNull ReadingModel readingModel) {
        Long compareRank = readingModel.getRank();
        return (int) (this.rank - compareRank);
    }


}
