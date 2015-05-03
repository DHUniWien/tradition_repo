package net.stemmaweb.rest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.model.UserModel;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;

import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.traversal.Uniqueness;

/**
 * 
 * Comprises all the api calls related to a user.
 * 
 */
@Path("/user")
public class User implements IResource {
	GraphDatabaseService db = GraphDatabaseServiceProvider.getDatabase();
	
	@GET
	public String getIt() {
		return "User!";
	}


	/**
	 * Creates a user based on the parameters submitted in JSON.
	 * 
	 * @param userModel
	 *            in JSON Format
	 * @return OK on success or an ERROR as JSON
	 */
	@POST
	@Path("createuser")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response create(UserModel userModel) {
		

		
		////GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);
		if (DatabaseService.checkIfUserExists(userModel.getId(),db)) {
			//db.shutdown()();
			return Response.status(Response.Status.CONFLICT).entity("Error: A user with this id already exists")
					.build();
		}

		ExecutionEngine engine = new ExecutionEngine(db);
		try (Transaction tx = db.beginTx()) {
			ExecutionResult rootNodeSearch = engine.execute("match (n:ROOT) return n");
			Node rootNode = (Node) rootNodeSearch.columnAs("n").next();

			Node node = db.createNode(Nodes.USER);
			node.setProperty("id", userModel.getId());
			node.setProperty("isAdmin", userModel.getIsAdmin());

			rootNode.createRelationshipTo(node, ERelations.NORMAL);

			tx.success();
		} catch (Exception e) {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
		} finally {
			//db.shutdown()();
		}
		return Response.status(Response.Status.CREATED).build();
	}

	/**
	 * Gets a user by the id.
	 * 
	 * @param userId
	 * @return UserModel as JSON
	 */
	@GET
	@Path("getuser/withid/{userId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getUserById(@PathParam("userId") String userId) {
		UserModel userModel = new UserModel();
		//GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);

		ExecutionEngine engine = new ExecutionEngine(db);
		try (Transaction tx = db.beginTx()) {
			ExecutionResult result = engine.execute("match (userId:USER {id:'" + userId + "'}) return userId");
			Iterator<Node> nodes = result.columnAs("userId");

			if (nodes.hasNext()) {
				Node node = nodes.next();
				userModel.setId((String) node.getProperty("id"));
				userModel.setIsAdmin((String) node.getProperty("isAdmin"));
			} else {
				return null;
			}

			tx.success();
		} catch (Exception e) {
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		} finally { 
			//db.shutdown()();
		}
		return Response.ok(userModel).build();
	}
	
	/**
	 * Removes a user and all his traditions
	 * @param userId
	 * @return
	 */
	@DELETE
	@Path("deleteuser/withid/{userId}")
	public Response deleteUserById(@PathParam("userId") String userId) {
		//GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);

		ExecutionEngine engine = new ExecutionEngine(db);
		try (Transaction tx = db.beginTx()) {
			ExecutionResult result = engine.execute("match (userId:USER {id:'" + userId + "'}) return userId");
			Iterator<Node> nodes = result.columnAs("userId");

			if (nodes.hasNext()) {
				Node node = nodes.next();
				
				/*
				 * Find all the nodes and relations to remove
				 */
				Set<Relationship> removableRelations = new HashSet<Relationship>();
				Set<Node> removableNodes = new HashSet<Node>();
				for (Node currentNode : db.traversalDescription()
				        .depthFirst()
				        .relationships( ERelations.NORMAL, Direction.OUTGOING)
				        .relationships( ERelations.STEMMA, Direction.OUTGOING)
				        .relationships( ERelations.RELATIONSHIP, Direction.OUTGOING)
				        .uniqueness( Uniqueness.RELATIONSHIP_GLOBAL )
				        .traverse( node )
				        .nodes()) 
				{
					for(Relationship currentRelationship : currentNode.getRelationships()){
						removableRelations.add(currentRelationship);
					}
					removableNodes.add(currentNode);
				}
				
				/*
				 * Remove the nodes and relations
				 */
				for(Relationship removableRel:removableRelations){
		            removableRel.delete();
		        }
				for(Node remNode:removableNodes){
		            remNode.delete();
		        }
			} else {
				return Response.status(Response.Status.NOT_FOUND).entity("A user with this ID was not found").build();
			}

			tx.success();
		} catch (Exception e) {
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		} finally {
			//db.shutdown()();
		}
		return Response.status(Response.Status.OK).build();
	}

	/**
	 * Get all Traditions of a user 
	 * 
	 * @param userId
	 * @return
	 */
	@GET
	@Path("gettraditions/ofuser/{userId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getTraditionsByUserId(@PathParam("userId") String userId) {

		//GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);
		ArrayList<TraditionModel> traditions = new ArrayList<TraditionModel>();
		
		if (!DatabaseService.checkIfUserExists(userId, db)) {
			//db.shutdown()();
			return null;
		}

		ExecutionEngine engine = new ExecutionEngine(db);
		ExecutionResult result = null;
		try (Transaction tx = db.beginTx()) {
			result = engine.execute("match (n)<-[:NORMAL]-(userId:USER {id:'" + userId + "'}) return n");
			Iterator<Node> tradIterator = result.columnAs("n");
			while (tradIterator.hasNext()) {
				if (tradIterator.hasNext()) {
					Node tradNode = tradIterator.next();
					TraditionModel tradition = new TraditionModel();
					tradition.setId(tradNode.getProperty("id").toString());
					tradition.setName(tradNode.getProperty("dg1").toString());
					traditions.add(tradition);
				}
			}
			tx.success();

		} catch (Exception e) {
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		} finally {
			//db.shutdown()();
		}
		return Response.ok(traditions).build();
	}
}
