package net.stemmaweb.stemmaserver.integrationtests;


import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.model.UserModel;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Root;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.test.framework.JerseyTest;
import org.neo4j.test.TestGraphDatabaseFactory;

import static org.junit.Assert.*;

/**
 * 
 * Contains all tests for the api calls related to the user.
 * 
 * @author PSE FS 2015 Team2
 *
 */
public class UserTest {

    private GraphDatabaseService db;

    /*
     * JerseyTest is the test environment to Test api calls it provides a
     * grizzly http service
     */
    private JerseyTest jerseyTest;

    @Before
    public void setUp() throws Exception {
        /*
         * Populate the test database with the root node
         */
        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();
        DatabaseService.createRootNode(db);

        /*
         * Create a JersyTestServer serving the Resource under test
         */
        Root webResource = new Root();
        jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
                .addResource(webResource)
                .create();
        jerseyTest.setUp();
    }

    /**
     * Test the ability to create a user
     */
    @Test
    public void createUserTest(){

        try (Transaction tx = db.beginTx()) {
            Node notaUser = db.findNode(Nodes.USER, "id", "1337");
            assertNull(notaUser);
            tx.success();
        }

        String jsonPayload = "{\"role\":\"user\",\"id\":1337,\"passphrase\":\"ABCDSaltedHash\"}";
        ClientResponse returnJSON = jerseyTest.resource().path("/user/1337")
                .type(MediaType.APPLICATION_JSON).put(ClientResponse.class, jsonPayload);
        assertEquals(Response.status(Response.Status.CREATED).build().getStatus(),
                returnJSON.getStatus());

        // Now check the list of users and make sure that the new user is there.
        List<UserModel> allUsers = jerseyTest.resource().path("/users")
                .type(MediaType.APPLICATION_JSON).get(new GenericType<List<UserModel>>() {});
        assertEquals(1, allUsers.size());
        Optional<UserModel> newUser = allUsers.stream().filter(x -> x.getId().equals("1337")).findFirst();
        assertTrue(newUser.isPresent());
        assertEquals("ABCDSaltedHash", newUser.get().getPassphrase());
    }


    /**
     * Test the behavior when creating a second user with the same id
     */
    @Test
    public void createAndUpdateUserTest(){

        String firstUser = "{\"role\":\"user\",\"id\":42}";
        String secondUser = "{\"role\":\"admin\",\"id\":42}";
        ClientResponse dummyJSON = jerseyTest.resource().path("/user/42")
                .type(MediaType.APPLICATION_JSON).put(ClientResponse.class, firstUser);
        assertEquals(Response.status(Response.Status.CREATED).build().getStatus(),
                dummyJSON.getStatus());

        ClientResponse returnJSON = jerseyTest.resource().path("/user/42")
                .type(MediaType.APPLICATION_JSON).put(ClientResponse.class, secondUser);
        assertEquals(Response.status(Response.Status.OK).build().getStatus(),
                returnJSON.getStatus());
    }


    /**
     * Test if the representation of a user is correct
     */
    @Test
    public void getUserTest(){
        UserModel userModel = new UserModel();
        userModel.setId("43");
        userModel.setRole("user");
        jerseyTest.resource()
                .path("/user/43")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, userModel);

