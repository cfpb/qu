# Data download and preparation steps expressed in this file.
#
# `make` by itself gets you the full dataset
# `make sample` will give you a 1/10 sample that will be easier to work with


full: hmda_lar_all_2011.csv hmda_lar_all_2010.csv hmda_lar_all_2009.csv
	ln -sf hmda_lar_all_2011.csv hmda_lar_2011.csv
	ln -sf hmda_lar_all_2010.csv hmda_lar_2010.csv
	ln -sf hmda_lar_all_2009.csv hmda_lar_2009.csv
	head hmda_lar_20*.csv > report.csv

sample: hmda_lar_sample_2011.csv hmda_lar_sample_2010.csv hmda_lar_sample_2009.csv
	ln -sf hmda_lar_sample_2011.csv hmda_lar_2011.csv
	ln -sf hmda_lar_sample_2010.csv hmda_lar_2010.csv
	ln -sf hmda_lar_sample_2009.csv hmda_lar_2009.csv
	head hmda_lar_20*.csv > report.csv

hmda_lar_2011.zip:
	wget http://www.ffiec.gov/hmdarawdata/LAR/National/2011HMDALAR%20-%20National.zip
	mv 2011HMDALAR\ -\ National.zip hmda_lar_2011.zip

hmda_lar_2010.zip:
	wget http://www.ffiec.gov/hmdarawdata/LAR/National/2010HMDALAR%20-%20National.zip
	mv 2010HMDALAR\ -\ National.zip hmda_lar_2010.zip

hmda_lar_2009.zip:
	wget http://www.ffiec.gov/hmdarawdata/LAR/National/2009HMDALAR%20-%20National.zip
	mv 2009HMDALAR\ -\ National.zip hmda_lar_2009.zip

hmda_lar_all_2011.csv: hmda_lar_2011.zip headers.csv
	cp headers.csv hmda_lar_all_2011.csv
	unzip -p hmda_lar_2011.zip  >> hmda_lar_all_2011.csv

hmda_lar_all_2010.csv: hmda_lar_2010.zip headers.csv
	cp headers.csv hmda_lar_all_2010.csv
	unzip -p hmda_lar_2010.zip  >> hmda_lar_all_2010.csv

hmda_lar_all_2009.csv: hmda_lar_2009.zip headers.csv
	cp headers.csv hmda_lar_all_2009.csv
	unzip -p hmda_lar_2009.zip  >> hmda_lar_all_2009.csv

hmda_lar_sample_2011.csv: hmda_lar_2011.zip headers.csv
	cp headers.csv hmda_lar_sample_2011.csv
	unzip -p hmda_lar_2011.zip  | ../../bin/sample.py >> hmda_lar_sample_2011.csv

hmda_lar_sample_2010.csv: hmda_lar_2010.zip headers.csv
	cp headers.csv hmda_lar_sample_2010.csv
	unzip -p hmda_lar_2010.zip  | ../../bin/sample.py >> hmda_lar_sample_2010.csv

hmda_lar_sample_2009.csv: hmda_lar_2009.zip headers.csv
	cp headers.csv hmda_lar_sample_2009.csv
	unzip -p hmda_lar_2009.zip  | ../../bin/sample.py >> hmda_lar_sample_2009.csv

headers.csv:
	echo "As of Year,Respondent ID,Agency Code,Loan Type,Property Type,Loan Purpose,Occupancy,Loan Amount (Thousands),Preapproval,Action Type,MSA/MD,State Code,County Code,Census Tract Number,Applicant Ethnicity,Co Applicant Ethnicity,Applicant Race 1,Applicant Race 2,Applicant Race 3,Applicant Race 4,Applicant Race 5,Co Applicant Race 1,Co Applicant Race 2,Co Applicant Race 3,Co Applicant Race 4,Co Applicant Race 5,Applicant Sex,Co Applicant Sex,Applicant Income (Thousands),Purchaser Type,Denial Reason 1,Denial Reason 2,Denial Reason 3,Rate Spread,HOEPA Status,Lien Status,Edit Status,Sequence Number,Population,Minority Population %,HUD Median Family Income,Tract to MSA/MD Income %,Number of Owner-Occupied Units,Number of 1-to-4-Family Units,Application Date Indicator" > headers.csv

clean_csv:
	rm *.csv

clean_all: clean
	rm *.csv *.zip



