package net.stemmaweb.services;

import java.util.Iterator;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;

/**
 * This class is used for debugging purposes only
 * @author PSE FS 2015 Team2
 */
class DebugService {

    public String findPathProblem(String tradId, GraphDatabaseService db) {

        String exceptionString;
        try (Transaction tx = db.beginTx()) {

            Result traditionResult = db
                    .execute("match (t:TRADITION {id:'" + tradId + "'}) return t");
            Iterator<Node> traditions = traditionResult.columnAs("t");

            if (!traditions.hasNext()) {
                exceptionString = "such tradition does not exist in the data base";
            }
            else {
                Result witnessResult = db
                        .execute("match (tradition:TRADITION {id:'" + tradId
                                + "'})--(w:READING  {text:'#START#'}) return w");
                Iterator<Node> witnesses = witnessResult.columnAs("w");

                if (witnesses.hasNext()) {
                    exceptionString = "no witness found: there is a problem with the data path";
                } else {
                    exceptionString = "such witness does not exist in the data base";
                }
            }
        }
        db.shutdown();
        return exceptionString;
    }

}