        UserModel actualResponse = jerseyTest
                .resource()
                .path("/user/43")
                .get(UserModel.class);
        assertEquals("43",actualResponse.getId());
        assertEquals("user",actualResponse.getRole());


    }

    /**
     * Test if the representation of a user is correct
     */
    @Test
    public void getInvalidUserTest(){
        ClientResponse actualResponse = jerseyTest
                .resource()
                .path("/user/43")
                .get(ClientResponse.class);
        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), actualResponse.getStatus());
    }

    /**
     * Test if a user is correctly removed with all his subgraphs
     */
    @Test
    public void deleteUserTest(){

        /*
         * Create User 1
         */
        UserModel userModel = new UserModel();
        userModel.setId("1");
        userModel.setRole("user");
        jerseyTest.resource()
                .path("/user/1")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, userModel);

        /*
         * Create User 2
         */
        userModel = new UserModel();
        userModel.setId("2");
        userModel.setRole("user/2");
        jerseyTest.resource()
                .path("/user/2")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, userModel);

        /*
         * Create a test tradition for user 1
         */
        try(Transaction tx = db.beginTx())
        {
            // Add the new ownership
            String createTradition = "CREATE (tradition:TRADITION { id:'842' })";
            db.execute(createTradition);
            String createNewRelationQuery = "MATCH(user:USER {id:'1'}) "
                    + "MATCH(tradition: TRADITION {id:'842'}) "
                    + "SET tradition.name = 'TestTradition' "
                    + "SET tradition.public = '0' "
                    + "CREATE (tradition)<-[r:OWNS_TRADITION]-(user) RETURN r, tradition";
            db.execute(createNewRelationQuery);

            tx.success();
        }

        /*
         * Create a test tradition for user 2
         */
        try(Transaction tx = db.beginTx())
        {
            // Add the new ownership
            String createTradition = "CREATE (tradition:TRADITION { id:'843' })";
            db.execute(createTradition);
            String createNewRelationQuery = "MATCH(user:USER {id:'2'}) "
                    + "MATCH(tradition: TRADITION {id:'843'}) "
                    + "SET tradition.name = 'TestTradition' "
                    + "SET tradition.public = '0' "
                    + "CREATE (tradition)<-[r:OWNS_TRADITION]-(user) RETURN r, tradition";
            db.execute(createNewRelationQuery);
            tx.success();
        }

        try(Transaction tx = db.beginTx())
        {

            /*
             * Try to remove user 1 with all traditions. This should fail
             */
            ClientResponse actualResponse = jerseyTest.resource().path("/user/1")
                    .delete(ClientResponse.class);
            assertEquals(Response.Status.PRECONDITION_FAILED.getStatusCode(), actualResponse.getStatus());

            /*
             * Check that user 1 is still there
             */
            Node user = db.findNode(Nodes.USER, "id", "1");
            assertNotNull(user);

            /*
             * Check that tradition 842 is still there
             */
            Node tradition = db.findNode(Nodes.TRADITION, "id", "842");
            assertNotNull(tradition);

            /*
             * Delete tradition 842
             */
            actualResponse = jerseyTest.resource().path("/tradition/842")
                    .delete(ClientResponse.class);
            assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());
            tradition = db.findNode(Nodes.TRADITION, "id", "842");
            assertNull(tradition);

            /*
             * Try again to remove user 1
             */
            actualResponse = jerseyTest.resource().path("/user/1")
                    .delete(ClientResponse.class);
            assertEquals(Response.Status.OK.getStatusCode(), actualResponse.getStatus());

            /*
             * Check that user 1 is now gone
             */
            user = db.findNode(Nodes.USER, "id", "1");
            assertNull(user);

            /*
             * Check if user 2 still exists
             */
            user = db.findNode(Nodes.USER, "id", "2");
            assertNotNull(user);

            /*
             * Check if tradition 843 is removed
             */
            tradition = db.findNode(Nodes.TRADITION, "id", "843");
            assertNotNull(tradition);

            tx.success();
        }
    }

    /**
     * Test user Removal with invalid userId
     */
    @Test
    public void deleteInvalidUserTest(){
        /*
         * Create User 1
         */
        UserModel userModel = new UserModel();
        userModel.setId("1");
        userModel.setRole("user");
        jerseyTest.resource()
                .path("/user/1")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, userModel);

        /*
         * Create a test tradition for user 1
         */
        try(Transaction tx = db.beginTx())
        {
            // Add the new ownership
            String createTradition = "CREATE (tradition:TRADITION { id:'842' })";
            db.execute(createTradition);
            String createNewRelationQuery = "MATCH(user:USER {id:'1'}) "
                    + "MATCH(tradition: TRADITION {id:'842'}) "
                    + "SET tradition.name = 'TestTradition' "
                    + "SET tradition.public = '0' "
                    + "CREATE (tradition)<-[r:OWNS_TRADITION]-(user) RETURN r, tradition";
            db.execute(createNewRelationQuery);

            /*
             * Remove user 2 with all his traditions
             */
            ClientResponse actualResponse = jerseyTest
                    .resource()
                    .path("/user/2")
                    .delete(ClientResponse.class);
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), actualResponse.getStatus());

            /*
             * Check if user 1 still exists
             */
            Result result = db.execute("match (userId:USER {id:'1'}) return userId");
            Iterator<Node> nodes = result.columnAs("userId");
            assertTrue(nodes.hasNext());

            /*
             * Check if tradition 842 still exists
             */
            result = db.execute("match (tradId:TRADITION {id:'842'}) return tradId");
            nodes = result.columnAs("tradId");
            assertTrue(nodes.hasNext());

            /*
             * Check if user 2 does not exist
             */
            result = db.execute("match (userId:USER {id:'2'}) return userId");
            nodes = result.columnAs("userId");
            assertFalse(nodes.hasNext());

            /*
             * Check if tradition 843 does not exist
             */
            result = db.execute("match (tradId:TRADITION {id:'843'}) return tradId");
            nodes = result.columnAs("tradId");
            assertFalse(nodes.hasNext());
            tx.success();
        }
    }

    /**
     * Test if the traditions of a user are listed well
     */
    @Test
    public void getUserTraditions(){
        String jsonPayload = "{\"role\":\"user\",\"id\":837462}";
        jerseyTest
                .resource()
                .path("/user/837462")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, jsonPayload);
        
        try(Transaction tx = db.beginTx())
        {
            // Add the new ownership
            String createTradition = "CREATE (tradition:TRADITION { id:'842' })";
            db.execute(createTradition);
            String createNewRelationQuery = "MATCH(user:USER {id:'837462'}) "
                    + "MATCH(tradition: TRADITION {id:'842'}) "
                    + "SET tradition.name = 'TestTradition' "
                    + "SET tradition.public = '0' "
                    + "CREATE (tradition)<-[r:OWNS_TRADITION]-(user) RETURN r, tradition";
            db.execute(createNewRelationQuery);

            tx.success();
        }
        TraditionModel trad = new TraditionModel();
        trad.setId("842");
        trad.setName("TestTradition");
        List<TraditionModel> traditions = jerseyTest
                .resource()
                .path("/user/837462/traditions")
                .get(new GenericType<List<TraditionModel>>() {
                });
        TraditionModel tradLoaded = traditions.get(0);
        assertEquals(trad.getId(), tradLoaded.getId());
        assertEquals(trad.getName(), tradLoaded.getName());

        ClientResponse getStemmaResponse = jerseyTest
                .resource()
                .path("/user/837462/traditions")
                .type(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);
        assertEquals(Response.ok().build().getStatus(), getStemmaResponse.getStatus());

        ClientResponse getNotFoundStemmaResponse = jerseyTest
                .resource()
                .path("/user/xy/traditions")
                .type(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), getNotFoundStemmaResponse.getStatus());
    }

    /*
     * Shut down the jersey server
     * @throws Exception
     */
    @After
    public void tearDown() throws Exception {
        db.shutdown();
        jerseyTest.tearDown();
    }
}
