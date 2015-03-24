package net.stemmaweb.rest;

import javax.ws.rs.Path;

import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.neo4j.graphdb.Node;

import net.stemmaweb.model.ReadingModel;

@JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
@Path("reading")
public class Reading implements IResource
{
	public static ReadingModel readingModelFromNode(Node node)
	{
		ReadingModel rm = new ReadingModel();
		
		if(node.hasProperty("grammar_invalid"))
			rm.setDn0(node.getProperty("grammar_invalid").toString());
		if(node.hasProperty("dn99"))
			rm.setDn1(node.getProperty("dn99").toString());
		if(node.hasProperty("is_common"))
			rm.setDn2(node.getProperty("is_common").toString());
		if(node.hasProperty("is_end"))
			rm.setDn3(node.getProperty("is_end").toString());
		if(node.hasProperty("is_lacuna"))
			rm.setDn4(node.getProperty("is_lacuna").toString());
		if(node.hasProperty("is_lemma"))
			rm.setDn5(node.getProperty("is_lemma").toString());
		if(node.hasProperty("is_nonsense"))
			rm.setDn6(node.getProperty("is_nonsense").toString());
		if(node.hasProperty("is_ph"))
			rm.setDn7(node.getProperty("is_ph").toString());
		if(node.hasProperty("is_start"))
			rm.setDn8(node.getProperty("is_start").toString());
		if(node.hasProperty("join_next"))
			rm.setDn9(node.getProperty("join_next").toString());
		if(node.hasProperty("join_prior"))
			rm.setDn10(node.getProperty("join_prior").toString());
		if(node.hasProperty("language"))
			rm.setDn11(node.getProperty("language").toString());
		if(node.hasProperty("lexemes"))
			rm.setDn12(node.getProperty("lexemes").toString());
		if(node.hasProperty("normal_form"))
			rm.setDn13(node.getProperty("normal_form").toString());
		if(node.hasProperty("rank"))
			rm.setDn14(node.getProperty("rank").toString());
		if(node.hasProperty("text"))
			rm.setDn15(node.getProperty("text").toString());
		
		return rm;
	}
	
	
	
}