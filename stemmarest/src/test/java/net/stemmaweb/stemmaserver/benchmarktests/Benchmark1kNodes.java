package net.stemmaweb.stemmaserver.benchmarktests;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Iterator;

import net.stemmaweb.rest.Reading;
import net.stemmaweb.rest.Relation;
import net.stemmaweb.rest.Tradition;
import net.stemmaweb.rest.User;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.GraphMLToNeo4JParser;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Result;

import com.carrotsearch.junitbenchmarks.BenchmarkRule;
import com.carrotsearch.junitbenchmarks.annotation.AxisRange;
import com.carrotsearch.junitbenchmarks.annotation.BenchmarkMethodChart;
import org.neo4j.test.TestGraphDatabaseFactory;

/**
 * 
 * @author PSE FS 2015 Team2
 *
 */
@AxisRange(min = 0, max = 0.2)
@BenchmarkMethodChart(filePrefix = "benchmark/benchmark-1kNodes")
public class Benchmark1kNodes extends BenchmarkTests {

    @Rule
    public TestRule benchmarkRun = new BenchmarkRule();

    @BeforeClass
    public static void prepareTheDatabase() {

        RandomGraphGenerator rgg = new RandomGraphGenerator();

        GraphDatabaseService db = new TestGraphDatabaseFactory().newImpermanentDatabase();
        GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider(db);

        userResource = new User();
        traditionResource = new Tradition();
        readingResoruce = new Reading();
        relationResource = new Relation();
        importResource = new GraphMLToNeo4JParser();

        jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
                .addResource(userResource)
                .addResource(traditionResource)
                .addResource(relationResource)
                .addResource(readingResoruce).create();
        try {
            jerseyTest.setUp();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        rgg.role(db, 2, 1, 5, 100);

		File testfile = new File("src/TestXMLFiles/ReadingstestTradition.xml");

        try {
            tradId = importResource.parseGraphML(testfile.getPath(), "1", "Tradition")
                    .getEntity()
                    .toString()
                    .replace("{\"tradId\":", "")
                    .replace("}", "");
        } catch (FileNotFoundException f) {
            // this error should not occur
            assertTrue(false);
        }

        Result result = db.execute("match (w:READING {text:'showers'}) return w");
        Iterator<Node> nodes = result.columnAs("w");
        duplicateReadingNodeId = nodes.next().getId();

        result = db.execute("match (w:READING {text:'the root'}) return w");
        nodes = result.columnAs("w");
        theRoot = nodes.next().getId();

        result = db.execute("match (w:READING {text:'unto me'}) return w");
        nodes = result.columnAs("w");
        untoMe = nodes.next().getId();
    }

    @AfterClass
    public static void shutdown() throws Exception{
        GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
        dbServiceProvider.getDatabase().shutdown();
        jerseyTest.tearDown();
    }

}
