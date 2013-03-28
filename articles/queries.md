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

<table class="table table-bordered table-striped">
<thead>
<tr>
<th>Clause</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>$select</code></td>
<td>Which columns to return. If not specified, all columns will be returned.</td>
</tr>
<tr>
<td><code>$where</code></td>
<td>Filter the results. This uses the <a href="#where_in_detail">WHERE query syntax</a>. If not specified, the results will not be filtered.</td>
</tr>
<tr>
<td><code>$orderBy</code></td>
<td>Order to return the results. If not specified, the order will be consistent, but unspecified.</td>
</tr>
<tr>
<td><code>$group</code></td>
<td><strong>TODO</strong>: Column to group results on.</td>
</tr>
<tr>
<td><code>$limit</code></td>
<td>Maximum number of results to return. If not specified, this defaults to 1000. <strong>TODO</strong>: This has a hard limit of 1000.</td>
</tr>
<tr>
<td><code>$offset</code></td>
<td>Offset into the results to start at. If not specified, this defaults to 0.</td>
</tr>
<tr>
<td><code>$q</code></td>
<td><strong>TODO</strong>: This will do a full-text search for a value within the row's dimensions.</td>
</tr>
<tr>
<td><code>$page</code></td>
<td><strong>TODO</strong>: The page of results to return. If not specified, this defaults to 1. If <code>$offset</code> is given, <code>$page</code> is ignored.</td>
</tr>
<tr>
<td><code>$perPage</code></td>
<td><strong>TODO</strong>: How many results to return per page. This is a synonym for <code>$limit</code>.</td>
</tr>
<tr>
<td><code>$callback</code></td>
<td><strong>TODO</strong>: The name of the callback function used in a JSONP query. Only used with JSONP.</td>
</tr>
</tbody>
</table>

### <tt>$where</tt> in detail

The `$where` clause supports a mini-language for writing queries. This language is a subset of SQL WHERE clauses, with the addition of function support.

A `$where` clauses is made up of one or more _comparisons_, joined by _boolean operators_.

#### Comparisons

A comparison is _always_ between a column and a value. You cannot compare two columns.

<table class="table table-bordered table-striped"><thead>
<tr>
<th>Operator</th>
<th>Description</th>
<th>Example</th>
</tr>
</thead><tbody>
<tr>
<td><code>=</code></td>
<td>equality</td>
<td><code>name="Phillip"</code></td>
</tr>
<tr>
<td><code>!=</code></td>
<td>inequality</td>
<td><code>state != "Alaska"</code></td>
</tr>
<tr>
<td><code>&gt;</code></td>
<td>greater than</td>
<td><code>age &gt; 18</code></td>
</tr>
<tr>
<td><code>&gt;=</code></td>
<td>greater than or equal</td>
<td><code>square_miles &gt;= 1000</code></td>
</tr>
<tr>
<td><code>&lt;</code></td>
<td>less than</td>
<td><code>age &lt; 18</code></td>
</tr>
<tr>
<td><code>&lt;=</code></td>
<td>less than or equal</td>
<td><code>square_miles &lt;= 1000</code></td>
</tr>
<tr>
<td><code>LIKE</code></td>
<td><strong>TODO:</strong> string matching</td>
<td><code>name LIKE "Pete%"</code> (would match "Pete", "Peter", or anything that starts with "Pete")</td>
</tr>
<tr>
<td><code>ILIKE</code></td>
<td><strong>TODO:</strong> case-insensitive string matching</td>
<td><code>name ILIKE "%rick"</code> (would match "Rick" as well as "Yorick", "Harrick", or anything else with "rick" in it)</td>
</tr>
<tr>
<td><code>IS NULL</code></td>
<td>existence of a value</td>
<td><code>city IS NULL</code></td>
</tr>
<tr>
<td><code>IS NOT NULL</code></td>
<td>non-existence of a value</td>
<td><code>city IS NOT NULL</code></td>
</tr>
</tbody></table>

For string matching, `%` matches zero-or-more characters, while `_` matches exactly one character. These are _wildcards_, so any character matches them.

#### Boolean operators

<table class="table table-bordered table-striped"><thead>
<tr>
<th>Operator</th>
<th>Description</th>
<th>Example</th>
</tr>
</thead><tbody>
<tr>
<td><code>AND</code></td>
<td>logical AND of two comparisons</td>
<td><code>state = "Alaska" AND age &gt; 18</code></td>
</tr>
<tr>
<td><code>OR</code></td>
<td>logical OR of two comparisons</td>
<td><code>state = "Alaska" OR state = "Hawaii"</code></td>
</tr>
<tr>
<td><code>NOT</code></td>
<td>negation of a comparison</td>
<td><code>NOT (state = "Alaska" OR state = "Hawaii")</code></td>
</tr>
<tr>
<td><code>()</code></td>
<td>grouping for order of operations</td>
<td><code>(state = "Alaska" OR state = "Hawaii") AND age &gt; 18</code></td>
</tr>
</tbody></table>

Without parentheses, boolean operators are evaluated left-to-right with NOT binding only to the next comparison.

### <tt>$orderBy</tt>

The `$orderBy` clause determines the order of the results returned. This takes a list of columns, separated by commas, with an optional suffix of `desc` to indicate that you want the data in descending order.

Examples:

```
$orderBy=age
$orderBy=state, square_miles
$orderBy=age desc, gender
```
