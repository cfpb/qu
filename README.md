# qu

:warning: **This project was archived on September 25th, 2020 and is no longer maintained** :warning:


---
---
---





_qu_ is a data platform created to serve our public data sets. You can use it to serve your data sets, as well.

The goals of this platform are to:
* Import data in our
  [Google-Dataset-inspired format][dataset-inspired]
* Query data using our
  [Socrata-Open-Data-API][soda]-inspired API
* Export data in JSON or CSV format

[CFPB]: http://www.consumerfinance.gov/
[dataset-inspired]: https://github.com/cfpb/qu/wiki/Dataset-publishing-format
[soda]: http://dev.socrata.com/consumers/getting-started/

## Developing with Vagrant

If you are using Vagrant, life is clean and easy for you! Go to [our Vagrant documentation](doc/vagrant.md) to get started.

## Getting started without Vagrant

### Prerequisites

In order to work on _qu_, you need the following languages and tools
installed:

* [Node.js][]
* [Grunt][]
* [Bower][]
* [Java][]
* [Leiningen][]
* [MongoDB][]

[Java]: http://www.java.com/en/
[Node.js]: http://nodejs.org/
[Leiningen]: http://leiningen.org/
[Grunt]: http://gruntjs.com/
[Bower]: http://bower.io/
[MongoDB]: http://www.mongodb.org/

### Setup

#### Front-end assets

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

#### Vagrant

Start a VM by running `vagrant up`. Provisioning will take a few minutes.

After a VM is started, you should be able to run `vagrant ssh` to SSH to the VM. Then run:

```
cd /vagrant
```

to change the working directory to the Qu codebase.

#### Clojure

To start a Clojure REPL to work with the software, run:

```sh
lein repl
```

In order to run the API as a web server, run:

```sh
lein run
```

Go to http://localhost:3000 (or http://localhost:3333 if using Vagrant) and you should see the app running.

Before starting the API, you will want to start MongoDB and load some
data into it.

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

You can also do this in the `QU_CONFIG` config file:

```clojure
{ :http-ip "0.0.0.0"
:http-port 3000
:http-threads 50 }
```

#### MongoDB

In development mode, the application will connect to your local MongoDB server. In production, or if you want to connect to a different Mongo server in dev, you will have to specify the Mongo host and port.

You can do this via setting environment variables:

```sh
MONGO_HOST=192.168.21.98
MONGO_PORT=27017
```

You can also do this in the `QU_CONFIG` config file:

```clojure
{ :mongo-host "192.168.21.98"
:mongo-port 27017 }
```

If you prefer to connect via a URI, use `MONGO_URI`.

If you need to connect to several servers to read from multiple replica sets, set specific Mongo options, or authenticate, you will have to set your configuration in a file as specified under `QU_CONFIG`. Your configuration should look like the following:

```clojure
{
  ;; General settings
  :http-ip "0.0.0.0"
  :http-port 3000
  :http-threads 50

  ;; Set a vector of vectors, each made up of the IP address and port.
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
  :mongo-auth {
    :admin ["admin-user" "s3cr3t"]
    :slicename ["admin-user" "s3cr3t"]
    :metadata ["admin-user" "s3cr3t"]
    :query_cache ["admin-user" "s3cr3t"]}
}
```

See [the Monger documentation for all available Mongo connection options](http://clojuremongodb.info/articles/connecting.html#connecting_to_mongodb_using_connection_options).

#### StatsD

The application can generate metrics related to its execution and send them to statsd. 

However by default metrics publishing is disabled. To enable it you need to provide statsd hostname in the configuration file:

```clojure
{
  :statsd-host "localhost"
  ;; Standard statsd port
  :statsd-port 8125
}
```

#### App URL

To control the HREF of the links that are created for data slices, you can set the APP_URL environment variable.

For example, given a slice at `/data/a_resource/a_slice`, setting the APP_URL variable like so

```sh
APP_URL=https://my.data.platform/data-api
```

will create links such as

```sh
_links":[{"rel":"self","href":"https://my.data.platform/data-api/data/a_resource/a_slice.json?...."}]
```

when emitted in JSON, JSONP, XML, and so on.

If the variable is not set, then relative HREFs such as `/data/a_resource/a_slice.json` are used. This variable is most useful in production hosting situations where an application server is behind a proxy, and you wish to granularly control the HREFs that are created independent of how the application server sees the request URI.

#### API Name

In order for your API to show a custom name (such as "Spiffy Lube
API"), set the `API_NAME` environment variable. This is probably best
set in an external config file.

### Loading data

Make sure you have MongoDB started. To load some sample data, run
`lein repl` and enter the following:

```clojure
(go)
(load-dataset "census") ; Takes quite a while to run; can skip.
(stop)
```

### Testing

To execute the project's tests, run:

```sh
lein test
```

We also have integration tests that run tests against a Mongo database.
To run these tests:

```sh
lein with-profile integration embongo test
```

or, even more easily:

```sh
lein inttest
```

### Nginx

We recommend serving Qu behind a proxy. Nginx works well for this, and
there is a [sample configuration file](doc/nginx.conf) available.
