package net.stemmaweb.rest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import net.stemmaweb.model.DuplicateModel;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.EvaluatorService;

import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.traversal.Evaluator;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.Uniqueness;

import Exceptions.DataBaseException;

@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
@Path("reading")
public class Reading implements IResource {
	private String errorMessage;
	GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();

	public static Node copyReadingProperties(Node oldReading, Node newReading) {
		for (int i = 0; i < 16; i++) {
			String key = "dn" + i;
			if (oldReading.hasProperty(key))
				newReading.setProperty(key, oldReading.getProperty(key));
		}
		newReading.addLabel(Nodes.WORD);
		return newReading;
	}

	/**
	 * Returns a single reading in a specific tradition.
	 * 
	 * @param tradId
	 * @param readId
	 * @return
	 */
	@GET
	@Path("reading/{tradId}/{readId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getReading(@PathParam("tradId") String tradId, @PathParam("readId") long readId) {
		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);

		ReadingModel reading = null;
		Node readingNode;

		try (Transaction tx = db.beginTx()) {
			try {
				readingNode = db.getNodeById(readId);
			} catch (Exception e) {
				db.shutdown();
				return Response.status(Status.NOT_FOUND).entity("no reading with this id found").build();
			}
			reading = new ReadingModel(readingNode);

			tx.success();
		} catch (Exception e) {
			return Response.status(Status.NOT_FOUND).entity("no reading with this id found").build();
		} finally {
			db.shutdown();
		}

