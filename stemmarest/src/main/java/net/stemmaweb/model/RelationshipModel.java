package net.stemmaweb.model;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class RelationshipModel {
	private String id;
	private String sourceNode;
	private String targetNode;
	private String reading_a;
	private String reading_b;
	private int alters_meaning;	
	private String is_significant;
	private String annotation;
	private String scope;
	private String type;


	
	public String getSourceNode() {
		return sourceNode;
	}
	public void setSourceNode(String sourceNode) {
		this.sourceNode = sourceNode;
	}
	public String getTargetNode() {
		return targetNode;
	}
	public void setTargetNode(String targetNode) {
		this.targetNode = targetNode;
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
	public int getAlters_meaning() {
		return alters_meaning;
	}
	public void setAlters_meaning(int alters_meaning) {
		this.alters_meaning = alters_meaning;
	}
	public String getIs_significant() {
		return is_significant;
	}
	public void setIs_significant(String is_significant) {
		this.is_significant = is_significant;
	}
	public String getAnnotation() {
		return annotation;
	}
	public void setAnnotation(String annotation) {
		this.annotation = annotation;
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
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
}
