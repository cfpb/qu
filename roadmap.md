---
title: Roadmap
published: true
layout: default
---

This roadmap is provisional and pre-decisional. It may change at any time, but reflects current plans.

## Recent Changes

* Turned Qu into a library that can be used to create new data APIs.
  * Added a [Leiningen template](https://github.com/qu-platform/lein-template) for creating new Qu instances.
* Made HTML templates customizable per API.

## Near Future

* Allow for pluggable data sources
  * MongoDB
    - MongoDB 2.6 - move back to aggregation framework
    - MongoDB with compression vs without compression
  * Postgres
  * In-memory
* Allow for pluggable output types
  * JSON
  * JSONP
  * XML
  * CSV
  * All of these are currently supported, but we want to make them pluggable.
* Have VMs available for download to try out Qu.
* Create a publicly available deployment strategy guide.

## Next Steps

* Data loading improvements
  * Simplify data definition format
  * Allow for incremental data loads
  * Allow for data loads via a "data package"
* Show common queries/filters/aggregations to end users  
* Admin dashboard
  * Track usage and hot queries
  * Add new datasets through dashboard
  * Identify useful indexes based on query logs

## Possibilities

* API keys
  * Rate limiting
  * Data access controls
* Graphical query generator
* D3-based data visualizations
* Make a graphical dataset definition generator
  * Look at how chart.io loads data
* API versioning
* API client libraries in multiple languages
