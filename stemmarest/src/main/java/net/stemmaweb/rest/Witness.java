package net.stemmaweb.rest;

import java.util.ArrayList;
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

import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.graphdb.traversal.Traverser;
import org.neo4j.graphdb.traversal.Uniqueness;

import Exceptions.DataBaseException;

/**
 * 
 * @author jakob/ido
 *
 **/
@Path("/witness")
public class Witness implements IResource {
	GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();


	/**
	 * find a requested witness in the data base and return it as a string
	 * 
	 * @param userId
	 *            : the id of the user who owns the witness
	 * @param traditionName
	 *            : the name of the tradition which the witness is in
	 * @param textId
	 *            : the id of the witness
	 * @return a witness as a string
	 * @throws DataBaseException
	 */
	@GET
	@Path("string/{tradId}/{textId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getWitnessAsPlainText(@PathParam("tradId") String tradId,
			@PathParam("textId") String textId) throws DataBaseException {

		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);
		String witnessAsText = "";
		final String WITNESS_ID = textId;
		ArrayList<ReadingModel> readingModels = new ArrayList<ReadingModel>();
		DatabaseService service = new DatabaseService(db);
		Node startNode = service.getStartNode(tradId);
		readingModels = getAllReadingsOfWitness(WITNESS_ID, startNode, db);

