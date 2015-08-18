######## Tradition tests
## NOW
my $t = Text::Tradition->new( 'name' => 'empty' );
is( ref( $t ), 'Text::Tradition', "initialized an empty Tradition object" );
is( $t->name, 'empty', "object has the right name" );
is( scalar $t->witnesses, 0, "object has no witnesses" );

## TODO make a tabular collation parser
my $simple = 't/data/simple.txt';
my $s = Text::Tradition->new( 
    'name'  => 'inline', 
    'input' => 'Tabular',
    'file'  => $simple,
	);
is( ref( $s ), 'Text::Tradition', "initialized a Tradition object" );
is( $s->name, 'inline', "object has the right name" );
is( scalar $s->witnesses, 3, "object has three witnesses" );

## NOW
my $wit_a = $s->witness('A');
is( ref( $wit_a ), 'Text::Tradition::Witness', "Found a witness A" );
if( $wit_a ) {
    is( $wit_a->sigil, 'A', "Witness A has the right sigil" );
}
is( $s->witness('X'), undef, "There is no witness X" );
ok( !exists $s->{'witnesses'}->{'X'}, "Witness key X not created" );

## TODO implement witness sigil constraints and test where appropriate. The constraint is:
subtype 'Sigil',
	as 'Str',
	where { $_ =~ /\A$xml10_name_rx\z/ },
	message { 'Sigil must be a valid XML attribute string' };



######## Relationship tests
## NOW - test that local and non-local relationship addition and deletion works
my $cxfile = 't/data/Collatex-16.xml';
my $t = Text::Tradition->new( 
	'name'  => 'inline', 
	'input' => 'CollateX',
	'file'  => $cxfile,
	);
my $c = $t->collation;

my @v1 = $c->add_relationship( 'n21', 'n22', { 'type' => 'lexical' } );
is( scalar @v1, 1, "Added a single relationship" );
is( $v1[0]->[0], 'n21', "Got correct node 1" );
is( $v1[0]->[1], 'n22', "Got correct node 2" );
my @v2 = $c->add_relationship( 'n24', 'n23', 
	{ 'type' => 'spelling', 'scope' => 'global' } );
is( scalar @v2, 2, "Added a global relationship with two instances" );
@v1 = $c->del_relationship( 'n22', 'n21' );
is( scalar @v1, 1, "Deleted first relationship" );
@v2 = $c->del_relationship( 'n12', 'n13', 'everywhere' );
is( scalar @v2, 2, "Deleted second global relationship" );
my @v3 = $c->del_relationship( 'n1', 'n2' );
is( scalar @v3, 0, "Nothing deleted on non-existent relationship" );
my @v4 = $c->add_relationship( 'n24', 'n23', 
    { 'type' => 'spelling', 'scope' => 'global' } );
is( @v4, 2, "Re-added global relationship" );
@v4 = $c->del_relationship( 'n12', 'n13' );  # not everywhere
is( @v4, 1, "Only specified relationship deleted this time" );
ok( $c->get_relationship( 'n24', 'n23' ), "Other globally-added relationship exists" );

# Test 1.3: attempt relationship with a meta reading (should fail)
$t1 = Text::Tradition->new( 'input' => 'Self', 'file' => 't/data/legendfrag.xml' );
ok( $t1, "Parsed test fragment file" );
my $c1 = $t1->collation;
try {
	$c1->add_relationship( 'r8.1', 'r9.2', { 'type' => 'collated' } );
	ok( 0, "Allowed a meta-reading to be used in a relationship" );
} catch ( Text::Tradition::Error $e ) {
	is( $e->message, 'Cannot set relationship on a meta reading', 
		"Relationship link prevented for a meta reading" );
}

# Test 2.1: try to equate nodes that are prevented with a real intermediate
# equivalence
my $t2;
warnings_exist {
	$t2 = Text::Tradition->new( 'input' => 'Self', 'file' => 't/data/legendfrag.xml' );
} [qr/Cannot set relationship on a meta reading/],
	"Got expected relationship drop warning on parse";
my $c2 = $t2->collation;
$c2->add_relationship( 'r9.2', 'r9.3', { 'type' => 'lexical' } );
my $trel2 = $c2->get_relationship( 'r9.2', 'r9.3' );
is( ref( $trel2 ), 'Text::Tradition::Collation::Relationship',
	"Created blocking relationship" );
