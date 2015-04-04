package net.stemmaweb.stemmaserver.benachmarktests;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.junit.BeforeClass;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

import com.carrotsearch.junitbenchmarks.annotation.AxisRange;
import com.carrotsearch.junitbenchmarks.annotation.BenchmarkMethodChart;

@AxisRange(min = 0, max = 1)
@BenchmarkMethodChart(filePrefix = "benchmark/benchmark-100kNodes")
public class Benchmark100kNodes extends BenachmarkTests {
	
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
		
		rgg.role(db, 10, 10, 10, 100);
		db.shutdown();
	}
	
}
