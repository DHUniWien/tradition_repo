#!/usr/bin/env perl

use strict;
use warnings;
use feature 'say';
use Text::CSV;

binmode STDOUT, ':utf8';

my ($filename, $witness) = @ARGV;
my $csv = Text::CSV->new({binary => 1, empty_is_undef => 1});

open my $fh, "<:encoding(utf8)", $filename or die "Could not open $filename to read: $!";
$csv->column_names($csv->getline( $fh ));
my @readings;
while ( my $row = $csv->getline_hr( $fh ) ) {
	my $word = $row->{$witness};
	push(@readings, $word) if $word;
}
close $fh;
say join(' ', @readings);