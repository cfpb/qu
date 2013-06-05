# qu

[![Build Status](https://travis-ci.org/cfpb/qu.png)](https://travis-ci.org/cfpb/qu)

_qu_ is an **in-progress** data platform created by the [CFPB][] to
serve their public data sets.

The goals of this platform are to:
* Import data in our
  [Google-Dataset-inspired format][dataset-inspired]
* Query data using our
  [Socrata-Open-Data-API][soda]-inspired API
* Export data in JSON or CSV format

[CFPB]: http://www.consumerfinance.gov/
[dataset-inspired]: https://github.com/cfpb/qu/wiki/Dataset-publishing-format
[soda]: http://dev.socrata.com/consumers/getting-started/

## Getting Started

### Prerequisites

In order to work on _qu_, you need the following languages and tools
installed:

* [Java][]
* [Node.js][]
* [Leiningen][]
* [Grunt][]
* [Bower][]
* [MongoDB][]

[Java]: http://www.java.com/en/
[Node.js]: http://nodejs.org/
[Leiningen]: http://leiningen.org/
[Grunt]: http://gruntjs.com/
[Bower]: http://twitter.github.com/bower/
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

If editing the JavaScript or CSS, run the following to watch the JS
and CSS and make sure your changes are compiled:

```sh
grunt watch
```

You can run `grunt` to compile the files once.

To start a Clojure REPL to work with the software, run:

```sh
lein repl
```

In order to run the API as a web server, run:

```sh
lein ring server
```

To auto-reload templates during development, run:

```sh
DEBUG=1 lein ring server
```

Go to http://localhost:3000 and you should see the app running.

Before starting the API, you will want to start MongoDB and load some
data into it. Currently, _qu_ only supports connecting to a local
MongoDB connection.

### Configuration

In development mode, the application will connect to your local MongoDB server. In production, or if you want to connect to a different Mongo server in dev, you will have to specify the Mongo host and port.

You can do this via setting environment variables:

```sh
MONGO_HOST=192.168.21.98
MONGO_PORT=27017
```

You can also do this via setting Java system properties:

```sh
java -jar qu.jar -Dmongo.host=192.168.21.98 -Dmongo.port=27017
```


### Loading data

Make sure you have MongoDB started. To load some sample data, run
`lein repl` and enter the following:

```clojure
(require 'cfpb.qu.loader)
(in-ns 'cfpb.qu.loader)
(ensure-mongo-connection)
(load-dataset "county_taxes")
(load-dataset "census") ; Takes quite a while to run; can skip.
(mongo/disconnect!)
```

### Testing

We use [Midje](https://github.com/marick/Midje) to test this project,
so to execute the tests, run:

```sh
lein midje
```

If you want the tests to automatically run whenever you change the
code, eliminating the JVM startup time and generally being great, run:

```sh
lein midje :autotest
```

We also have integration tests that run tests against a Mongo database.
To run these tests:

```sh
lein with-profile integration embongo midje
```
