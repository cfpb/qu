#!/usr/bin/python

import sys,random,argparse

parser = argparse.ArgumentParser(description='Sample size.')
parser.add_argument('-n','--numerator',type=int,default=1)
parser.add_argument('-d','--denominator',type=int,default=10)
args = parser.parse_args()

sample_size = float(args.numerator)/float(args.denominator)

for line in sys.stdin:
	if random.random() < sample_size:
		sys.stdout.write(line)