package net.stemmaweb.model;

import java.util.Iterator;

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
	
	private String source;				// source
	private String target;				// target
	private String id;					// id
	private String a_derivable_from_b;	// de0
	private String alters_meaning;		// de1
	private String annotation;			// de2
	private String b_derivable_from_a;	// de3
	private String displayform;			// de4
	private String extra;				// de5
	private String is_significant;		// de6
	private String non_independent;		// de7
	private String reading_a;			// de8
	private String reading_b;			// de9
	private String scope;				// de10
	private String type;				// de11
	private String witness;				// de12
	
	public RelationshipModel(){
		
	}
	
	/**
	 * Creates a relationshipModel directly from a Relationship from Neo4J db
	 * @param rel
	 */
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
			case "a_derivable_from_b":
				a_derivable_from_b = rel.getProperty("a_derivable_from_b").toString();				
				break;
			case "alters_meaning":
				alters_meaning = rel.getProperty("alters_meaning").toString();
				break;
			case "annotation":
				annotation = rel.getProperty("annotation").toString();
				break;
			case "b_derivable_from_a":
				b_derivable_from_a = rel.getProperty("b_derivable_from_a").toString();
				break;
			case "displayform":
				displayform = rel.getProperty("displayform").toString();
				break;
			case "extra":
				extra = rel.getProperty("extra").toString();
				break;
			case "is_significant":
				is_significant = rel.getProperty("is_significant").toString();
				break;
			case "non_independent":
				non_independent = rel.getProperty("non_independent").toString();
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
			case "witness":
				witness = rel.getProperty("witness").toString();
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

	public String getA_derivable_from_b() {
		return a_derivable_from_b;
	}

	public void setA_derivable_from_b(String a_derivable_from_b) {
		this.a_derivable_from_b = a_derivable_from_b;
	}

	public String getAlters_meaning() {
		return alters_meaning;
	}

	public void setAlters_meaning(String alters_meaning) {
		this.alters_meaning = alters_meaning;
	}

	public String getAnnotation() {
		return annotation;
	}

	public void setAnnotation(String annotation) {
		this.annotation = annotation;
	}

	public String getB_derivable_from_a() {
		return b_derivable_from_a;
	}

	public void setB_derivable_from_a(String b_derivable_from_a) {
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
		return is_significant;
	}

	public void setIs_significant(String is_significant) {
		this.is_significant = is_significant;
	}

	public String getNon_independent() {
		return non_independent;
	}

	public void setNon_independent(String non_independent) {
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

	public String getWitness() {
		return witness;
	}

	public void setWitness(String witness) {
		this.witness = witness;
	}
	

	
}
