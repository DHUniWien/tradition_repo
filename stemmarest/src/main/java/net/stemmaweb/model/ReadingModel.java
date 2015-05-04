package net.stemmaweb.model;

import javax.xml.bind.annotation.XmlRootElement;

import org.neo4j.graphdb.Node;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * 
 * Provides a model for a reading outside of the database. Can be parsed into a
 * json object.
 * 
 * @author PSE FS 2015 Team2
 *
 */
@XmlRootElement
@JsonInclude(Include.NON_NULL)
public class ReadingModel implements Comparable<ReadingModel> {
	
	private String dn0; // grammar_invalid
	private String dn1; // id
	private String dn2; // is_common
	private String dn3; // is_end
	private String dn4; // is_lacuna
	private String dn5; // is_lemma
	private String dn6; // is_nonsense
	private String dn7; // is_ph
	private String dn8; // is_start
	private String dn9; // join_next
	private String dn10; // join_prior
	private String dn11; // language
	private String dn12; // lexemes
	private String dn13; // normal_form
	private Long dn14; // rank
	private String dn15; // text

	public ReadingModel(Node node) {
		if (node.hasProperty("dn0"))
			this.setDn0(node.getProperty("dn0").toString());
		this.setDn1(String.valueOf(node.getId()));
		if (node.hasProperty("dn2"))
			this.setDn2(node.getProperty("dn2").toString());
		if (node.hasProperty("dn3"))
			this.setDn3(node.getProperty("dn3").toString());
		if (node.hasProperty("dn4"))
			this.setDn4(node.getProperty("dn4").toString());
		if (node.hasProperty("dn5"))
			this.setDn5(node.getProperty("dn5").toString());
		if (node.hasProperty("dn6"))
			this.setDn6(node.getProperty("dn6").toString());
		if (node.hasProperty("dn7"))
			this.setDn7(node.getProperty("dn7").toString());
		if (node.hasProperty("dn8"))
			this.setDn8(node.getProperty("dn8").toString());
		if (node.hasProperty("dn9"))
			this.setDn9(node.getProperty("dn9").toString());
		if (node.hasProperty("dn10"))
			this.setDn10(node.getProperty("dn10").toString());
		if (node.hasProperty("dn11"))
			this.setDn11(node.getProperty("dn11").toString());
		if (node.hasProperty("dn12"))
			this.setDn12(node.getProperty("dn12").toString());
		if (node.hasProperty("dn13"))
			this.setDn13(node.getProperty("dn13").toString());
		if (node.hasProperty("dn14"))
			this.setDn14(Long.parseLong(node.getProperty("dn14").toString()));
		if (node.hasProperty("dn15"))
			this.setDn15(node.getProperty("dn15").toString());
	}

	public ReadingModel() {

	}

	public String getDn0() {
		return dn0;
	}
	public void setDn0(String dn0) {
		this.dn0 = dn0;
	}
	public String getDn1() {
		return dn1;
	}
	public void setDn1(String dn1) {
		this.dn1 = dn1;
	}
	public String getDn2() {
		return dn2;
	}
	public void setDn2(String dn2) {
		this.dn2 = dn2;
	}
	public String getDn3() {
		return dn3;
	}
	public void setDn3(String dn3) {
		this.dn3 = dn3;
	}
	public String getDn4() {
		return dn4;
	}
	public void setDn4(String dn4) {
		this.dn4 = dn4;
	}
	public String getDn5() {
		return dn5;
	}
	public void setDn5(String dn5) {
		this.dn5 = dn5;
	}
	public String getDn6() {
		return dn6;
	}
	public void setDn6(String dn6) {
		this.dn6 = dn6;
	}
	public String getDn7() {
		return dn7;
	}
	public void setDn7(String dn7) {
		this.dn7 = dn7;
	}
	public String getDn8() {
		return dn8;
	}
	public void setDn8(String dn8) {
		this.dn8 = dn8;
	}
	public String getDn9() {
		return dn9;
	}
	public void setDn9(String dn9) {
		this.dn9 = dn9;
	}
	public String getDn10() {
		return dn10;
	}
	public void setDn10(String dn10) {
		this.dn10 = dn10;
	}
	public String getDn11() {
		return dn11;
	}
	public void setDn11(String dn11) {
		this.dn11 = dn11;
	}
	public String getDn12() {
		return dn12;
	}
	public void setDn12(String dn12) {
		this.dn12 = dn12;
	}
	public String getDn13() {
		return dn13;
	}
	public void setDn13(String dn13) {
		this.dn13 = dn13;
	}
	public Long getDn14() {
		return dn14;
	}
	public void setDn14(Long dn14) {
		this.dn14 = dn14;
	}
	public String getDn15() {
		return dn15;
	}
	public void setDn15(String dn15) {
		this.dn15 = dn15;
	}
	
	@Override
	public int compareTo(ReadingModel readingModel) {
		Long compareRank = ((ReadingModel) readingModel).getDn14();
		return (int) (this.dn14 - compareRank);
	}

	
}
