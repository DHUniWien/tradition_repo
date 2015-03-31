package net.stemmaweb.rest;

import javax.ws.rs.Path;

import net.stemmaweb.model.ReadingModel;

import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.neo4j.graphdb.Node;

@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
@Path("reading")
public class Reading implements IResource {
	public static ReadingModel readingModelFromNode(Node node) {
		ReadingModel rm = new ReadingModel();

		if (node.hasProperty("dn0"))
			rm.setDn0(node.getProperty("dn0").toString());
		rm.setDn1(String.valueOf(node.getId()));
		if (node.hasProperty("dn2"))
			rm.setDn2(node.getProperty("dn2").toString());
		if (node.hasProperty("dn3"))
			rm.setDn3(node.getProperty("dn3").toString());
		if (node.hasProperty("dn4"))
			rm.setDn4(node.getProperty("dn4").toString());
		if (node.hasProperty("dn5"))
			rm.setDn5(node.getProperty("dn5").toString());
		if (node.hasProperty("dn6"))
			rm.setDn6(node.getProperty("dn6").toString());
		if (node.hasProperty("dn7"))
			rm.setDn7(node.getProperty("dn7").toString());
		if (node.hasProperty("dn8"))
			rm.setDn8(node.getProperty("dn8").toString());
		if (node.hasProperty("dn9"))
			rm.setDn9(node.getProperty("dn9").toString());
		if (node.hasProperty("dn10"))
			rm.setDn10(node.getProperty("dn10").toString());
		if (node.hasProperty("dn11"))
			rm.setDn11(node.getProperty("dn11").toString());
		if (node.hasProperty("dn12"))
			rm.setDn12(node.getProperty("dn12").toString());
		if (node.hasProperty("dn13"))
			rm.setDn13(node.getProperty("dn13").toString());
		if (node.hasProperty("dn14"))
			rm.setDn14(Long.parseLong(node.getProperty("dn14").toString()));
		if (node.hasProperty("dn15"))
			rm.setDn15(node.getProperty("dn15").toString());

		return rm;
	}

	public static Node copyReadingProperties(Node oldReading, Node newReading) {
		for (int i = 0; i < 16; i++) {
			String key = "dn" + i;
			if (oldReading.hasProperty(key))
				newReading.setProperty(key, oldReading.getProperty(key).toString());
		}
		newReading.addLabel(Nodes.WORD);
		return newReading;
	}

}