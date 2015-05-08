package net.stemmaweb.model;

import javax.xml.bind.annotation.XmlRootElement;

import org.neo4j.graphdb.Node;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Provides a model for a reading outside of the database. Can be parsed into a
 * json object.
 * 
 * @author PSE FS 2015 Team2
 */

@XmlRootElement
@JsonInclude(Include.NON_NULL)
public class ReadingModel implements Comparable<ReadingModel> {

	private String grammar_invalid; // dn0
	private String id; 				// dn1
	private String is_common; 		// dn2
	private String is_end; 			// dn3
	private String is_lacuna; 		// dn4
	private String is_lemma; 		// dn5
	private String is_nonsense; 	// dn6
	private String is_ph; 			// dn7
	private String is_start; 		// dn8
	private String join_next; 		// dn9
	private String join_prior; 		// dn10
	private String language; 		// dn11
	private String lexemes; 		// dn12
	private String normal_form; 	// dn13
	private Long rank; 				// dn14
	private String text; 			// dn15

	/**
	 * Generates a model from a Neo4j Node
	 * @param node
	 */
	public ReadingModel(Node node) {
		if (node.hasProperty("grammar_invalid"))
			this.setGrammar_invalid(node.getProperty("grammar_invalid").toString());
		this.setId(String.valueOf(node.getId()));
		if (node.hasProperty("is_common"))
			this.setIs_common(node.getProperty("is_common").toString());
		if (node.hasProperty("is_end"))
			this.setIs_end(node.getProperty("is_end").toString());
		if (node.hasProperty("is_lacuna"))
			this.setIs_lacuna(node.getProperty("is_lacuna").toString());
		if (node.hasProperty("is_lemma"))
			this.setIs_lemma(node.getProperty("is_lemma").toString());
		if (node.hasProperty("is_nonsense"))
			this.setIs_nonsense(node.getProperty("is_nonsense").toString());
		if (node.hasProperty("is_ph"))
			this.setIs_ph(node.getProperty("is_ph").toString());
		if (node.hasProperty("is_start"))
			this.setIs_start(node.getProperty("is_start").toString());
		if (node.hasProperty("join_next"))
			this.setJoin_next(node.getProperty("join_next").toString());
		if (node.hasProperty("join_prior"))
			this.setJoin_prior(node.getProperty("join_prior").toString());
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
	}

	public ReadingModel() {
	}

	public String getGrammar_invalid() {
		return grammar_invalid;
	}

	public void setGrammar_invalid(String grammar_invalid) {
		this.grammar_invalid = grammar_invalid;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getIs_common() {
		return is_common;
	}

	public void setIs_common(String is_common) {
		this.is_common = is_common;
	}

	public String getIs_end() {
		return is_end;
	}

	public void setIs_end(String is_end) {
		this.is_end = is_end;
	}

	public String getIs_lacuna() {
		return is_lacuna;
	}

	public void setIs_lacuna(String is_lacuna) {
		this.is_lacuna = is_lacuna;
	}

	public String getIs_lemma() {
		return is_lemma;
	}

	public void setIs_lemma(String is_lemma) {
		this.is_lemma = is_lemma;
	}

	public String getIs_nonsense() {
		return is_nonsense;
	}

	public void setIs_nonsense(String is_nonsense) {
		this.is_nonsense = is_nonsense;
	}

	public String getIs_ph() {
		return is_ph;
	}

	public void setIs_ph(String is_ph) {
		this.is_ph = is_ph;
	}

	public String getIs_start() {
		return is_start;
	}

	public void setIs_start(String is_start) {
		this.is_start = is_start;
	}

	public String getJoin_next() {
		return join_next;
	}

	public void setJoin_next(String join_next) {
		this.join_next = join_next;
	}

	public String getJoin_prior() {
		return join_prior;
	}

	public void setJoin_prior(String join_prior) {
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
	public int compareTo(ReadingModel readingModel) {
		Long compareRank = ((ReadingModel) readingModel).getRank();
		return (int) (this.rank - compareRank);
	}

	
}
