package net.stemmaweb.rest;

import java.util.ArrayList;
import java.util.Iterator;

import javax.ws.rs.Consumes;
import javax.ws.rs.Path;
import javax.ws.rs.GET;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.WitnessModel;
import net.stemmaweb.services.DbPathProblemService;

import org.codehaus.jettison.json.JSONArray;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;
import org.neo4j.graphdb.Transaction;

import Exceptions.DataBaseException;
import Exceptions.UrlPathException;

import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;

/**
 * 
 * @author jakob/ido
 *
 **/
@Path("/witness")
public class Witness {
	public static final String DB_PATH = "database";
	private GraphDatabaseService db = new GraphDatabaseFactory()
			.newEmbeddedDatabase(DB_PATH);

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
	@Path("{textId}/{userId}/{traditionName}")
	@Produces("text/plain")
	public String getWitnssAsPlainText(@PathParam("userId") String userId,
			@PathParam("traditionName") String traditionName,
			@PathParam("textId") String textId) throws DataBaseException {
		String witnessAsText = "";
		final String WITNESS_ID = textId;
		ArrayList<ReadingModel> readingModels = new ArrayList<ReadingModel>();

		Node witnessNode = getFirstWitnessNode(userId, traditionName, textId);
		readingModels = getNodesOfWitness(WITNESS_ID, witnessNode);

		for (ReadingModel readingModel : readingModels) {
			witnessAsText += readingModel.getText() + " ";
		}
		return witnessAsText.trim();
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
	@Path("{textId}/{userId}/{traditionName}/{startRank}/{endRank}")
	@Produces("text/plain")
	public Object getWitnssAsPlainText(@PathParam("userId") String userId,
			@PathParam("traditionName") String traditionName,
			@PathParam("textId") String textId,
			@PathParam("startRank") String startRank,
			@PathParam("endRank") String endRank) {

		int endRankAsInt;
		int startRankAsInt;
		try {
			startRankAsInt = Integer.parseInt(startRank);
			endRankAsInt = Integer.parseInt(endRank);
		} catch (NumberFormatException e) {
			throw new UrlPathException(
					"the requested rank range is not defined by numbers");
		}

		if (startRankAsInt >= endRankAsInt)
			throw new UrlPathException(
					"the start rank is not lower than the end rank");

		String witnessAsText = "";
		final String WITNESS_ID = textId;
		ArrayList<ReadingModel> readingModels = new ArrayList<ReadingModel>();

		Node witnessNode = getFirstWitnessNode(userId, traditionName, textId);

		readingModels = getNodesOfWitness(WITNESS_ID, witnessNode);

		for (ReadingModel readingModel : readingModels) {
			if (readingModel.getRankAsInt() >= startRankAsInt
					&& readingModel.getRankAsInt() <= endRankAsInt)
				witnessAsText += readingModel.getText() + " ";
		}
		return witnessAsText.trim();
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
	@Path("{textId}/{userId}/{traditionName}")
	@Produces(MediaType.APPLICATION_JSON)
	public String getWitnessAsReadings(@PathParam("userId") String userId,
			@PathParam("traditionName") String traditionName,
			@PathParam("textId") String textId) throws DataBaseException {
		final String WITNESS_ID = textId;

		ArrayList<ReadingModel> readingModels = new ArrayList<ReadingModel>();

		Node witnessNode = getFirstWitnessNode(userId, traditionName, textId);
		readingModels = getNodesOfWitness(WITNESS_ID, witnessNode);
		JSONArray ar = new JSONArray(readingModels);

		return ar.toString();
	}

	/**
	 * gets the "start" node of a witness
	 * 
	 * @param traditionName
	 * @param userId
	 * @param textId
	 *            : the witness id
	 * @return the start node of a witness
	 */
	private Node getFirstWitnessNode(String userId, String traditionName,
			String textId) {
		ExecutionEngine engine = new ExecutionEngine(db);
		DbPathProblemService problemFinder = new DbPathProblemService(db);
		Node witnessNode;

		ExecutionResult result;

		/**
		 * this quarry gets the "Start" node of the witness
		 */
		String witnessQuarry = "match (user:USER {id:'" + userId
				+ "'})--(tradition:TRADITION {name:'" + traditionName
				+ "'})--(w:WORD  {text:'#START#'}) return w";

		try (Transaction tx = db.beginTx()) {

			result = engine.execute(witnessQuarry);
			Iterator<Node> nodes = result.columnAs("w");

			if (!nodes.hasNext())
				throw new DataBaseException(problemFinder.findPathProblem(
						userId, traditionName, textId));
			else
				witnessNode = nodes.next();

			if (nodes.hasNext())
				throw new DataBaseException(
						"this path leads to more than one witness");
		}
		return witnessNode;
	}

	private ArrayList<ReadingModel> getNodesOfWitness(final String WITNESS_ID,
			Node witnessNode) {
		ArrayList<ReadingModel> readingModels = new ArrayList<ReadingModel>();

		Evaluator e = new Evaluator() {
			@Override
			public Evaluation evaluate(org.neo4j.graphdb.Path path) {

				if (path.length() == 0)
					return Evaluation.EXCLUDE_AND_CONTINUE;

				boolean includes = path.lastRelationship()
						.getProperty("lexemes").equals(WITNESS_ID);
				boolean continues = path.lastRelationship()
						.getProperty("lexemes").equals(WITNESS_ID);

				return Evaluation.of(includes, continues);
			}
		};

		try (Transaction tx = db.beginTx()) {

			for (Node witnessNodes : db.traversalDescription().depthFirst()
					.relationships(Relations.NORMAL, Direction.OUTGOING)
					.evaluator(e).traverse(witnessNode).nodes()) {
				ReadingModel tempReading = extractReadingFromNode(witnessNodes);

				readingModels.add(tempReading);
			}
			if (readingModels.isEmpty())
				throw new DataBaseException("this witness is empty");
		}
		return readingModels;
	}

	private ReadingModel extractReadingFromNode(Node witnessNodes) {
		ReadingModel tempReading = new ReadingModel();
		if (witnessNodes.hasProperty("text"))
			tempReading.setText((String) witnessNodes.getProperty("text"));
		if (witnessNodes.hasProperty("id"))
			tempReading.setId((String) witnessNodes.getProperty("id"));
		if (witnessNodes.hasProperty("language"))
			tempReading.setLanguage((String) witnessNodes
					.getProperty("language"));
		if (witnessNodes.hasProperty("rank"))
			tempReading.setRank((String) witnessNodes.getProperty("rank"));
		if (witnessNodes.hasProperty("is_common"))
			tempReading.setIs_common((String) witnessNodes
					.getProperty("is_common"));
		return tempReading;
	}

	public void setDb(GraphDatabaseService graphDb) {
		db = graphDb;
	}
	
	@GET
	@Path("readings/{tradId}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getReadings(@PathParam("tradId") String tradId) {
		
		ArrayList<ReadingModel> readList= new ArrayList<ReadingModel>();

		GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();
		GraphDatabaseService db= dbFactory.newEmbeddedDatabase(DB_PATH);
		
		ExecutionEngine engine = new ExecutionEngine(db);
	
		
		try (Transaction tx = db.beginTx()) 
		{
			Node traditionNode = null;
			Node startNode = null;
    		ExecutionResult result = engine.execute("match (n:TRADITION {id: '"+ tradId +"'}) return n");
    		Iterator<Node> nodes = result.columnAs("n");
    		
    		if(!nodes.hasNext())
    			return Response.status(Status.NOT_FOUND).entity("trad node not found").build();
    		
    		traditionNode = nodes.next();
    		    		
    		Iterable<Relationship> rels = traditionNode.getRelationships(Direction.OUTGOING);
    		
    		if(rels==null) 
    			return Response.status(Status.NOT_FOUND).entity("rels not found").build();

    		Iterator<Relationship> relIt = rels.iterator();
    		
    		while( relIt.hasNext()) 
    		{
    			Relationship rel = relIt.next();
    			startNode = rel.getEndNode();
    			if(startNode!=null && startNode.hasProperty("text"))
    			{
    				if(startNode.getProperty("text").equals("#START#"))
    				{
    					rels = startNode.getRelationships(Direction.OUTGOING);
    					break;
    				}
    			}	
    		}

    		if(rels==null) 
    			return Response.status(Status.NOT_FOUND).entity("start node not found").build();


    		
    		
			tx.success();
		}
		catch(Exception e)
	    {
	    	e.printStackTrace();
	    }	
		finally
		{
			db.shutdown();
		}
		//return Response.status(Status.NOT_FOUND).build();
		
		return Response.ok(readList).build();
	}
}
