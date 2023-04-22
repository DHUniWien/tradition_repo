package net.stemmaweb.stemmaserver.integrationtests;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.Response;

import org.glassfish.jersey.test.JerseyTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;

import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.TextSequenceModel;
import net.stemmaweb.model.WitnessModel;
import net.stemmaweb.rest.Root;
import net.stemmaweb.rest.Tradition;
import net.stemmaweb.rest.Witness;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.Util;

/**
 * Test TEI parallel segmentation input.
 *
 * @author tla
 */
public class TEIParallelSegInputTest {
    private GraphDatabaseService db;
    private JerseyTest jerseyTest;
	private DatabaseManagementService dbbuilder;

    @Before
    public void setUp() throws Exception {
//        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();
    	dbbuilder = new TestDatabaseManagementServiceBuilder().build();
    	dbbuilder.createDatabase("stemmatest");
    	db = dbbuilder.database("stemmatest");
        Util.setupTestDB(db, "1");

        // Create a JerseyTestServer for the necessary REST API calls
        jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
                .addResource(Root.class)
                .create();
        jerseyTest.setUp();
    }

    @Test
    public void testTEIPSInput()  {
        String qacText = "Ἡ περὶ τοῦ ἁγίου πνεύματος βλασφημία αὐτόθεν ἔχει τὴν λύσιν· ὁ δὲ δεύτερος ἐστὶν οὗτος· ὅτάν " +
                "τις ἐν ἁμαρτίαις ἐνεχόμενος, ἀκούων δὲ τοῦ κυρίου λέγοντος μὴ κρίνετε φοβούμενος οὐδένα κρίνει ἐν τῇ " +
                "ἐξετάσει τῶν βεβιωμένων ὡς φύλαξ τῆς ἐντολῆς οὐ κρίνεται· εἰ μὴ τὸ γενέσθαι πιστόν, εἰκότως ὅταν ἐν " +
                "ἁμαρτίαις τίς ὢν οἰκονομῆται ἐκ τῆς προνοίας ἐν συμφοραῖς, ἐν ἀνάγκαις, ἐν νόσοις — ὡς οὐκ οἶδε γὰρ " +
                "διὰ τῶν τοιούτων καθαίρει αὐτὸν ὁ θεός — οὖν τῷ ἐν ἀπιστίᾳ τὸν βίον κατακλείσαντι οὔτε ἐνταῦθα οὔτε ἐν " +
                "τῷ μέλλοντι ἀφεθήσεται τῆς ἀπιστίας καὶ ἀθεΐας ἡ ἁμαρτία. τοῦ νύσσης Ἤκουσά που τῆς ἁγίας " +
                "γραφῆς κατακινούσης ἐκείνους, οἳ κατὰ τοῦ θεοῦ βλασφημίας αἴτιοι γίνονται. Οὐαὶ γὰρ φησὶν δι᾽ οὓς " +
                "τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσι. Διὰ τοῦτο γὰρ χαλεπὴν τοῖς τοιούτοις ἀπειλὴν ὁ λόγος " +
                "ἐπανατείνεται λέγων ἐκείνοις εἶναι τὸ Οὐαὶ δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσιν. " +
                "Νείλου μοναχοῦ Ὄψις γυναικὸς βέλος ἐστὶ πεφαρμακευμένον, ἔτρωσε τὴν ψυχὴν, καὶ τὸν ἰὸν ἐναπέθετο, " +
                "καὶ ὅσον χρονίζει, πλείονα τὴν σῆψιν ἐργάζεται. βέλτιον γὰρ οἴκοι μένοντα σχολάζειν διηνεκῶς τῇ " +
                "προσευχῇ, ἢ διὰ τοῦ τιμᾶν τὰς ἑορτὰς πάρεργον γίνεσθαι τῶν ἐχθρῶν. Φεῦγε συντυχίας γυναικῶν ἐὰν " +
                "θέλῃς σωφρονεῖν, καὶ μὴ δῷς αὐταῖς παρρησίαν θαρρῆσαι σοί ποτε. Θάλλει βοτάνη ἑστῶσα παρ᾽ ὕδατι, " +
                "καὶ πάθος ἀκολασίας, ἐν συντυχίαις γυναικῶν.";
        String qpcText = "Ἡ περὶ τοῦ ἁγίου πνεύματος βλασφημία αὐτόθεν ἔχει τὴν λύσιν· ὁ δὲ δεύτερος ἐστὶν οὗτος· ὅτάν " +
                "τις ἐν ἁμαρτίαις ἐνεχόμενος, ἀκούων δὲ τοῦ κυρίου λέγοντος μὴ κρίνετε φοβούμενος οὐδένα κρίνῃ ἐν τῇ " +
                "ἐξετάσει τῶν βεβιωμένων ὡς φύλαξ τῆς ἐντολῆς οὐ κρίνεται· εἰ μὴ τὸ γενέσθαι πιστόν, εἰκότως ὅταν ἐν " +
                "ἁμαρτίαις τίς ὢν οἰκονομῆται ἐκ τῆς προνοίας ἐν συμφοραῖς, ἐν ἀνάγκαις, ἐν νόσοις — ὡς οὐκ οἶδε γὰρ " +
                "διὰ τῶν τοιούτων καθαίρει αὐτὸν ὁ θεός — οὖν τῷ ἐν ἀπιστίᾳ τὸν βίον καταλύσαντι οὔτε ἐνταῦθα οὔτε ἐν " +
                "τῷ μέλλοντι ἀφεθήσεται τῆς ἀπιστίας καὶ ἀθεΐας ἡ ἁμαρτία. νείλου τοῦ νύσσης Ἤκουσά που τῆς ἁγίας " +
                "γραφῆς κατακρινούσης ἐκείνους, οἳ κατὰ τοῦ θεοῦ βλασφημίας αἴτιοι γίνονται. Οὐαὶ γὰρ φησὶν δι᾽ οὓς " +
                "τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσι. Διὰ τοῦτο γὰρ χαλεπὴν τοῖς τοιούτοις ἀπειλὴν ὁ λόγος " +
                "ἐπανατείνεται λέγων ἐκείνοις εἶναι τὸ Οὐαὶ δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσιν. " +
                "Νείλου μοναχοῦ Ὄψις γυναικὸς βέλος ἐστὶ πεφαρμακευμένον, ἔτρωσε τὴν ψυχὴν, καὶ τὸν ἰὸν ἐναπέθετο, " +
                "καὶ ὅσον χρονίζει, πλείονα τὴν σῆψιν ἐργάζεται. βέλτιον γὰρ οἴκοι μένοντα σχολάζειν διηνεκῶς τῇ " +
                "προσευχῇ, ἢ διὰ τοῦ τιμᾶν τὰς ἑορτὰς πάρεργον γίνεσθαι τῶν ἐχθρῶν. Φεῦγε συντυχίας γυναικῶν ἐὰν " +
                "θέλῃς σωφρονεῖν, καὶ μὴ δῷς αὐταῖς παρρησίαν θαρρῆσαι σοί ποτε. Θάλλει βοτάνη ἑστῶσα παρ᾽ ὕδατι, " +
                "καὶ πάθος ἀκολασίας, ἐν συντυχίαις γυναικῶν.";
        String tText = "Ἡ περὶ τῆς τοῦ πνεύματος τοῦ ἁγίου βλασφημίας ἀπορία αὐτόθι ἔχει τὴν λύσιν· ὁ δὲ δεύτερος " +
                "ἐστὶν οὗτος· ὅτάν τις ἐν ἁμαρτίαις ἐνεχόμενος, ἀκούων δὲ τοῦ κυρίου λέγοντος μὴ κρίνεται φοβούμενος " +
                "οὐδένα κρίνει ἐν τῇ ἐξετάσει τῶν βεβιωμένων ὡς φύλαξ τῆς ἐντολῆς οὐ κρίνεται· εἰ μὴ τὸ γενέσθαι " +
                "πιστόν, εἰκότως ὅταν ἐν ἁμαρτίαις τίς ὢν οἰκονομεῖται ἐκ τῆς προνοίας ἐν συμφοραῖς, ἐν ἀνάγκαις, ἐν " +
                "νόσοις — ὡς οὐκ οἶδε γὰρ διὰ τῶν τοιούτων καθαίρει αὐτὸν ὁ θεός — οὖν τῷ ἐν ἀπιστίᾳ τὸν βίον " +
                "κατακλείσαντι οὔτε ἐνταῦθα οὔτε ἐν τῷ μέλλοντι ἀφεθήσεται τῆς ἀπιστίας καὶ ἀθεΐας ἡ ἁμαρτία. " +
                "Γρηγορίου Νύσης Ἤκουσά που τῆς ἁγίας γραφῆς κατακρινούσης ἐκείνους, οἳ κατὰ τῆς τοῦ θεοῦ βλασφημίας " +
                "αἴτιοι γίνονται. Οὐαὶ γὰρ φησὶν δι᾽ οὓς τὸ ὄνομά μου βλασφημεῖται ἐν τοῖς ἔθνεσι. Διὰ τοῦτο χαλεπὴν " +
                "τοῖς τοιούτοις ἀπειλὴν ὁ λόγος ἐπανατείνεται λέγων ἐκείνοις εἶναι τὸ Οὐαὶ δι᾽ οὓς τὸ ὄνομά μου " +
                "βλασφημεῖται ἐν τοῖς ἔθνεσιν. Νείλου μοναχοῦ Ὄψις γυναικὸς μέλος ἐστὶ πεφαρμακευμένον, ἔτρωσε τὴν " +
                "ψυχὴν, καὶ τὸν ἰὸν ἐναπέθετο, καὶ ὅσον χρονίζει, πλείονα τὴν σῆψιν ἐργάζεται. βέλτιον γὰρ οἴκοι " +
                "μένοντα σχολάζειν διηνεκῶς τῇ προσευχῇ, ἢ διὰ τοῦ τιμᾶν τὰς ἑορτὰς πάρεργον γίνεσθαι τῶν ἐχθρῶν. " +
                "Φεῦγε συντυχίας γυναικῶν ἐὰν θέλῃς σωφρονεῖν, καὶ μὴ δῷς αὐταῖς παρρησίαν θαρρῆσαι σοί ποτε. Θάλλει " +
                "βοτάνη ἐστῶσα παρ᾽ ὕδατι, καὶ πάθος ἀκολασίας, ἐν συντυχίαις γυναικῶν.";

        Response cResult = Util.createTraditionFromFileOrString(jerseyTest, "Florilegium", "LR", "1",
                "src/TestFiles/florilegium_tei_ps.xml", "teips");
        assertEquals(Response.Status.CREATED.getStatusCode(), cResult.getStatus());

        String tradId = Util.getValueFromJson(cResult, "tradId");
        Tradition tradition = new Tradition(tradId);

        // Basic statistics
        Response result = tradition.getAllWitnesses();
        @SuppressWarnings("unchecked")
        ArrayList<WitnessModel> allWitnesses = (ArrayList<WitnessModel>) result.getEntity();
        assertEquals(13, allWitnesses.size());
        result = tradition.getAllReadings();

        @SuppressWarnings("unchecked")
        ArrayList<ReadingModel> allReadings = (ArrayList<ReadingModel>) result.getEntity();
        assertEquals(317, allReadings.size());
        boolean foundReading = false;
        for (ReadingModel r : allReadings)
            if (r.getText().equals("βλασφημίας"))
                foundReading = true;
        assertTrue(foundReading);


        // Get a witness text
        TextSequenceModel tm = (TextSequenceModel) new Witness(tradId, "T").getWitnessAsText().getEntity();
        assertEquals(tText, tm.getText());
        // Get a layered witness text
        Witness q = new Witness(tradId, "Q");
        List<String> layers = new ArrayList<>();
        layers.add("a.c.");
        TextSequenceModel ltm = (TextSequenceModel) q.getWitnessAsTextWithLayer(layers, "0", "E").getEntity();
        assertEquals(qacText, ltm.getText());

        // Fail to get a witness text with a conflicting set of layers
        layers.add("s.l.");
        Response badResult = q.getWitnessAsTextWithLayer(layers, "0", "E");
        assertEquals(Response.Status.CONFLICT.getStatusCode(), badResult.getStatus());

        // Get a witness text with a valid set of layers
        layers.remove(0);
        layers.add("margin");
        ltm = (TextSequenceModel) q.getWitnessAsTextWithLayer(layers, "0", "E").getEntity();
        assertEquals(qpcText, ltm.getText());

    }

    @After
    public void tearDown() throws Exception {
//        db.shutdown();
    	if (dbbuilder != null) {
    		dbbuilder.shutdownDatabase(db.databaseName());
    	}
        jerseyTest.tearDown();
    }
}