		for (ReadingModel readingModel : readingModels) {
			witnessAsText += readingModel.getDn15() + " ";
		}
		db.shutdown();
		if(witnessAsText.length()==0)
			return Response.status(Status.NOT_FOUND).build();
		return Response.status(Response.Status.OK)
				.entity("{\"tradId\":\"" + tradId + "\",\"textId\":\"" + textId + "\", \"text\":\"" + witnessAsText.trim() + "\"}").build();
	}

	/**
	 * find a requested witness in the data base and return it as a string
	 * according to define start and end readings
	 * 
	 * @param userId
	 *            : the id of the user who owns the witness
	 * @param traditionName
	 *            : the name of the tradition which the witness is in
	 * @param textId
	 *            : the id of the witness
	 * @return a witness as a string
	 * @throws DataBaseException
	 */
	@GET
	@Path("string/rank/{tradId}/{textId}/{startRank}/{endRank}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getWitnssAsPlainText(@PathParam("tradId") String tradId,
			@PathParam("textId") String textId,
			@PathParam("startRank") String startRank,
			@PathParam("endRank") String endRank) {
		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);
		String witnessAsText = "";
		final String WITNESS_ID = textId;
		ArrayList<ReadingModel> readingModels = new ArrayList<ReadingModel>();

		DatabaseService service = new DatabaseService(db);
		Node startNode = service.getStartNode(tradId);

		readingModels = getAllReadingsOfWitness(WITNESS_ID, startNode, db);

		int includeReading = 0;
		for (ReadingModel readingModel : readingModels) {
			if (readingModel.getDn14().equals(startRank))
				includeReading = 1;
			if (readingModel.getDn14().equals(endRank)) {
				witnessAsText += readingModel.getDn15();
				includeReading = 0;
			}
			if (includeReading == 1)
				witnessAsText += readingModel.getDn15() + " ";
		}
		db.shutdown();
		if (witnessAsText.equals(""))
			return Response.status(Status.NOT_FOUND).build();
		return Response.ok(witnessAsText.trim()).build();
	}

	/**
	 * finds a witness in data base and return it as a list of readings
	 * 
	 * @param userId
	 *            : the id of the user who owns the witness
	 * @param traditionName
	 *            : the name of the tradition which the witness is in
	 * @param textId
	 *            : the id of the witness
	 * @return a witness as a list of readings
	 * @throws DataBaseException
	 */
	@GET
	@Path("list/{tradId}/{textId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getWitnessAsReadings(@PathParam("tradId") String tradId,
			@PathParam("textId") String textId) {
		final String WITNESS_ID = textId;

		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);
		ArrayList<ReadingModel> readingModels = new ArrayList<ReadingModel>();

		DatabaseService service = new DatabaseService(db);
		Node startNode = service.getStartNode(tradId);
		if (startNode == null)
			return Response.status(Status.NOT_FOUND)
					.entity("Could not find tradition with this id").build();
		readingModels = getAllReadingsOfWitness(WITNESS_ID, startNode, db);
		if (readingModels.size() == 0)
			return Response.status(Status.NOT_FOUND)
					.entity("Could not found a witness with this id").build();
		db.shutdown();
		return Response.status(Status.OK).entity(readingModels).build();
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
	@Path("reading/next/{tradId}/{textId}/{readId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getNextReadingInWitness(@PathParam("tradId") String tradId,
			@PathParam("textId") String textId,
			@PathParam("readId") String readId) {

		final String WITNESS_ID = textId;
		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);

		DatabaseService service = new DatabaseService(db);
		Node startNode = service.getStartNode(tradId);
		if (startNode == null)
			return Response.status(Status.NOT_FOUND)
					.entity("Could not find tradition with this id").build();

		ReadingModel reading = getNextReading(WITNESS_ID, readId, startNode, db);

		return Response.ok(reading).build();
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
	@Path("reading/previous/{tradId}/{textId}/{readId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getPreviousReadingInWitness(@PathParam("tradId") String tradId,
			@PathParam("textId") String textId,
			@PathParam("readId") String readId) {

		final String WITNESS_ID = textId;
		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);

		DatabaseService service = new DatabaseService(db);
		Node startNode = service.getStartNode(tradId);
		if (startNode == null)
			return Response.status(Status.NOT_FOUND)
					.entity("Could not find tradition with this id").build();

		ReadingModel reading = getPreviousReading(WITNESS_ID, readId, startNode, db);

		return Response.ok(reading).build();
	}
	
	/**
	 * gets the Next reading to a given reading and a witness
	 * help method
	 * @param WITNESS_ID
	 * @param readId
	 * @param startNode
	 * @return the Next reading to that of the readId
	 */
	private ReadingModel getNextReading(String WITNESS_ID, String readId,
			Node startNode, GraphDatabaseService db) {
		Evaluator e = createEvalForWitness(WITNESS_ID);


		try (Transaction tx = db.beginTx()) {
			int stop = 0;
			for (Node node : db.traversalDescription().depthFirst()
					.relationships(Relations.NORMAL, Direction.OUTGOING)
					.evaluator(e).uniqueness(Uniqueness.NONE).traverse(startNode).nodes()) {
				if (stop == 1) {
					tx.success();
					return Reading.readingModelFromNode(node);
				}
				if (((String) node.getProperty("dn1")).equals(readId)) {
					stop = 1;
				}
			}
			db.shutdown();
			throw new DataBaseException("given readings not found");
		}
	}

	/**
	 * gets the Previous reading to a given reading and a witness
	 * help method
	 * @param WITNESS_ID
	 * @param readId
	 * @param startNode
	 * @return the Previous reading to that of the readId
	 */
	private ReadingModel getPreviousReading(final String WITNESS_ID, String readId,
			Node startNode,GraphDatabaseService db) {
		Node previousNode = null;

			Evaluator e = createEvalForWitness(WITNESS_ID);

		try (Transaction tx = db.beginTx()) {
			for (Node node : db.traversalDescription().depthFirst()
					.relationships(Relations.NORMAL, Direction.OUTGOING)
					.evaluator(e).uniqueness(Uniqueness.NONE).traverse(startNode).nodes()) {
			
				if (((String) node.getProperty("dn1")).equals(readId)) {
					tx.success();
					if (previousNode != null)
					return Reading.readingModelFromNode(previousNode);	
					else{
						db.shutdown();
						throw new DataBaseException("there is no previous reading to the given one");
					}						
				}
				previousNode = node;
			}
			db.shutdown();
			throw new DataBaseException("given readings not found");
		}
	}

	private Evaluator createEvalForWitness(final String WITNESS_ID) {
		Evaluator e = new Evaluator() {
			@Override
			public Evaluation evaluate(org.neo4j.graphdb.Path path) {

				if (path.length() == 0)
					return Evaluation.EXCLUDE_AND_CONTINUE;

				boolean includes = false;
				boolean continues = false;

				if(path.lastRelationship().hasProperty("lexemes"))
				{
					String[] arr = (String[]) path.lastRelationship().getProperty(
							"lexemes");
					for (String str : arr) {
						if (str.equals(WITNESS_ID)) {
							includes = true;
							continues = true;
						}
					}
				}
				return Evaluation.of(includes, continues);
			}
		};
		return e;
	}



	/**
	 * gets all readings of a single witness
	 * 
	 * @param WITNESS_ID
	 * @param startNode
	 *            : the start node of the tradition
	 * @return a list of the readings as readingModels
	 */
	private ArrayList<ReadingModel> getAllReadingsOfWitness(
			final String WITNESS_ID, Node startNode,GraphDatabaseService db) {
		ArrayList<ReadingModel> readingModels = new ArrayList<ReadingModel>();

		Evaluator e = createEvalForWitness(WITNESS_ID);
		try (Transaction tx = db.beginTx()) {

			for (Node startNodes : db.traversalDescription().depthFirst()
					.relationships(Relations.NORMAL, Direction.OUTGOING)
					.evaluator(e).uniqueness(Uniqueness.NONE).traverse(startNode).nodes()) {
				ReadingModel tempReading = Reading
						.readingModelFromNode(startNodes);

				readingModels.add(tempReading);
			}
			tx.success();
		}
		
		//remove the #END# node if it exists
		if(readingModels.get(readingModels.size()-1).getDn15().equals("#END#"))
			readingModels.remove(readingModels.size()-1);
		return readingModels;
	}
}
