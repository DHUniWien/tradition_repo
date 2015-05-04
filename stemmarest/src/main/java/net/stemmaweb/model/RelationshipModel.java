package net.stemmaweb.model;

import java.util.Iterator;

import javax.xml.bind.annotation.XmlRootElement;

import org.neo4j.graphdb.Relationship;

/**
 * 
 * Provides a model for a relationship outside of the database. Can be parsed
 * into a json object.
 * 
 * @author PSE FS 2015 Team2
 *
 */
@XmlRootElement
public class RelationshipModel {
	
	private String source;
	private String target;
	private String id;
	private String de0; // a_derivable_from_b
	private String de1; // alters_meaning
	private String de2; // annotation
	private String de3; // b_derivable_from_a
	private String de4; // displayform
	private String de5; // extra
	private String de6; // is_significant
	private String de7; // non_independent
	private String de8; // reading_a
	private String de9; // reading_b
	private String de10; // scope
	private String de11; // type
	private String de12; // witness
	
	public RelationshipModel(){
		
	}
	
	public RelationshipModel(Relationship rel){
		Iterable<String> properties = rel.getPropertyKeys();
		id = Long.toString(rel.getId());
		Iterator<String> iterator = properties.iterator();
		while(iterator.hasNext()){
			
			switch (iterator.next()){
			case "target":
				target = rel.getProperty("target").toString();
				break;
			case "source":
				source = rel.getProperty("source").toString();
				break;
			case "de0":
				de0 = rel.getProperty("de0").toString();				
				break;
			case "de1":
				de1 = rel.getProperty("de1").toString();
				break;
			case "de2":
				de2 = rel.getProperty("de2").toString();
				break;
			case "de3":
				de3 = rel.getProperty("de3").toString();
				break;
			case "de4":
				de4 = rel.getProperty("de4").toString();
				break;
			case "de5":
				de5 = rel.getProperty("de5").toString();
				break;
			case "de6":
				de6 = rel.getProperty("de6").toString();
				break;
			case "de7":
				de7 = rel.getProperty("de7").toString();
				break;
			case "de8":
				de8 = rel.getProperty("de8").toString();				
				break;
			case "de9":
				de9 = rel.getProperty("de9").toString();
				break;
			case "de10":
				de10 = rel.getProperty("de10").toString();
				break;
			case "de11":
				de11 = rel.getProperty("de11").toString();
				break;
			case "de12":
				de12 = rel.getProperty("de12").toString();
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
	
	public String getDe0() {
		return de0;
	}
	public void setDe0(String de0) {
		this.de0 = de0;
	}
	
	public String getDe2() {
		return de2;
	}
	public void setDe2(String de2) {
		this.de2 = de2;
	}
	
	public String getDe4() {
		return de4;
	}
	public void setDe4(String de4) {
		this.de4 = de4;
	}
	
	public String getDe6() {
		return de6;
	}
	public void setDe6(String de6) {
		this.de6 = de6;
	}
	
	public String getDe1() {
		return de1;
	}
	public void setDe1(String de1) {
		this.de1 = de1;
	}
	public String getDe3() {
		return de3;
	}
	public void setDe3(String de3) {
		this.de3 = de3;
	}
	public String getDe5() {
		return de5;
	}
	public void setDe5(String de5) {
		this.de5 = de5;
	}
	public String getDe7() {
		return de7;
	}
	public void setDe7(String de7) {
		this.de7 = de7;
	}
	public String getDe8() {
		return de8;
	}
	public void setDe8(String de8) {
		this.de8 = de8;
	}
	public String getDe9() {
		return de9;
	}
	public void setDe9(String de9) {
		this.de9 = de9;
	}
	public String getDe10() {
		return de10;
	}
	public void setDe10(String de10) {
		this.de10 = de10;
	}
	public String getDe11() {
		return de11;
	}
	public void setDe11(String de11) {
		this.de11 = de11;
	}
	public String getDe12() {
		return de12;
	}
	public void setDe12(String de12) {
		this.de12 = de12;
	}

	
}
