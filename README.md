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

## Getting started

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
[Bower]: http://bower.io/
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
lein run
```

Go to http://localhost:3000 and you should see the app running.

Before starting the API, you will want to start MongoDB and load some
data into it. Currently, _qu_ only supports connecting to a local
MongoDB connection.

### Configuration

All the settings below are shown via environment variables, but they
can also be set via Java properties. See
[the documentation for environ][https://github.com/weavejester/environ/blob/master/README.md]
for more information on how to use Java properties if you prefer.

#### Configuration file

Besides using environment variables, you can also use a configuration
file. This file must contain a Clojure map with your configuration set
in it. Unlike with environment variables, where each setting is
uppercased and SNAKE_CASED, these settings must be lowercase keywords
with dashes, like so:

```clojure
{ :http-port 8080
  :mongo-host "127.0.0.1" }
```

In order to use a configuration file, set `QU_CONFIG` to the file's
location, like so:

```sh
QU_CONFIG=/etc/qu-conf.clj
```

Note that the configuration file overrides environment variables.

#### HTTP server

By default, the server will come up on port 3000 and 4 threads will be
allocated to handle requests. The server will be bound to
localhost. You can change these settings via environment variables:

```sh
HTTP_IP=0.0.0.0
HTTP_PORT=3000
HTTP_THREADS=4
```

#### MongoDB

In development mode, the application will connect to your local MongoDB server. In production, or if you want to connect to a different Mongo server in dev, you will have to specify the Mongo host and port.

You can do this via setting environment variables:

```sh
MONGO_HOST=192.168.21.98
MONGO_PORT=27017
```

If you prefer to connect via a URI, use `MONGO_URI`.

If you need to connect to several servers to read from multiple replica sets, set specific Mongo options, or authenticate, you will have to set your configuration in a file as specified under `QU_PROJECT`. Your configuration should look like the following:

```clojure
{ ;; Set a vector of vectors, each made up of the IP address and port.
  :mongo-hosts [["127.0.0.1" 27017] ["192.168.1.1" 27017]]
  
  ;; Mongo options should be in a map.
  :mongo-options {:connections-per-host 20
                  :connect-timeout 60}
                  
  ;; Authentication should be a map of database names to vectors containing username and password.
  ;; If you have a user on the admin database with the roles "readWriteAnyDatabase", that user should
  ;; work for running the entire API. To load data, that user needs the roles "clusterAdmin" and
  ;; "dbAdminAnyDatabase" as well.
  ;; If you choose not to have a user on the admin database, you will need a user for every dataset
  ;; and for the "metadata" database.
  :mongo-auth {:admin ["admin" "s3cr3t"]}
}
```

See [the Monger documentation for all available Mongo connection options](http://clojuremongodb.info/articles/connecting.html#connecting_to_mongodb_using_connection_options).

#### APP URL

To control the HREF of the links that are created for data slices, you can set the APP_URL environment variable.

For example, given a slice at `/data/a_resource/a_slice`, setting the APP_URL variable like so

```sh
APP_URL=https://my.data.platform/data-api
```

will create links such as

```sh
_links":[{"rel":"self","href":"https://my.data.platform/data-api/data/a_resource/a_slice.json? ....
```

when emitted in JSON, JSONP, XML, and so on.

If the variable is not set, then absolute HREFs such as `/data/a_resource/a_slice.json` are used. This variable is most useful in production hosting situations where an application server is behind a proxy, and you wish to granularly control the HREFs that are created independent of how the application server sees the request URI.

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
