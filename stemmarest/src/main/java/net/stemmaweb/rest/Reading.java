package net.stemmaweb.rest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.services.DatabaseService;

import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.graphdb.traversal.Traverser;
import org.neo4j.graphdb.traversal.Uniqueness;

@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
@Path("reading")
public class Reading implements IResource {
	GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();

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
				newReading.setProperty(key, oldReading.getProperty(key)
						.toString());
		}
		newReading.addLabel(Nodes.WORD);
		return newReading;
	}

	// the new method (below) is probably more efficient. Still keeping this one
	// for a team discussion
	/*
	 * public Response getAllReadingsOfTradition(@PathParam("tradId") String
	 * tradId) {
	 * 
	 * ArrayList<ReadingModel> readList = new ArrayList<ReadingModel>();
	 * GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);
	 * ExecutionEngine engine = new ExecutionEngine(db);
	 * 
	 * try (Transaction tx = db.beginTx()) { Node traditionNode = null; Node
	 * startNode = null; ExecutionResult result =
	 * engine.execute("match (n:TRADITION {id: '" + tradId + "'}) return n");
	 * Iterator<Node> nodes = result.columnAs("n");
	 * 
	 * if (!nodes.hasNext()) return
	 * Response.status(Status.NOT_FOUND).entity("trad node not found").build();
	 * 
	 * traditionNode = nodes.next();
	 * 
	 * Iterable<Relationship> rels =
	 * traditionNode.getRelationships(Direction.OUTGOING);
	 * 
	 * if (rels == null) return
	 * Response.status(Status.NOT_FOUND).entity("rels not found").build();
	 * 
	 * Iterator<Relationship> relIt = rels.iterator();
	 * 
	 * while (relIt.hasNext()) { Relationship rel = relIt.next(); startNode =
	 * rel.getEndNode(); if (startNode != null && startNode.hasProperty("text"))
	 * { if (startNode.getProperty("text").equals("#START#")) { rels =
	 * startNode.getRelationships(Direction.OUTGOING); break; } } }
	 * 
	 * if (rels == null) return
	 * Response.status(Status.NOT_FOUND).entity("start node not found").build();
	 * 
	 * TraversalDescription td = db.traversalDescription().breadthFirst()
	 * .relationships(ERelations.NORMAL,
	 * Direction.OUTGOING).evaluator(Evaluators.excludeStartPosition());
	 * 
	 * Traverser traverser = td.traverse(startNode); for (org.neo4j.graphdb.Path
	 * path : traverser) { Node nd = path.endNode(); ReadingModel rm =
	 * Reading.readingModelFromNode(nd); readList.add(rm); }
	 * 
	 * tx.success(); } catch (Exception e) { e.printStackTrace(); } finally {
	 * db.shutdown(); } // return Response.status(Status.NOT_FOUND).build();
	 * 
	 * return Response.ok(readList).build(); }
	 */

	@GET
	@Path("/{tradId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAllReadings(@PathParam("tradId") String tradId) {

		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);
		ArrayList<ReadingModel> readingModels = new ArrayList<ReadingModel>();

		Node startNode = DatabaseService.getStartNode(tradId, db);
		if (startNode == null)
			return Response.status(Status.NOT_FOUND)
					.entity("Could not find tradition with this id").build();
		readingModels = getAllReadingsAsSortedList(startNode, db);

		db.shutdown();
		return Response.ok(readingModels).build();
	}

	private ArrayList<ReadingModel> getAllReadingsAsSortedList(Node startNode,
			GraphDatabaseService db) {

		ArrayList<ReadingModel> readingModels = new ArrayList<ReadingModel>();

		try (Transaction tx = db.beginTx()) {

			for (Node node : db.traversalDescription().depthFirst()
					.relationships(ERelations.NORMAL, Direction.OUTGOING)
					.evaluator(Evaluators.all())
					.uniqueness(Uniqueness.NODE_GLOBAL).traverse(startNode)
					.nodes()) {
				ReadingModel tempReading = Reading.readingModelFromNode(node);
				readingModels.add(tempReading);
				/*
				 * if (readingModels.size() == 0) {
				 * readingModels.add(tempReading); } else { // sort the list of
				 * reading by rank int i = readingModels.size() - 1; while
				 * (readingModels.get(i).getDn14() > tempReading .getDn14() && i
				 * != 0) { i--; } readingModels.add(i, tempReading); }
				 */}
			tx.success();
		}
		Collections.sort(readingModels);
		return readingModels;
	}

	@GET
	@Path("identical/{tradId}/{startRank}/{endRank}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getIdenticalReadings(@PathParam("tradId") String tradId,
			@PathParam("startRank") long startRank,
			@PathParam("endRank") long endRank) {

		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);
		ArrayList<ReadingModel> readingModels = new ArrayList<ReadingModel>();

		Node startNode = DatabaseService.getStartNode(tradId, db);
		if (startNode == null)
			return Response.status(Status.NOT_FOUND)
					.entity("Could not find tradition with this id").build();
		readingModels = getAllReadingsAsSortedList(startNode, db);

		ArrayList<ReadingModel> identicalReadings = new ArrayList<ReadingModel>();
		identicalReadings = getIdenticalReadingsAsList(readingModels,
				startRank, endRank);

		if (identicalReadings.size() == 0)
			return Response.status(Status.NOT_FOUND)
					.entity("no identical readings were found").build();

		return Response.ok(identicalReadings).build();
	}
/**
 * gets identical readings in a tradition between the given ranks
 * @param readingModels list of all readings sorted according to rank
 * @param startRank
 * @param endRank
 * @return list of the identical readings as readingModels
 */
	private ArrayList<ReadingModel> getIdenticalReadingsAsList(
			ArrayList<ReadingModel> readingModels, long startRank, long endRank) {
		ArrayList<ReadingModel> identicalReadings = new ArrayList<ReadingModel>();

		for (int i = 0; i < readingModels.size() - 2; i++) {
			while (readingModels.get(i).getDn14() == readingModels.get(i + 1)
					.getDn14() && i + 1 < readingModels.size()) {
				if (readingModels.get(i).getDn15()
						.equals(readingModels.get(i + 1).getDn15())
						&& readingModels.get(i).getDn14() < endRank
						&& readingModels.get(i).getDn14() > startRank) {
					identicalReadings.add(readingModels.get(i));
					identicalReadings.add(readingModels.get(i + 1));
				}
				i++;
			}
		}
		return identicalReadings;
	}
}