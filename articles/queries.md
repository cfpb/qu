---
title: "CFPB - Qu: Query API"
layout: article
---

## Query API

### Endpoints

The endpoint for getting all datasets is `/data`. For convenience, the root (`/`) also acts as this endpoint.

Each dataset has an endpoint at `/data/<dataset-name>`. This endpoint gives all information about a dataset needed to query it. Each dataset has several _slices_ representing views of the dataset. These are analogous to tables in a relational database.

Each slice has an endpoint at `/data/<dataset-name>/<slice-name>`.

All endpoints can have an optional filename extension, so accessing a slice could use any of the following example URLs:

```
/data/census/population_estimates
/data/census/population_estimates.html
/data/census/population_estimates.csv
```

If the MIME type corresponding to the extension is available, it will be served. If an extension is not used, the request's Accept header will be used to determine what MIME type to serve.

**TODO**: Only slices currently work with file extensions.

## Query Language

Our query language is based on Socrata's [SoQL][] language. Queries are simple GET parameters sent to a slice endpoint.

[SoQL]: http://dev.socrata.com/consumers/getting-started#queryingwithsoql

### Dimensions

Certain fields of a slice are _dimensions_. Dimensions are usually fields that have one of a series of values, such as a `state` field having one of the 50 US States, or a `marital_status` field having Single, Widowed, Divorced, or Separated.

To perform an equality query on a dimension, use the dimension name as a GET parameter with your filter as the value. To query the census population estimates with a `state` of `Nebraska`, you would use this query:

```
/data/census/population_estimates?state=Nebraska
```

### Clauses

Most queries will involve more than simple equality queries. For these, we have a set of _clauses_ you can use.

| Clause     | Description |
| ---------- | ----------- |
| `$select`  | Which columns to return. If not specified, all columns will be returned. |
| `$where`   | Filter the results. This uses the [WHERE query syntax][]. If not specified, the results will not be filtered. |
| `$orderBy` | Order to return the results. If not specified, the order will be consistent, but unspecified. |
| `$group`   | **TODO**: Column to group results on. |
| `$limit`   | Maximum number of results to return. If not specified, this defaults to 1000. **TODO**: This has a hard limit of 1000. |
| `$offset`  | Offset into the results to start at. If not specified, this defaults to 0. |
| `$q`       | **TODO**: This will do a full-text search for a value within the row's dimensions. |
| `$page`    | **TODO**: The page of results to return. If not specified, this defaults to 1. |
| `$perPage` | **TODO**: How many results to return per page. This is a synonym for `$limit`. |

[WHERE query syntax]: #where_in_detail

### $where in detail

## Data formats