is( $trel2->type, 'lexical', "Blocking relationship is not a collation" );
# This time the link ought to fail
try {
	$c2->add_relationship( 'r8.6', 'r10.3', { 'type' => 'orthographic' } );
	ok( 0, "Added cross-equivalent bad relationship" );
} catch ( Text::Tradition::Error $e ) {
	like( $e->message, qr/witness loop/,
		"Existing equivalence blocked crossing relationship" );
}
## TODO implement recalculation of ranks!
try {
	$c2->calculate_ranks();
	ok( 1, "Successfully calculated ranks" );
} catch ( Text::Tradition::Error $e ) {
	ok( 0, "Collation now has a cycle: " . $e->message );
}

# Test 3.1: make a straightforward pair of transpositions.
my $t3 = Text::Tradition->new( 'input' => 'Self', 'file' => 't/data/lf2.xml' );
# Test 1: try to equate nodes that are prevented with an intermediate collation
my $c3 = $t3->collation;
try {
	$c3->add_relationship( 'r36.4', 'r38.3', { 'type' => 'transposition' } );
	ok( 1, "Added straightforward transposition" );
} catch ( Text::Tradition::Error $e ) {
	ok( 0, "Failed to add normal transposition: " . $e->message );
}
try {
	$c3->add_relationship( 'r36.3', 'r38.2', { 'type' => 'transposition' } );
	ok( 1, "Added straightforward transposition complement" );
} catch ( Text::Tradition::Error $e ) {
	ok( 0, "Failed to add normal transposition complement: " . $e->message );
}

# Test 3.2: try to make a transposition that could be a parallel.
try {
	$c3->add_relationship( 'r28.2', 'r29.2', { 'type' => 'transposition' } );
	ok( 0, "Added bad colocated transposition" );
} catch ( Text::Tradition::Error $e ) {
	like( $e->message, qr/Readings appear to be colocated/,
		"Prevented bad colocated transposition" );
}

# Test 3.3: make the parallel, and then make the transposition again.
try {
	$c3->add_relationship( 'r28.3', 'r29.3', { 'type' => 'orthographic' } );
	ok( 1, "Equated identical readings for transposition" );
} catch ( Text::Tradition::Error $e ) {
	ok( 0, "Failed to equate identical readings: " . $e->message );
}
try {
	$c3->add_relationship( 'r28.2', 'r29.2', { 'type' => 'transposition' } );
	ok( 1, "Added straightforward transposition complement" );
} catch ( Text::Tradition::Error $e ) {
	ok( 0, "Failed to add normal transposition complement: " . $e->message );
}

# Test 4: make a global relationship that involves re-ranking a node first, when 
# the prior rank has a potential match too
my $t4 = Text::Tradition->new( 'input' => 'Self', 'file' => 't/data/globalrel_test.xml' );
my $c4 = $t4->collation;
# Can we even add the relationship?
try {
	$c4->add_relationship( 'r463.2', 'r463.4', 
		{ type => 'orthographic', scope => 'global' } );
	ok( 1, "Added global relationship without error" );
} catch ( Text::Tradition::Error $e ) {
	ok( 0, "Failed to add global relationship when same-rank alternative exists: "
		. $e->message );
}
$c4->calculate_ranks();
# Do our readings now share a rank?
is( $c4->reading('r463.2')->rank, $c4->reading('r463.4')->rank, 
	"Expected readings now at same rank" );

######## Collation tests
my $cxfile = 't/data/Collatex-16.xml';
my $t = Text::Tradition->new( 
    'name'  => 'inline', 
    'input' => 'CollateX',
    'file'  => $cxfile,
    );
my $c = $t->collation;

my $rno = scalar $c->readings;
# Split n21 ('unto') for testing purposes into n21p0 ('un', 'join_next' => 1 ) and n21 ('to')...

# Combine n3 and n4 ( with his )
$c->merge_readings( 'n3', 'n4', 1 );
ok( !$c->reading('n4'), "Reading n4 is gone" );
is( $c->reading('n3')->text, 'with his', "Reading n3 has both words" );

# Collapse n9 and n10 ( rood / root )
$c->merge_readings( 'n9', 'n10' );
ok( !$c->reading('n10'), "Reading n10 is gone" );
is( $c->reading('n9')->text, 'rood', "Reading n9 has an unchanged word" );

# Try to combine n21 and n21p0. This should break.
my $remaining = $c->reading('n21');
$remaining ||= $c->reading('n22');  # one of these should still exist
try {
	$c->merge_readings( 'n21p0', $remaining, 1 );
	ok( 0, "Bad reading merge changed the graph" );
} catch( Text::Tradition::Error $e ) {
	like( $e->message, qr/neither concatenated nor collated/, "Expected exception from bad concatenation" );
} catch {
	ok( 0, "Unexpected error on bad reading merge: $@" );
}

