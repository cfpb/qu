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

#### Server port and threads

By default, the server will come up on port 3000 and 4 threads will be
allocated to handle requests. You can change these settings via
environment variables:

```sh
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
