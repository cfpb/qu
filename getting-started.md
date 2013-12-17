---
title: "Getting Started"
layout: default
---

## Getting started

### Prerequisites

In order to install and use _qu_, you need the following languages and
tools installed:

* [Java][]
* [Node.js][]
* [Leiningen][]
* [MongoDB][]

[Java]: http://www.java.com/en/
[Node.js]: http://nodejs.org/
[Leiningen]: http://leiningen.org/
[MongoDB]: http://www.mongodb.org/

### Setup

Once you have the prerequisites installed and the code downloaded and
expanded into a directory (which we will call "qu"), run the following
commands:

```sh
cd qu
lein deps
npm install -g grunt-cli bower
npm install && bower install
grunt
```

To start a Clojure REPL to work with the software, run:

```sh
lein repl
```

In order to run the API as a web server, run:

```sh
lein run
```

Go to http://localhost:3000 and you should see the app running.

Before starting the API, you will want to start MongoDB and load some
data into it. Currently, _qu_ only supports connecting to a local
MongoDB connection.

### Loading data

Make sure you have MongoDB started. To load some sample data, run
`lein repl` and enter the following:

```clojure
(require 'cfpb.qu.loader)
(in-ns 'cfpb.qu.loader)
(mongo/connect!)
(load-dataset "county_taxes")
(load-dataset "census") ; Takes quite a while to run; can skip.
(mongo/disconnect!)
```

You can find out more about loading your own data in the
[loading data](loading_data.html) section.