try {
	$c->calculate_ranks();
	ok( 1, "Graph is still evidently whole" );
} catch( Text::Tradition::Error $e ) {
	ok( 0, "Caught a rank exception: " . $e->message );
}

## TODO again with the tabular / CSV input.
# Test right-to-left reading merge.
my $rtl = Text::Tradition->new( 
    'name'  => 'inline', 
    'input' => 'Tabular',
    'sep_char' => ',',
    'direction' => 'RL',
    'file'  => 't/data/arabic_snippet.csv'
    );
my $rtlc = $rtl->collation;
is( $rtlc->reading('r8.1')->text, 'سبب', "Got target first reading in RTL text" );
my $pt = $rtlc->path_text('A');
my @path = $rtlc->reading_sequence( $rtlc->start, $rtlc->end, 'A' );
is( $rtlc->reading('r9.1')->text, 'صلاح', "Got target second reading in RTL text" );
$rtlc->merge_readings( 'r8.1', 'r9.1', 1 );
is( $rtlc->reading('r8.1')->text, 'سبب صلاح', "Got target merged reading in RTL text" );
is( $rtlc->path_text('A'), $pt, "Path text is still correct" );
is( scalar($rtlc->reading_sequence( $rtlc->start, $rtlc->end, 'A' )), 
	scalar(@path) - 1, "Path was shortened" );
}

## Collation correction tests
my $st = Text::Tradition->new( 'input' => 'Self', 'file' => 't/data/collatecorr.xml' );
is( ref( $st ), 'Text::Tradition', "Got a tradition from test file" );
ok( $st->has_witness('Ba96'), "Tradition has the affected witness" );

my $sc = $st->collation;
my $numr = 17;
ok( $sc->reading('n131'), "Tradition has the affected reading" );
is( scalar( $sc->readings ), $numr, "There are $numr readings in the graph" );
is( $sc->end->rank, 14, "There are fourteen ranks in the graph" );

# Detach the erroneously collated reading
my( $newr, @del_rdgs ) = $sc->duplicate_reading( 'n131', 'Ba96' );
ok( $newr, "New reading was created" );
ok( $sc->reading('n131_0'), "Detached the bad collation with a new reading" );
is( scalar( $sc->readings ), $numr + 1, "A reading was added to the graph" );
is( $sc->end->rank, 10, "There are now only ten ranks in the graph" );

# Check that the bad transposition is gone
is( scalar @del_rdgs, 1, "Deleted reading was returned by API call" );
is( $sc->get_relationship( 'n130', 'n135' ), undef, "Bad transposition relationship is gone" );

# The collation should not be fixed
my @pairs = $sc->identical_readings();  # in the Java this is "reading/couldbeidenticalreadings/..." and the pair *should* be found.
is( scalar @pairs, 0, "Not re-collated yet" );
# Fix the collation
ok( $sc->merge_readings( 'n124', 'n131_0' ), "Collated the readings correctly" );
@pairs = $sc->identical_readings( start => 'n124', end => $csucc->id );
is( scalar @pairs, 3, "Found three more identical readings" );
is( $sc->end->rank, 11, "The ranks shifted appropriately" );
$sc->flatten_ranks();
is( scalar( $sc->readings ), $numr - 3, "Now we are collated correctly" );

# Check that we can't "duplicate" a reading with no wits or with all wits
try {
	my( $badr, @del_rdgs ) = $sc->duplicate_reading( 'n124' );
	ok( 0, "Reading duplication without witnesses throws an error" );
} catch( Text::Tradition::Error $e ) {
	like( $e->message, qr/Must specify one or more witnesses/, 
		"Reading duplication without witnesses throws the expected error" );
} catch {
	ok( 0, "Reading duplication without witnesses threw the wrong error" );
}

try {
	my( $badr, @del_rdgs ) = $sc->duplicate_reading( 'n124', 'Ba96', 'Mü11475' );
	ok( 0, "Reading duplication with all witnesses throws an error" );
} catch( Text::Tradition::Error $e ) {
	like( $e->message, qr/Cannot join all witnesses/, 
		"Reading duplication with all witnesses throws the expected error" );
} catch {
	ok( 0, "Reading duplication with all witnesses threw the wrong error" );
}

try {
	$sc->calculate_ranks();
	ok( 1, "Graph is still evidently whole" );
} catch( Text::Tradition::Error $e ) {
	ok( 0, "Caught a rank exception: " . $e->message );
}

## TODO add output as adjacency list, as tabular, as TEI, etc. etc.

## TODO we need an alignment table output! And for it to be tested.


