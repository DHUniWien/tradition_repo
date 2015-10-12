package net.stemmaweb.stemmaserver.benchmarktests;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;

import org.neo4j.graphdb.*;


/**
 * A helper class which helps to populate a complete random graph
 * 
 * @author PSE FS 2015 Team2
 *
 */
class RandomGraphGenerator {


    /**
     * A method which fills the database with randomly generated valid traditions
     *
     * @precondition the database should be initialized but empty
     * @param db
     * @param cardOfUsers
     * @param cardOfTraditionsPerUser
     * @param cardOfWitnesses
     * @param maxRank
     * @postcondition the database if full
     */
    public void role(GraphDatabaseService db, int cardOfUsers, int cardOfTraditionsPerUser,
            int cardOfWitnesses, int maxRank){

        Random randomGenerator = new Random();

        String loremIpsum = "Lorem ipsum dolor sit amet consetetur sadipscing elitr sed diam "
                + "nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat sed diam "
                + "voluptua At vero eos et accusam et justo duo dolores et ea rebum Stet clita "
                + "kasd gubergren no sea takimata sanctus est Lorem ipsum dolor sit amet Lorem "
                + "ipsum dolor sit amet consetetur sadipscing elitr sed diam nonumy eirmod tempor "
                + "invidunt ut labore et dolore magna aliquyam erat sed diam voluptua At vero eos "
                + "et accusam et justo duo dolores et ea rebum Stet clita kasd gubergren no sea "
                + "takimata sanctus est Lorem ipsum dolor sit amet";
        String[] loremIpsumArray = loremIpsum.split(" ");

        try(Transaction tx = db.beginTx()) {
            Result result = db.execute("match (n:ROOT) return n");
            Iterator<Node> nodes = result.columnAs("n");
            if(!nodes.hasNext()) {
                Node node = db.createNode(Nodes.ROOT);
                node.setProperty("name", "Root node");
                node.setProperty("LAST_INSERTED_TRADITION_ID", "1000");
            }
            tx.success();
        }

        for(int k = 0; k < cardOfUsers; k++) {
            Node currentUser;
            try (Transaction tx = db.beginTx()) {
                Result rootNodeSearch = db.execute("match (n:ROOT) return n");
                Node rootNode = (Node) rootNodeSearch.columnAs("n").next();

                currentUser = db.createNode(Nodes.USER);
                currentUser.setProperty("id", Integer.toString(k));
                currentUser.setProperty("isAdmin", Integer.toString(randomGenerator.nextInt(2)));

                rootNode.createRelationshipTo(currentUser, ERelations.SEQUENCE);

                tx.success();
            }

            /**
             * Create the Traditions
             */
            for(int i=0; i < cardOfTraditionsPerUser; i++) {
                int ind = 0;
                System.out.print("Import User: " +(k+1)+"/"+cardOfUsers+" [");
                for( ; ind < (int)((double) i/cardOfTraditionsPerUser * 20.0); ind++) {
                    System.out.print("#");
                }
                for( ; ind < 20; ind++) {
                    System.out.print(" ");
                }
                System.out.println("]");
                ArrayList<WitnessBranch> witnessUnconnectedBranches = new ArrayList<>();
                Node traditionRootNode;
                try (Transaction tx = db.beginTx()) {
                    String prefix = db.findNodes(Nodes.ROOT, "name", "Root node")
                            .next()
                            .getProperty("LAST_INSERTED_TRADITION_ID")
                            .toString();
                    traditionRootNode = db.createNode(Nodes.TRADITION);
                    Node rootNode = db.findNodes(Nodes.ROOT, "name", "Root node").next();
                    rootNode.setProperty("LAST_INSERTED_TRADITION_ID",
                            Integer.toString(Integer.parseInt(prefix) + 1));

                    traditionRootNode.setProperty("name", "TestTradition_"+prefix);
                    traditionRootNode.setProperty("id", prefix);
                    currentUser.createRelationshipTo(traditionRootNode, ERelations.SEQUENCE);

                    /**
                     * Create start node
                     */
                    Node startNode = db.createNode(Nodes.READING);
                    startNode.setProperty("text", "#START#");
                    startNode.setProperty("is_start", "1");
                    startNode.setProperty("rank", "0");
                    startNode.setProperty("is_common", "0");

                    traditionRootNode.createRelationshipTo(startNode, ERelations.COLLATION);

                    for(int l = 0; l < cardOfWitnesses; l++){
                        WitnessBranch witnessBranch = new WitnessBranch();
                        witnessBranch.setLastNode(startNode);
                        witnessBranch.setName("W"+l);
                        witnessUnconnectedBranches.add(witnessBranch);
                    }

                    tx.success();
                }

                /**
                 * Create Nodes for each rank
                 */
                ArrayList<WitnessBranch> witnessConnectedBranches = new ArrayList<>();
                for(int u = 1; u < maxRank; u++) {

                    ArrayList<Node> nodesOfCurrentRank = new ArrayList<>();
                    int numberOfNodesOnThisRank = randomGenerator.nextInt(cardOfWitnesses)+1;
                    try(Transaction tx = db.beginTx()){
                        for(int m = 0; m < numberOfNodesOnThisRank; m++) {
                            Node wordNode = db.createNode(Nodes.READING);

                            wordNode.setProperty("text", loremIpsumArray[randomGenerator
                                    .nextInt(loremIpsumArray.length)]);
                            wordNode.setProperty("rank", Integer.toString(u));
                            wordNode.setProperty("is_common", "0");
                            wordNode.setProperty("language", "latin");

                            nodesOfCurrentRank.add(wordNode);
                        }
                        tx.success();
                    }
                    try(Transaction tx = db.beginTx()) {

                        int moduloIndex = 0;

                        /**
                         * Connect the words randomly
                         */
                        for(int n = cardOfWitnesses; n > 0; n--){
                            WitnessBranch witnessBranch = witnessUnconnectedBranches
                                    .remove(randomGenerator.nextInt(n));
                            Node lastNode = witnessBranch.getLastNode();
                            Node nextNode = nodesOfCurrentRank.get(moduloIndex);

                            Iterable<Relationship> relationships = lastNode
                                    .getRelationships(ERelations.SEQUENCE);

                            Relationship relationshipAtoB = null;
                            for (Relationship relationship : relationships) {
                                if((relationship.getStartNode().equals(lastNode)
                                        || relationship.getEndNode().equals(lastNode))
                                        && relationship.getStartNode().equals(nextNode)
                                        || relationship.getEndNode().equals(nextNode)){
                                    relationshipAtoB = relationship;
                                }
                            }

                            if(relationshipAtoB == null) {
                                String[] witnessesArray = {witnessBranch.getName()};
                                Relationship rel = lastNode.createRelationshipTo(nextNode,
                                        ERelations.SEQUENCE);
                                rel.setProperty("witnesses", witnessesArray);
                            } else {
                                String[] arr = (String[]) relationshipAtoB.getProperty("witnesses");

                                String[] witnessesArray = new String[arr.length + 1];
                                System.arraycopy(arr, 0, witnessesArray, 0, arr.length);
                                witnessesArray[arr.length] = witnessBranch.getName();

                                relationshipAtoB.setProperty("witnesses", witnessesArray);
                            }

                            witnessBranch.setLastNode(nextNode);
                            witnessConnectedBranches.add(witnessBranch);
                            moduloIndex = (moduloIndex+1)%numberOfNodesOnThisRank;
                        }
                        witnessUnconnectedBranches = witnessConnectedBranches;
                        tx.success();
                    }
                }

                /**
                 * Create End node
                 */
                Node endNode;
                try(Transaction tx = db.beginTx()){
                    endNode = db.createNode(Nodes.READING);
                    endNode.setProperty("text", "#END#");
                    endNode.setProperty("rank", maxRank);
                    endNode.setProperty("is_start", "0");
                    endNode.setProperty("is_common", "0");
                    traditionRootNode.createRelationshipTo(endNode, ERelations.HAS_END);
                    tx.success();
                }

                /**
                 * Connect to end node
                 */
                for(WitnessBranch witnessBranch : witnessUnconnectedBranches) {
                    try(Transaction tx = db.beginTx()) {
                        Node lastNode = witnessBranch.getLastNode();

                        Iterable<Relationship> relationships = lastNode
                                .getRelationships(ERelations.SEQUENCE);

                        Relationship relationshipAtoB = null;
                        for (Relationship relationship : relationships) {
                            if((relationship.getStartNode().equals(lastNode)
                                    || relationship.getEndNode().equals(lastNode))
                                    && relationship.getStartNode().equals(endNode)
                                    || relationship.getEndNode().equals(endNode)) {
                                relationshipAtoB = relationship;
                            }
                        }

                        if(relationshipAtoB == null) {
                            String[] witnessesArray = {witnessBranch.getName()};
                            Relationship rel = lastNode.createRelationshipTo(endNode,
                                    ERelations.SEQUENCE);
                            rel.setProperty("witnesses", witnessesArray);
                        } else {
                            String[] arr = (String[]) relationshipAtoB.getProperty("witnesses");

                            String[] witnessesArray = new String[arr.length + 1];
                            System.arraycopy(arr, 0, witnessesArray, 0, arr.length);
                            witnessesArray[arr.length] = witnessBranch.getName();

                            relationshipAtoB.setProperty("witnesses", witnessesArray);
                        }

                        tx.success();
                    }
                }
            }
        }
    }

    private class WitnessBranch {
        private Node lastNode;
        private String name;
        public Node getLastNode() {
            return lastNode;
        }
        public void setLastNode(Node lastNode) {
            this.lastNode = lastNode;
        }
        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }
    }
}
