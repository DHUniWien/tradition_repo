package net.stemmaweb.stemmaserver.benachmarktests;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

import com.carrotsearch.junitbenchmarks.BenchmarkRule;
import com.carrotsearch.junitbenchmarks.annotation.AxisRange;
import com.carrotsearch.junitbenchmarks.annotation.BenchmarkMethodChart;

@AxisRange(min = 0, max = 1)
@BenchmarkMethodChart(filePrefix = "benchmark/benchmark-600Nodes")
public class Benchmark600Nodes extends BenachmarkTests {
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
		initDatabase();
	}

	public static void initDatabase() {
		RandomGraphGenerator rgg = new RandomGraphGenerator();
		GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();
		GraphDatabaseService db = dbFactory.newEmbeddedDatabase("database");
		
		rgg.role(db, 2, 1, 3, 100);
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
