package net.stemmaweb.stemmaserver.benachmarktests;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;

import net.stemmaweb.stemmaserver.OSDetector;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

import com.carrotsearch.junitbenchmarks.BenchmarkRule;
import com.carrotsearch.junitbenchmarks.annotation.AxisRange;
import com.carrotsearch.junitbenchmarks.annotation.BenchmarkMethodChart;

@AxisRange(min = 0, max = 1)
@BenchmarkMethodChart(filePrefix = "benchmark/benchmark-1kNodes")
public class Benchmark1kNodes extends BenachmarkTests {
	
	@Rule
	public TestRule benchmarkRun = new BenchmarkRule();
	
	@BeforeClass
	public static void prepareTheDatabase(){

		try {
			tempFolder.create();
			File dbPathFile = new File("database");
			Files.move(dbPathFile.toPath(), tempFolder.getRoot().toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			e.printStackTrace();
		}
		/*
		 * Fill the Testbench with a nice graph 9 users 2 traditions 5 witnesses with degree 10
		 */
		RandomGraphGenerator rgg = new RandomGraphGenerator();
		GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();
		GraphDatabaseService db = dbFactory.newEmbeddedDatabase("database");
		
		rgg.role(db, 2, 1, 5, 100);

		db.shutdown();
		if (OSDetector.isWin())
			filename = "src\\TestXMLFiles\\ReadingstestTradition.xml";
		else
			filename = "src/TestXMLFiles/ReadingstestTradition.xml";
		
		try {
			tradId = importResource.parseGraphML(filename, "1").getEntity().toString().replace("{\"tradId\":", "").replace("}", "");
		} catch (FileNotFoundException f) {
			// this error should not occur
			assertTrue(false);
		}
		
		db = dbFactory.newEmbeddedDatabase("database");
		ExecutionEngine engine = new ExecutionEngine(db);
		ExecutionResult result = engine.execute("match (w:WORD {dn15:'showers'}) return w");
		Iterator<Node> nodes = result.columnAs("w");
		duplicateReadingNodeId = nodes.next().getId();

		result = engine.execute("match (w:WORD {dn15:'the root'}) return w");
		nodes = result.columnAs("w");
		theRoot = nodes.next().getId();
		
		result = engine.execute("match (w:WORD {dn15:'unto me'}) return w");
		nodes = result.columnAs("w");
		untoMe = nodes.next().getId();
		
		db.shutdown();
		

		
		
	}
	
	
	@AfterClass 
	public static void shutDown(){
		try {
			File dbPathFile = new File("database");
			FileUtils.deleteDirectory(dbPathFile);
			Files.move(tempFolder.getRoot().toPath(), dbPathFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
}
