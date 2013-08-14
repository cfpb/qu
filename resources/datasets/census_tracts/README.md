The data in census_tracts.csv was derived from the publicly available HMDA data from 2007-2011.

After loading the ../hmda dataset, run:

```
mongo hmda tracts.json > census_tracts.csv --quiet
```

Fetch that file and then run the census_tract dataload.

Ideally, this would instead run as a derived table in the HMDA dataset. Performing the aggregation on unindexed data is too slow for this to be feasible. Future work may see post-index derived table creation, at which time this becomes an option.