		return Response.ok(reading).build();
	}

	/**
	 * Duplicates a reading in a specific tradition. Opposite of merge
	 * 
	 * @param tradId
	 * @param duplicateModel
	 * @return
	 */
	@POST
	@Path("duplicate/{tradId}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response duplicateReading(@PathParam("tradId") String tradId, DuplicateModel duplicateModel) {
		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);

		Node originalReading = null;

		try (Transaction tx = db.beginTx()) {
			List<Long> readings = duplicateModel.getReadings();
			for (Long readId : readings) {
				try {
					originalReading = db.getNodeById(readId);
				} catch (NotFoundException e) {
					db.shutdown();
					return Response.status(Status.NOT_FOUND).entity("no reading with this id found: " + readId).build();
				}

				if (!canBeDuplicated(originalReading, duplicateModel.getWitnesses())) {
					db.shutdown();
					return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Duplication not possible").build();
				}

				duplicateReading(duplicateModel.getWitnesses(), originalReading, db.createNode());
			}

			tx.success();
		} catch (Exception e) {
			return Response.status(Status.NOT_FOUND).entity(e.getMessage()).build();
		} finally {
			db.shutdown();
		}
		return Response.ok("Successfully duplicated readings").build();
	}

	private void duplicateReading(List<String> newWitnesses, Node originalReading, Node addedReading) {
		// copy reading properties to newly added reading
		addedReading = Reading.copyReadingProperties(originalReading, addedReading);

		// copy relationships
		for (Relationship originalRel : originalReading.getRelationships(ERelations.RELATIONSHIP)) {
			Relationship newRel = addedReading.createRelationshipTo(originalRel.getOtherNode(originalReading),
					ERelations.RELATIONSHIP);
			for (String key : originalRel.getPropertyKeys())
				newRel.setProperty(key, originalRel.getProperty(key));
		}

		// add witnesses to relationships
		// Incoming
		for (Relationship originalRelationship : originalReading
				.getRelationships(ERelations.NORMAL, Direction.INCOMING))
			handleWitnesses(newWitnesses, originalRelationship, originalRelationship.getStartNode(), addedReading);
		// Outgoing
		for (Relationship originalRelationship : originalReading
				.getRelationships(ERelations.NORMAL, Direction.OUTGOING))
			handleWitnesses(newWitnesses, originalRelationship, addedReading, originalRelationship.getEndNode());
	}

	private void handleWitnesses(List<String> newWitnesses, Relationship originalRel, Node originNode,
			Node targetNode) {
		String[] oldWitnesses = (String[]) originalRel.getProperty("lexemes");
		// if oldWitnesses only contains one witness and this one should be
		// duplicated, create new relationship for addedReading and delete
		// the one from the originalReading
		if (oldWitnesses.length == 1) {
			if (newWitnesses.contains(oldWitnesses[0])) {
				Relationship newRel = originNode.createRelationshipTo(targetNode, ERelations.NORMAL);
				newRel.setProperty("lexemes", oldWitnesses);
				originalRel.delete();
			}
			// if oldWitnesses contains more than one witnesses, create new
			// relationship and add those witnesses which should be duplicated
		} else {
			// add only those witnesses to oldWitnesses which are
			// not in newWitnesses
			ArrayList<String> remainingWitnesses = new ArrayList<String>();
			ArrayList<String> stayingWitnesses = new ArrayList<String>();
			for (String oldWitness : oldWitnesses) {
				if (!newWitnesses.contains(oldWitness))
					stayingWitnesses.add(oldWitness);
				else
					remainingWitnesses.add(oldWitness);
			}

			Relationship addedRelationship = originNode.createRelationshipTo(targetNode, ERelations.NORMAL);
			addedRelationship.setProperty("lexemes", remainingWitnesses.toArray(new String[remainingWitnesses.size()]));

			if (stayingWitnesses.isEmpty())
				originalRel.delete();
			else
				originalRel.setProperty("lexemes", stayingWitnesses.toArray(new String[stayingWitnesses.size()]));
		}
	}

	private boolean canBeDuplicated(Node originalReading, List<String> witnesses) {
		if (witnesses.isEmpty())
			return false;

		// test if there are witnesses to be duplicated for which no witnesses
		// in the readings relationships exist
		List<String> allWitnesses = new LinkedList<String>();
		String[] currentWitnesses;
		for (Relationship relationship : originalReading.getRelationships(ERelations.NORMAL)) {
			currentWitnesses = (String[]) relationship.getProperty("lexemes");
			for (String currentWitness : currentWitnesses)
				if (!allWitnesses.contains(currentWitness))
					allWitnesses.add(currentWitness);
		}
		for (String newWitness : witnesses)
			if (!allWitnesses.contains(newWitness))
				return false;

		// the reading must be in at least two witnesses
		if (allWitnesses.size() < 2)
			return false;

		return true;
	}

	/**
	 * Merges two readings into one single reading in a specific tradition.
	 * Opposite of duplicate
	 * 
	 * @param tradId
	 * @param firstReadId
	 * @param secondReadId
	 * @return
	 */
	@POST
	@Path("merge/{tradId}/{firstReadId}/{secondReadId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response mergeReadings(@PathParam("tradId") String tradId, @PathParam("firstReadId") long firstReadId,
			@PathParam("secondReadId") long secondReadId) {
		Node stayingReading = null;
		Node deletingReading = null;

		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);

		try (Transaction tx = db.beginTx()) {
			try {
				stayingReading = db.getNodeById(firstReadId);
				deletingReading = db.getNodeById(secondReadId);
			} catch (Exception e) {
				db.shutdown();
				return Response.status(Status.NOT_FOUND).entity("no readings with this ids found").build();
			}

			if (!stayingReading.getProperty("dn15").toString()
					.equalsIgnoreCase(deletingReading.getProperty("dn15").toString())) {
				db.shutdown();
				return Response.status(Status.INTERNAL_SERVER_ERROR)
						.entity("Readings to be merged do not contain the same text").build();
			}

			if (doReadingsBelongToSameWitness(stayingReading, deletingReading)) {
				db.shutdown();
				return Response.status(Status.INTERNAL_SERVER_ERROR)
						.entity("Readings to be merged belong to the same witness").build();
			}

			if (containClassTwoRelationships(stayingReading, deletingReading)) {
				db.shutdown();
				return Response.status(Status.INTERNAL_SERVER_ERROR)
						.entity("Readings to be merged cannot contain class 2 relationships (transposition / repetition)")
						.build();
			}

			if (containClassOneRelationships(stayingReading, deletingReading)) {
				// graph has to stay acyclic
				mergeReadings(stayingReading, deletingReading);
				if (isCyclic())
					tx.failure();
			}

			mergeReadings(stayingReading, deletingReading);

			tx.success();
		} catch (Exception e) {
			return Response.status(Status.NOT_FOUND).entity(e.getMessage()).build();
		} finally {
			db.shutdown();
		}

		return Response.ok("Successfully merged readings").build();
	}

	private boolean isCyclic() {
		// TODO Auto-generated method stub
		return true;
	}

	private void mergeReadings(Node stayingReading, Node deletingReading) {
		copyRelationships(stayingReading, deletingReading);
		addRelationshipsToStayingReading(stayingReading, deletingReading);
		deletingReading.delete();
		copyWitnesses(stayingReading);
	}

	private boolean containClassOneRelationships(Node stayingReading, Node deletingReading) {
		for (Relationship stayingRel : stayingReading.getRelationships(ERelations.RELATIONSHIP))
			if (stayingRel.getOtherNode(stayingReading).equals(deletingReading))
				if (!stayingRel.getProperty("de11").equals("transposition")
						&& !stayingRel.getProperty("de11").equals("repetition"))
					return true;
		return false;
	}

	private boolean containClassTwoRelationships(Node stayingReading, Node deletingReading) {
		for (Relationship stayingRel : stayingReading.getRelationships(ERelations.RELATIONSHIP))
			if(stayingRel.getOtherNode(stayingReading).equals(deletingReading))
				if (stayingRel.getProperty("de11").equals("transposition")
						|| stayingRel.getProperty("de11").equals("repetition"))
					return true;
		return false;
	}

	private void copyWitnesses(Node stayingReading) {
		for (Relationship firstRel : stayingReading.getRelationships(ERelations.NORMAL)) {
			for (Relationship secondRel : stayingReading.getRelationships(ERelations.NORMAL)) {
				if (!firstRel.equals(secondRel))
				if (firstRel.getOtherNode(stayingReading).equals(secondRel.getOtherNode(stayingReading))) {
					// get Witnesses
					String[] stayingReadingWitnesses = (String[]) firstRel.getProperty("lexemes");
					String[] deletingReadingWitnesses = (String[]) secondRel.getProperty("lexemes");

					// combine witness lists into one list
					String[] combinedWitnesses = new String[stayingReadingWitnesses.length
							+ deletingReadingWitnesses.length];
					for (int i = 0; i < stayingReadingWitnesses.length; i++)
						combinedWitnesses[i] = stayingReadingWitnesses[i];
					for (int i = 0; i < deletingReadingWitnesses.length; i++)
						combinedWitnesses[stayingReadingWitnesses.length + i] = deletingReadingWitnesses[i];
					firstRel.setProperty("lexemes", combinedWitnesses);
					secondRel.delete();
				}
			}
		}
	}

	private boolean doReadingsBelongToSameWitness(Node stayingReading, Node deletingReading) {
		// write all witnesses of the staying reading into ArrayList
		Iterable<Relationship> stayingRels = stayingReading.getRelationships(ERelations.NORMAL);
		ArrayList<String> stayingWitnesses = new ArrayList<String>();
		for (Relationship stayingRel : stayingRels) {
			for (String witness : (String[]) stayingRel.getProperty("lexemes"))
				stayingWitnesses.add(witness);
		}

		// check if one of the witnesses of the reading to be deleted is already
		// present in the ArrayList
		Iterable<Relationship> deletingRels = deletingReading.getRelationships(ERelations.NORMAL);
		for (Relationship deletingRel : deletingRels)
			for (String witness : (String[]) deletingRel.getProperty("lexemes"))
				if (stayingWitnesses.contains(witness))
					return true;
		return false;
	}

	private void addRelationshipsToStayingReading(Node stayingReading, Node deletingReading) {
		// copy relationships from deletingReading to stayingReading
		Iterable<Relationship> rels = deletingReading.getRelationships(ERelations.RELATIONSHIP, Direction.OUTGOING);
		for (Relationship rel : rels) {
			stayingReading.createRelationshipTo(rel.getEndNode(), ERelations.RELATIONSHIP);
			rel.delete();
		}
		rels = deletingReading.getRelationships(ERelations.RELATIONSHIP, Direction.INCOMING);
		for (Relationship rel : rels) {
			rel.getStartNode().createRelationshipTo(stayingReading, ERelations.RELATIONSHIP);
			rel.delete();
		}
	}

	/**
	 * Splits up a single reading into several ones in a specific tradition.
	 * Opposite of compress
	 * 
	 * @param tradId
	 * @param readId
	 * @return
	 */
	@POST
	@Path("split/{tradId}/{readId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response splitReading(@PathParam("tradId") String tradId, @PathParam("readId") long readId) {
		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);

		Node originalReading = null;

		try (Transaction tx = db.beginTx()) {
			try {
				originalReading = db.getNodeById(readId);
			} catch (Exception e) {
				db.shutdown();
				return Response.status(Status.NOT_FOUND).entity("no reading with this id found").build();
			}

			// splitting of reading happens here
			String oldText = originalReading.getProperty("dn15").toString();
			String[] splittedWords = oldText.split("\\s+");
			if (splittedWords.length < 2) {
				db.shutdown();
				return Response.status(Status.INTERNAL_SERVER_ERROR)
						.entity("A reading to be splitted has to contain at least 2 words").build();
			}

			originalReading.setProperty("dn15", splittedWords[0]);

			Node newReading = null;

			for (int i = 1; i < splittedWords.length; i++) {
				newReading = db.createNode();

				// is this assignment necessary or does that function
				// otherwise as well in this transaction?
				newReading = Reading.copyReadingProperties(originalReading, newReading);
				newReading.setProperty("dn15", splittedWords[i]);
				Long previousRank = (Long) originalReading.getProperty("dn14");
				newReading.setProperty("dn14", previousRank + 1);

				ArrayList<String> allWitnesses = new ArrayList<String>();
				Iterable<Relationship> rels = originalReading.getRelationships(ERelations.NORMAL, Direction.OUTGOING);
				for (Relationship relationship : rels) {
					String[] witnesses = (String[]) relationship.getProperty("lexemes");
					for (int j = 0; j < witnesses.length; j++)
						allWitnesses.add(witnesses[j]);

					newReading.createRelationshipTo(relationship.getEndNode(), ERelations.NORMAL);
					relationship.delete();
				}

				Relationship relationship = originalReading.createRelationshipTo(newReading, ERelations.NORMAL);
				relationship.setProperty("lexemes", allWitnesses.toArray(new String[allWitnesses.size()]));
			}
			tx.success();
		} catch (Exception e) {
			return Response.status(Status.NOT_FOUND).entity(e.getMessage()).build();
		} finally {
			db.shutdown();
		}
		return Response.ok("Successfully split up reading").build();
	}

	/**
	 * gets the next readings from a given readings in the same witness
	 * @param textId
	 *            : witness id
	 * @param readId
	 *            : reading id
	 * 
	 * @return the requested reading
	 */
	@GET
	@Path("next/{textId}/{readId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getNextReadingInWitness(@PathParam("textId") String textId,
			@PathParam("readId") long readId) {

		final String WITNESS_ID = textId;
		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);
		EvaluatorService evaService = new EvaluatorService();
		Evaluator wintessEvaluator = evaService.getEvalForWitness(WITNESS_ID);

		try (Transaction tx = db.beginTx()) {
			Node read = db.getNodeById(readId);

			for (Node node : db.traversalDescription().depthFirst()
					.relationships(ERelations.NORMAL, Direction.OUTGOING)
					.evaluator(wintessEvaluator)
					.evaluator(Evaluators.toDepth(1))
					.uniqueness(Uniqueness.NONE).traverse(read).nodes()) {
				db.shutdown();
				if (!new ReadingModel(node).getDn15()
						.equals("#END#"))
					return Response.ok(new ReadingModel(node))
							.build();
				else
					return Response
							.status(Status.NOT_FOUND)
							.entity("this was the last reading of this witness")
							.build();
			}
		}
		db.shutdown();
		throw new DataBaseException("given readings not found");
	}

	/**
	 * gets the next readings from a given readings in the same witness
	 * @param textId
	 *            : witness id
	 * @param readId
	 *            : reading id
	 * 
	 * @return the requested reading
	 */
	@GET
	@Path("/previous/{textId}/{readId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getPreviousReadingInWitness(
			@PathParam("textId") String textId,
			@PathParam("readId") long readId) {

		final String WITNESS_ID = textId;
		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);		
		EvaluatorService evaService = new EvaluatorService();
		Evaluator wintessEvaluator = evaService.getEvalForWitness(WITNESS_ID);

		try (Transaction tx = db.beginTx()) {
			Node read = db.getNodeById(readId);

			for (Node node : db.traversalDescription().depthFirst()
					.relationships(ERelations.NORMAL, Direction.INCOMING)
					.evaluator(wintessEvaluator)
					.evaluator(Evaluators.toDepth(1))
					.uniqueness(Uniqueness.NONE).traverse(read).nodes()) {
				db.shutdown();
				if (!new ReadingModel(node).getDn15()
						.equals("#START#"))
					return Response.ok(new ReadingModel(node))
							.build();
				else
					return Response
							.status(Status.NOT_FOUND)
							.entity("there is no previous reading to this reading")
							.build();
			}
		}
		db.shutdown();
		throw new DataBaseException("given readings not found");
	}

	@GET
	@Path("/{tradId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAllReadings(@PathParam("tradId") String tradId) {

		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);
		ArrayList<ReadingModel> readingModels = new ArrayList<ReadingModel>();

		Node startNode = DatabaseService.getStartNode(tradId, db);
		if (startNode == null) {
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
				ReadingModel tempReading = new ReadingModel(node);
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
					ReadingModel tempReading = new ReadingModel(node);
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

		ArrayList<List> identicalReadings = new ArrayList<List>();
		identicalReadings = getIdenticalReadingsAsList(readingModels,
				startRank, endRank);

		Boolean isEmpty = true;
		for (List list : identicalReadings) {
			if (list.size() > 0)
				isEmpty = false;
		}
		if (isEmpty)
			return Response.status(Status.NOT_FOUND)
					.entity("no identical readings were found").build();

		return Response.ok(identicalReadings).build();
	}

	/**
	 * Returns a list of a list of readingModels with could be one the same rank
	 * without problems
	 * 
	 * @param tradId
	 * @param startRank
	 * @param endRank
	 * @return
	 */
	@GET
	@Path("couldBeIdentical/{tradId}/{startRank}/{endRank}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getCouldBeIdenticalReadings(
			@PathParam("tradId") String tradId,
			@PathParam("startRank") long startRank,
			@PathParam("endRank") long endRank) {

		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);

		Node startNode = DatabaseService.getStartNode(tradId, db);
		if (startNode == null)
			return Response.status(Status.NOT_FOUND)
					.entity("Could not find tradition with this id").build();

		ArrayList<Node> questionedReadings = getReadingsBetweenRanks(startRank,
				endRank, db, startNode);

		ArrayList<ArrayList<ReadingModel>> couldBeIdenticalReadings = getCouldBeIdenticalAsList(
				questionedReadings, db);

		if (couldBeIdenticalReadings.size() == 0)
			return Response.status(Status.NOT_FOUND)
					.entity("no identical readings were found").build();

		return Response.ok(couldBeIdenticalReadings).build();
	}

	/**
	 * Makes separate List for every group of Readings with identical text and
	 * different ranks and send the list for further test
	 * 
	 * @param questionedReadings
	 * @param db
	 * @return
	 */
	private ArrayList<ArrayList<ReadingModel>> getCouldBeIdenticalAsList(
			ArrayList<Node> questionedReadings, GraphDatabaseService db) {

		ArrayList<ArrayList<ReadingModel>> couldBeIdenticalReadings = new ArrayList<ArrayList<ReadingModel>>();

		try (Transaction tx = db.beginTx()) {

			for (Node nodeA : questionedReadings) {
				ArrayList<Node> sameText = new ArrayList<Node>();
				for (Node nodeB : questionedReadings) {
					if (nodeA.getProperty("dn15").toString()
							.equals(nodeB.getProperty("dn15").toString())
							&& !nodeA.equals(nodeB)
							&& !nodeA
									.getProperty("dn14")
									.toString()
									.equals(nodeB.getProperty("dn14")
											.toString())) {
						sameText.add(nodeB);
						sameText.add(nodeA);
					}
				}
				if (sameText.size() > 0)
					couldBeIdenticalCheck(sameText, couldBeIdenticalReadings,
							db);
			}
		} finally {
			db.shutdown();
		}

		return couldBeIdenticalReadings;
	}

	/**
	 * Adds all the words that could be on the same rank to the result list
	 * 
	 * @param sameText
	 * @param couldBeIdenticalReadings
	 * @param db
	 */
	private void couldBeIdenticalCheck(ArrayList<Node> sameText,
			ArrayList<ArrayList<ReadingModel>> couldBeIdenticalReadings,
			GraphDatabaseService db) {

		ArrayList<ReadingModel> couldBeIdentical = new ArrayList<ReadingModel>();
		try (Transaction tx = db.beginTx()) {

			for (int i = 0; i < sameText.size() - 1; i++) {
				Node biggerRankNode;
				Node smallerRankNode;
				long rankA = (long) sameText.get(i).getProperty("dn14");
				long rankB = (long) sameText.get(i + 1).getProperty("dn14");
				long biggerRank, smallerRank;

				if (rankA < rankB) {
					biggerRankNode = sameText.get(i + 1);
					smallerRankNode = sameText.get(i);
					smallerRank = rankA;
					biggerRank = rankB;
				} else {
					biggerRankNode = sameText.get(i);
					smallerRankNode = sameText.get(i + 1);
					smallerRank = rankB;
					biggerRank = rankA;
				}

				long rank = 0;
				boolean gotOne = false;

				Iterable<Relationship> rels = smallerRankNode.getRelationships(
						Direction.OUTGOING, ERelations.NORMAL);

				for (Relationship rel : rels) {
					rank = (long) rel.getEndNode().getProperty("dn14");
					if (rank <= biggerRank) {
						gotOne = true;
						break;
					}
				}

				if (gotOne) {
					rank = 0;
					gotOne = false;

					Iterable<Relationship> rels2 = biggerRankNode
							.getRelationships(Direction.INCOMING,
									ERelations.NORMAL);

					for (Relationship rel : rels2) {
						rank = (long) rel.getStartNode().getProperty("dn14");
						if (rank >= smallerRank) {
							gotOne = true;
							break;
						}
					}
				}
				if (!gotOne) {
					if (!couldBeIdentical.contains(new ReadingModel(smallerRankNode)))
						couldBeIdentical.add(new ReadingModel(smallerRankNode));
					if (!couldBeIdentical.contains(new ReadingModel(biggerRankNode)))
						couldBeIdentical.add(new ReadingModel(biggerRankNode));
				}

			}
			if(couldBeIdentical.size()>0)
				couldBeIdenticalReadings.add(couldBeIdentical);
		}

		finally {
			db.shutdown();
		}

	}

	private ArrayList<Node> getReadingsBetweenRanks(long startRank,
			long endRank, GraphDatabaseService db, Node startNode) {
		ArrayList<Node> readings = new ArrayList<Node>();

		try (Transaction tx = db.beginTx()) {

			for (Node node : db.traversalDescription().breadthFirst()
					.relationships(ERelations.NORMAL, Direction.OUTGOING)
					.uniqueness(Uniqueness.NODE_GLOBAL).traverse(startNode)
					.nodes()) {
				if ((Long) node.getProperty("dn14") > startRank
						&& (Long) node.getProperty("dn14") < endRank)
					readings.add(node);

			}
		} finally {
			db.shutdown();
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
	private ArrayList<List> getIdenticalReadingsAsList(
			ArrayList<ReadingModel> readingModels, long startRank, long endRank) {
		ArrayList<List> identicalReadingsList = new ArrayList<List>();

		for (int i = 0; i <= readingModels.size() - 2; i++) {
			while (readingModels.get(i).getDn14() == readingModels.get(i + 1)
					.getDn14() && i + 1 < readingModels.size()) {
				ArrayList<ReadingModel> identicalReadings = new ArrayList<ReadingModel>();

				if (readingModels.get(i).getDn15()
						.equals(readingModels.get(i + 1).getDn15())
						&& readingModels.get(i).getDn14() < endRank
						&& readingModels.get(i).getDn14() > startRank) {
					identicalReadings.add(readingModels.get(i));
					identicalReadings.add(readingModels.get(i + 1));
				}
				identicalReadingsList.add(identicalReadings);
				i++;
			}
		}
		return identicalReadingsList;
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
		errorMessage = "problem with a reading. Could not compress";		

		try (Transaction tx = db.beginTx()) {
			read1 = db.getNodeById(readId1);
			read2 = db.getNodeById(readId2);
			if ((long) read1.getProperty("dn14") > (long) read2
					.getProperty("dn14"))
				swapReadings(read1, read2);

			tx.success();
		}

		if (canCompress(read1, read2, db)) {
			compress(read1, read2, db);
			return Response.ok("Successfully compressed readings").build();
		} else
			return Response.status(Status.NOT_FOUND).entity(errorMessage)
					.build();
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
			copyRelationships(read1, read2);
			read2.delete();
			tx.success();
		}
	}

	/**
	 * copy all NORMAL relationship from one node to another
	 * IMPORTANT: when called needs to be inside a try-catch
	 * 
	 * @param read1
	 *            the node which receives the relationships
	 * @param read2
	 *            the node from which relationships are copied
	 */
	private void copyRelationships(Node read1, Node read2) {
		for (Relationship tempRel : read2.getRelationships(Direction.OUTGOING)) {
			Node tempNode = tempRel.getOtherNode(read2);
			Relationship rel1 = read1.createRelationshipTo(tempNode,
					ERelations.NORMAL);
			
			for (String key : tempRel.getPropertyKeys()) {
				rel1.setProperty(key, tempRel.getProperty(key));
			}
			tempRel.delete();
		}
		
		for (Relationship tempRel : read2.getRelationships(Direction.INCOMING)) {
			Node tempNode = tempRel.getOtherNode(read2);
			Relationship rel1 = tempNode.createRelationshipTo(read1,
					ERelations.NORMAL);
			
			for (String key : tempRel.getPropertyKeys()) {
				rel1.setProperty(key, tempRel.getProperty(key));
			}
			tempRel.delete();
		}
	}

	/**
	 * checks if two readings could be compressed
	 * 
	 * @param read1
	 *            the first reading
	 * @param read2
	 *            the second reading
	 * @return true if ok to compress, false otherwise
	 */
	private boolean canCompress(Node read1, Node read2, GraphDatabaseService db) {
		Iterable<Relationship> rel;
		try (Transaction tx = db.beginTx()) {
			rel = read2.getRelationships(ERelations.NORMAL);
			tx.success();
		}
		Iterator<Relationship> normalFromRead2 = rel.iterator();
		if (!normalFromRead2.hasNext()) {
			errorMessage = "second readings is not connected. could not compress";
			return false;
		}
		Relationship from1to2 = getRealtionshipBetweenReadings(read1, read2, db);
		if (from1to2 == null) {
			errorMessage = "reading are not neighbors. could not compress";
			return false;
		}

		if (hasNotNormalRealtionships(read1, db)
				|| hasNotNormalRealtionships(read2, db)) {
			errorMessage = "reading has other relations. could not compress";
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