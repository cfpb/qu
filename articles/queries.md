---
title: "CFPB - Qu: Query API"
layout: article
---

## Query API

### Endpoints

The endpoint for getting all datasets is `/data`. For convenience, the
root (`/`) also acts as this endpoint.

Each dataset has an endpoint at `/data/<dataset-name>`. This endpoint
gives all information about a dataset needed to query it. Each dataset
has several _slices_ representing views of the dataset. These are
analogous to tables in a relational database.

Each slice has an endpoint at `/data/<dataset-name>/<slice-name>`.

All endpoints can have an optional filename extension, so accessing a
slice could use any of the following example URLs:

```
/data/census/counties
/data/census/counties.html
/data/census/counties.csv
```

If the MIME type corresponding to the extension is available, it will
be served. If an extension is not used, the request's Accept header
will be used to determine what MIME type to serve.

## Query Language

### Dimensions

### Clauses

### $where in detail

## Data formats

