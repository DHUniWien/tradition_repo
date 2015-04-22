package net.stemmaweb.rest;

import java.util.ArrayList;

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

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.traversal.Evaluator;
import org.neo4j.graphdb.traversal.Uniqueness;

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
			@PathParam("textId") String textId) {

		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);
		String witnessAsText = "";
		final String WITNESS_ID = textId;
		ArrayList<ReadingModel> readingModels = new ArrayList<ReadingModel>();
		Node startNode = DatabaseService.getStartNode(tradId,db);
		readingModels = getAllReadingsOfWitness(WITNESS_ID, startNode, db);

		for (ReadingModel readingModel : readingModels) {
			witnessAsText += readingModel.getDn15() + " ";
		}
		db.shutdown();
		if (witnessAsText.length() == 0)
			return Response.status(Status.NOT_FOUND).build();
		return Response.status(Response.Status.OK)
				.entity("{\"text\":\"" + witnessAsText.trim() + "\"}").build();
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
	public Response getWitnessAsPlainTextBetweenRanks(@PathParam("tradId") String tradId,
			@PathParam("textId") String textId,
			@PathParam("startRank") String startRank,
			@PathParam("endRank") String endRank) {
		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);
		String witnessAsText = "";
		final String WITNESS_ID = textId;
		ArrayList<ReadingModel> readingModels = new ArrayList<ReadingModel>();

		Node startNode = DatabaseService.getStartNode(tradId, db);

		readingModels = getAllReadingsOfWitness(WITNESS_ID, startNode, db);

		int includeReading = 0;
		for (ReadingModel readingModel : readingModels) {
			if (readingModel.getDn14()==Long.parseLong(startRank))
				includeReading = 1;
			if (readingModel.getDn14()==Long.parseLong(endRank)) {
				witnessAsText += readingModel.getDn15();
				includeReading = 0;
			}
			if (includeReading == 1)
				witnessAsText += readingModel.getDn15() + " ";
		}
		db.shutdown();
		if (witnessAsText.equals(""))
			return Response.status(Status.NOT_FOUND).build();
		return Response.status(Response.Status.OK)
				.entity("{\"text\":\"" + witnessAsText.trim() + "\"}").build();
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

		Node startNode = DatabaseService.getStartNode(tradId,db);
		if (startNode == null)
			return Response.status(Status.NOT_FOUND)
					.entity("Could not find tradition with this id").build();
		readingModels = getAllReadingsOfWitness(WITNESS_ID, startNode, db);
		if (readingModels.size() == 0)
			return Response.status(Status.NOT_FOUND)
.entity("Could not find a witness with this id").build();
		db.shutdown();
		return Response.status(Status.OK).entity(readingModels).build();
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
			final String WITNESS_ID, Node startNode, GraphDatabaseService db) {
		ArrayList<ReadingModel> readingModels = new ArrayList<ReadingModel>();
		EvaluatorService evaService = new EvaluatorService();
		Evaluator e = evaService.getEvalForWitness(WITNESS_ID);
		try (Transaction tx = db.beginTx()) {

			for (Node startNodes : db.traversalDescription().depthFirst()
					.relationships(ERelations.NORMAL, Direction.OUTGOING)
					.evaluator(e).uniqueness(Uniqueness.RELATIONSHIP_GLOBAL)
					.traverse(startNode).nodes()) {
				ReadingModel tempReading = new ReadingModel(startNodes);

				readingModels.add(tempReading);
			}
			tx.success();
		}

		// remove the #END# node if it exists
		if (readingModels.get(readingModels.size() - 1).getDn15()
				.equals("#END#"))
			readingModels.remove(readingModels.size() - 1);
		return readingModels;
	}
}
