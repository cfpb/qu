---
title: "API Endpoints"
layout: default
---

## Endpoints

### Datasets

The endpoint for getting all datasets is `/data`. For convenience, the root (`/`) also acts as this endpoint.

Each dataset has an endpoint at `/data/<dataset-name>`. This endpoint gives all information about a dataset needed to query it. Each dataset has several _slices_ representing views of the dataset. These are analogous to tables in a relational database. Each dataset also has _concepts_, which are definitions of types of data that can be in a slice. Some concept definitions just include a descriptive name, but the more interesting ones contain a table of data that can be used during data load to join information into a slice.

### Slices

Each slice has an endpoint at `/data/<dataset-name>/slice/<slice-name>`. This endpoint returns data from the slice. See [the query API](queries.html) to find out more about how this works.

Each slice also has an endpoint at `/data/<dataset-name>/slice/<slice-name>/metadata`. This endpoint returns all the metadata about a slice.

### Concepts

Each concept has an endpoint at `/data/<dataset-name>/concept/<concept-name>`.

## MIME Types and Extensions

All endpoints can have an optional filename extension, so accessing a slice could use any of the following example URLs:

```
/data/census/slice/population_estimates
/data/census/slice/population_estimates.html
/data/census/slice/population_estimates.csv
```

If the MIME type corresponding to the extension is available, it will be served. If an extension is not used, the request's Accept header will be used to determine what MIME type to serve.

All endpoints should serve HTML, XML, JSON, and JSONP. In addition, slice data can be served as CSV.

### JSONP

By default, the callback used in JSONP responses is `callback`. If you want to specify the callback, add a GET parameter called `$callback` to the request with the name of your callback.
