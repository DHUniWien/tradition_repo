package net.stemmaweb.stemmaserver.benchmarktests;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Iterator;

import net.stemmaweb.rest.Root;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
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
    public static void prepareTheDatabase() throws Exception {

        RandomGraphGenerator rgg = new RandomGraphGenerator();

        GraphDatabaseService db = new GraphDatabaseServiceProvider(
                new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();

        // Create the Jersey test server
        webResource = new Root();
        jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
                .addResource(webResource)
                .create();
        try {
            jerseyTest.setUp();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        rgg.role(db, 2, 1, 5, 100);

        testfile = new File("src/TestFiles/ReadingstestTradition.xml");
        try {
            String fileName = testfile.getPath();
            tradId = createTraditionFromFile("Tradition", "LR", "1", fileName, "stemmaweb");
        } catch (FileNotFoundException f) {
            // this error should not occur
            fail();
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
