### HMDA Datase

To load data, first use make to retrieve the datasets from the [FFIEC website](http://www.ffiec.gov/hmda/hmdaproducts.htm).



To load all data from 2007-2011:
```
make
```

To a sample roughly 1/50 of the size of the full dataset:
```
make sample
```

Then load as described on the main docs
```clojure
(require 'cfpb.qu.loader)
(in-ns 'cfpb.qu.loader)
(mongo/connect!)
(load-dataset "hmda_lar")
(mongo/disconnect!)
```
