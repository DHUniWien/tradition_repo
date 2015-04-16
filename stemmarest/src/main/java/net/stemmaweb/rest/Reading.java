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
import net.stemmaweb.services.EvaluatorService;

import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.graphdb.traversal.Traverser;
import org.neo4j.graphdb.traversal.Uniqueness;

import Exceptions.DataBaseException;

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
	
	/**
	 * gets the next readings from a given readings in the same witness
	 * 
	 * @param tradId
	 *            : tradition id
	 * @param textId
	 *            : witness id
	 * @param readId
	 *            : reading id
	 * @return the requested reading
	 */
	@GET
	@Path("next/{tradId}/{textId}/{readId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getNextReadingInWitness(@PathParam("tradId") String tradId,
			@PathParam("textId") String textId,
			@PathParam("readId") long readId) {

		final String WITNESS_ID = textId;
		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);

		Node startNode = DatabaseService.getStartNode(tradId, db);
		if (startNode == null)
			return Response.status(Status.NOT_FOUND)
					.entity("Could not find tradition with this id").build();

		ReadingModel reading = getNextReading(WITNESS_ID, readId, startNode, db);
		if (reading.getDn15().equals("#END#"))
			return Response.status(Status.NOT_FOUND).entity("this was the last reading of this witness").build();

		return Response.ok(reading).build();
	}
	
	/**
	 * gets the Next reading to a given reading and a witness help method
	 * 
	 * @param WITNESS_ID
	 * @param readId
	 * @param startNode
	 * @return the Next reading to that of the readId
	 */
	private ReadingModel getNextReading(String WITNESS_ID, long readId,
			Node startNode, GraphDatabaseService db) {

		EvaluatorService evaService = new EvaluatorService();
		Evaluator e = evaService.getEvalForWitness(WITNESS_ID);

		try (Transaction tx = db.beginTx()) {
			int stop = 0;
			for (Node node : db.traversalDescription().depthFirst()
					.relationships(ERelations.NORMAL, Direction.OUTGOING)
					.evaluator(e).uniqueness(Uniqueness.NONE)
					.traverse(startNode).nodes()) {
				if (stop == 1) {
					tx.success();
					return Reading.readingModelFromNode(node);
				}
				if (node.getId()==readId) {
					stop = 1;
				}
			}
			db.shutdown();
			throw new DataBaseException("given readings not found");
		}
	}
	
	/**
	 * gets the next readings from a given readings in the same witness
	 * 
	 * @param tradId
	 *            : tradition id
	 * @param textId
	 *            : witness id
	 * @param readId
	 *            : reading id
	 * @return the requested reading
	 */
	@GET
	@Path("/previous/{tradId}/{textId}/{readId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getPreviousReadingInWitness(
			@PathParam("tradId") String tradId,
			@PathParam("textId") String textId,
			@PathParam("readId") long readId) {

		final String WITNESS_ID = textId;
		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);

		Node startNode = DatabaseService.getStartNode(tradId, db);
		if (startNode == null)
			return Response.status(Status.NOT_FOUND)
					.entity("Could not find tradition with this id").build();

		ReadingModel reading = getPreviousReading(WITNESS_ID, readId,
				startNode, db);

		if (reading==null)
			return Response.status(Status.NOT_FOUND)
					.entity("there is no previous reading to this reading").build();
		return Response.ok(reading).build();
	}

	
	
	/**
	 * gets the Previous reading to a given reading and a witness help method
	 * 
	 * @param WITNESS_ID
	 * @param readId
	 * @param startNode
	 * @return the Previous reading to that of the readId
	 */
	private ReadingModel getPreviousReading(final String WITNESS_ID,
			long readId, Node startNode, GraphDatabaseService db) {
		Node previousNode = null;

		EvaluatorService evaService = new EvaluatorService();
		Evaluator e = evaService.getEvalForWitness(WITNESS_ID);

		try (Transaction tx = db.beginTx()) {
			for (Node node : db.traversalDescription().depthFirst()
					.relationships(ERelations.NORMAL, Direction.OUTGOING)
					.evaluator(e).uniqueness(Uniqueness.NONE)
					.traverse(startNode).nodes()) {

				if (node.getId()==readId) {
					tx.success();
					if (previousNode != null)
						return Reading.readingModelFromNode(previousNode);
					else {
						db.shutdown();
						return null;
					}
				}
				previousNode = node;
			}
			db.shutdown();
			throw new DataBaseException("given readings not found");
		}
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
		if (startNode == null){
			return Response.status(Status.NOT_FOUND)
					.entity("Could not find tradition with this id").build();
		}
		readingModels = getAllReadingsFromTradition(startNode, db);

		db.shutdown();
		return Response.ok(readingModels).build();
	}

	private ArrayList<ReadingModel> getAllReadingsFromTradition(Node startNode,
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
			}
			tx.success();
		}
		return readingModels;
	}

	private ArrayList<ReadingModel> getAllReadingsFromTraditionBetweenRanks(
			Node startNode, long startRank, long endRank,
			GraphDatabaseService db) {

		ArrayList<ReadingModel> readingModels = new ArrayList<ReadingModel>();

		try (Transaction tx = db.beginTx()) {

			for (Node node : db.traversalDescription().depthFirst()
					.relationships(ERelations.NORMAL, Direction.OUTGOING)
					.evaluator(Evaluators.all())
					.uniqueness(Uniqueness.NODE_GLOBAL).traverse(startNode)
					.nodes()) {
				long nodeRank = (long) node.getProperty("dn14");

				if (nodeRank < endRank && nodeRank > startRank) {
					ReadingModel tempReading = Reading
							.readingModelFromNode(node);
					readingModels.add(tempReading);
				}
			}
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
		readingModels = getAllReadingsFromTraditionBetweenRanks(startNode,
				startRank, endRank, db);

		ArrayList<ReadingModel> identicalReadings = new ArrayList<ReadingModel>();
		identicalReadings = getIdenticalReadingsAsList(readingModels,
				startRank, endRank);

		if (identicalReadings.size() == 0)
			return Response.status(Status.NOT_FOUND)
					.entity("no identical readings were found").build();

		return Response.ok(identicalReadings).build();
	}
	
	@GET
	@Path("couldBeIdentical/{tradId}/{startRank}/{endRank}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getCouldBeIdenticalReadings(@PathParam("tradId") String tradId,
			@PathParam("startRank") long startRank,
			@PathParam("endRank") long endRank) {

		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);
		ArrayList<Node> readings = new ArrayList<Node>();

		Node startNode = DatabaseService.getStartNode(tradId, db);
		if (startNode == null)
			return Response.status(Status.NOT_FOUND)
					.entity("Could not find tradition with this id").build();
		
		readings = getReadingsBetweenRanks(startRank, endRank, db, startNode);
				
		ArrayList<ReadingModel> couldBeIdenticalReadings = new ArrayList<ReadingModel>();

		couldBeIdenticalReadings = getCouldBeIdenticalAsList(readings);
		
		if (couldBeIdenticalReadings.size() == 0)
			return Response.status(Status.NOT_FOUND)
					.entity("no identical readings were found").build();

		return Response.ok(couldBeIdenticalReadings).build();
	}

	/**
	 * Makes seperate List for every group of Readings with identical text and different ranks 
	 * and send the list for further test
	 * @param readings
	 * @return
	 */
	private ArrayList<ReadingModel> getCouldBeIdenticalAsList(
			ArrayList<Node> readings) {
			ArrayList<ReadingModel> identicalReadings = new ArrayList<ReadingModel>();
			for (Node nodeA:readings) {
				ArrayList<Node> sameText = new ArrayList<Node>();
				sameText.add(nodeA);
				for (Node nodeB: readings) {
					if (nodeA.getProperty("dn15").toString()
							.equals(nodeB.getProperty("dn15").toString()) && !nodeA.equals(nodeB)
							&& !nodeA.getProperty("dn14").toString()
							.equals(nodeB.getProperty("dn14").toString())) {
						
					sameText.add(nodeB);
					
					}
				}
				if(sameText.size()>1)
					couldBeCheck(sameText,identicalReadings);
		}
		
		return identicalReadings;
	}

	private void couldBeCheck(ArrayList<Node> sameText,
			ArrayList<ReadingModel> identicalReadings) {
		
		for(int i = 0; i<sameText.size()-1; i++) {
			Node bigger;
			Node smaller;

			long rankA = (long) sameText.get(i).getProperty("dn14");
			long rankB = (long) sameText.get(i+1).getProperty("dn14");
			long delta,biggerRank,smallerRank;
			
			
			if(rankA<rankB) {
				bigger = sameText.get(i+1);
				smaller = sameText.get(i);
				delta = rankB-rankA;
				smallerRank = rankA;
				biggerRank = rankB;
			}
			else {
				bigger = sameText.get(i);
				smaller = sameText.get(i+1);
				delta = rankA-rankB;
				smallerRank = rankB;
				biggerRank = rankA;
			}
			
			long j = 0;
			while(j <= biggerRank) {
				Iterable<Relationship> rels = smaller.getRelationships(Direction.OUTGOING, ERelations.NORMAL);
				
				for(Relationship rel : rels) {
					j = (long)rel.getEndNode().getProperty("dn14");
				}
			}

		}
	}

	private ArrayList<Node> getReadingsBetweenRanks(long startRank, long endRank,
			GraphDatabaseService db, Node startNode) {
		ArrayList<Node> readings = new ArrayList<Node>();

		for (Node node : db.traversalDescription().depthFirst()
				.relationships(ERelations.NORMAL,Direction.OUTGOING)
				.uniqueness(Uniqueness.NODE_GLOBAL)
				.traverse(startNode).nodes()) {
			if((Long)node.getProperty("dn14")>startRank && (Long)node.getProperty("dn14")<endRank )
				readings.add(node);
			
		}
		return readings;
	}

	/**
	 * gets identical readings in a tradition between the given ranks
	 * 
	 * @param readingModels
	 *            list of all readings sorted according to rank
	 * @param startRank
	 * @param endRank
	 * @return list of the identical readings as readingModels
	 */
	private ArrayList<ReadingModel> getIdenticalReadingsAsList(
			ArrayList<ReadingModel> readingModels, long startRank, long endRank) {
		ArrayList<ReadingModel> identicalReadings = new ArrayList<ReadingModel>();

		for (int i = 0; i <=readingModels.size() - 2; i++) {
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

	/**
	 * compress two readings into one
	 * 
	 * @param tradId
	 * @param readId1
	 * @param readId2
	 * @return confirmation that the operation was completed
	 */
	@GET
	@Path("compress/{tradId}/{readId1}/{readId2}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response compressReadings(@PathParam("tradId") String tradId,
			@PathParam("readId1") long readId1,
			@PathParam("readId2") long readId2) {
		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);
		Node read1, read2;
		String errorMessage = "problem with a reading. Could not compress";
		Node startNode = DatabaseService.getStartNode(tradId, db);
		if (startNode == null)
			return Response.status(Status.NOT_FOUND)
					.entity("Could not find tradition with this id").build();
		read1 = DatabaseService.getReadingById(readId1, startNode, db);
		read2 = DatabaseService.getReadingById(readId2, startNode, db);

		try (Transaction tx = db.beginTx()) {
			if ((long) read1.getProperty("dn14") > (long) read2
					.getProperty("dn14"))
				swapReadings(read1, read2);

			tx.success();
		}

		if (canCompress(read1, read2, errorMessage, db)) {
			compress(read1, read2, db);
			return Response.ok("Successfully compressed readings").build();
		} else
			return Response.status(Status.NOT_FOUND).entity(errorMessage).build();
	}

	/**
	 * compress two readings
	 * 
	 * @param read1
	 *            the first reading
	 * @param read2
	 *            the second reading
	 */
	private void compress(Node read1, Node read2, GraphDatabaseService db) {
		try (Transaction tx = db.beginTx()) {
			String textRead1 = (String) read1.getProperty("dn15");
			String textRead2 = (String) read2.getProperty("dn15");
			read1.setProperty("dn15", textRead1 + " " + textRead2);

			Relationship from1to2 = getRealtionshipBetweenReadings(read1,
					read2, db);
			from1to2.delete();
			copyRelationships(read1, read2, db);
			read2.delete();
			tx.success();
		}
	}

	/**
	 * copy all NORMAL relationship from one node to another
	 * 
	 * @param read1
	 *            the node which receives the relationships
	 * @param read2
	 *            the node from which relationships are copied
	 */
	private void copyRelationships(Node read1, Node read2,
			GraphDatabaseService db) {
		for (Relationship tempRel2 : read2.getRelationships()) {
			Node tempNode = tempRel2.getOtherNode(read2);
			Relationship rel1 = read1.createRelationshipTo(tempNode,
					ERelations.NORMAL);
			for (String key : tempRel2.getPropertyKeys()) {
				rel1.setProperty(key, tempRel2.getProperty(key));
			}
			tempRel2.delete();
		}
	}

	/**
	 * checks if two readings could be compressed
	 * 
	 * @param read1
	 *            the first reading
	 * @param read2
	 *            the second reading
	 * @param message
	 *            the error message, in case the readings cannot be compressed
	 * @return true if ok to compress, false otherwise
	 */
	private boolean canCompress(Node read1, Node read2, String message,
			GraphDatabaseService db) {
		Iterable<Relationship> rel;
		try (Transaction tx = db.beginTx()) {
			rel = read2.getRelationships(ERelations.NORMAL);
			tx.success();
		}
		Iterator<Relationship> normalFromRead2 = rel.iterator();
		if (!normalFromRead2.hasNext()) {
			message = "second readings is not connected. could not compress.";
			return false;
		}
		Relationship from1to2 = getRealtionshipBetweenReadings(read1, read2, db);
		if (from1to2 == null) {
			message = "reading are not neighbors. could not compress.";
			return false;
		}

		if (hasNotNormalRealtionships(read1, db)
				|| hasNotNormalRealtionships(read2, db)) {
			message = "reading has other relations. could not compress";
			return false;
		}
		return true;
	}

	/**
	 * checks if a reading has relationships which are not NORMAL
	 * 
	 * @param read
	 *            the reading
	 * @param db
	 *            the data base
	 * @return true if it has, false otherwise
	 */
	private boolean hasNotNormalRealtionships(Node read, GraphDatabaseService db) {
		String type = "", normal = "";
		try (Transaction tx = db.beginTx()) {

			for (Relationship rel : read.getRelationships()) {
				type = rel.getType().name();
				normal = ERelations.NORMAL.toString();

				if (!type.equals(normal))
					return true;
			}
			tx.success();

			return false;
		}
	}

	/**
	 * get the normal relationship between two readings
	 * 
	 * @param read1
	 *            the first reading
	 * @param read2
	 *            the second reading
	 * @return the NORMAL relationship
	 */
	private Relationship getRealtionshipBetweenReadings(Node read1, Node read2,
			GraphDatabaseService db) {
		Relationship from1to2 = null;
		try (Transaction tx = db.beginTx()) {
			for (Relationship tempRel : read1.getRelationships()) {
				if (tempRel.getOtherNode(read1).equals(read2)) {
					from1to2 = tempRel;
				}
			}
			tx.success();
		}
		return from1to2;
	}

	private void swapReadings(Node read1, Node read2) {
		Node tempRead = read1;
		read1 = read2;
		read2 = tempRead;
	}
}