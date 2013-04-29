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
	echo "Loan Type,HUD Median Family Income,Agency Code,As of Year,Respondent ID,Occupancy,Co Applicant Ethnicity,HOEPA Status,Applicant Race 2,Purchaser Type,Edit Status,MSA/MD,Applicant Income (000s),Sequence Number,Rate Spread,Minority Population %,Co Applicant Race 4,Co Applicant Race 5,Application Date Indicator,Property Type,Census Tract Number,Co Applicant Race 2,Co Applicant Race 3,State Code,Number of Owner-occupied units,Co Applicant Sex,Tract to MSA/MD Income %,Applicant Ethnicity,Number of 1-to 4-Family units,Loan Amount (000s),Denial Reason 1,Denial Reason 2,Denial Reason 3,County Code,Applicant Sex,Applicant Race 5,Applicant Race 4,Applicant Race 1,Preapproval,Applicant Race 3,Co Applicant Race 1,Action Type,Lien Status,Loan Purpose,Population" >headers.csv


clean_csv:
	rm *.csv

clean_all: clean
	rm *.csv *.zip



