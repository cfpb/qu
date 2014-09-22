# Developing with Vagrant

With Vagrant, everything you need for Qu should be isolated on a virtual machine that you can develop on.

## Getting started

After downloading the code via git like so:

```sh
git clone https://github.com/cfpb/qu.git
cd qu
```

you should be able to run the following command to build your virtual machine:

```sh
vagrant up --provision
```

**NOTE:** This will take a very long time the first time. Plan a coffee break.

Once this is built, you should be able to run:

```sh
vagrant ssh
cd /vagrant
```

which will put you into a shell on the virtual machine in the Qu working directory. All other commands in this documentation are run on the virtual machine in this directory.


## Front-end assets

Front-end assets for Qu are managed by Bower and Grunt. The first time you start working with Qu, you will need to install the front-end dependencies like so:

```sh
npm install && bower install
grunt
```

From then on, if editing the JavaScript or CSS, run the following to watch the JS
and CSS and make sure your changes are compiled:

```sh
grunt watch
```

You can run `grunt` again to compile the files once.

## Loading data

You will need to load some data to get started working with Qu. At the shell, run:

```
lein repl
```

This will start the Clojure REPL, a shell, that you can run Clojure commands in. Run the following:

```clojure
(go)
(load-dataset "county_taxes")
(stop)
```

After that, you can type `Ctrl+D` to leave the Clojure REPL. You are ready to work.

## Running the API

In order to run the API as a web server, run:

```sh
lein run
```

To start a Clojure REPL to work with the software, run:

```sh
lein repl
```

Inside the REPL, you can run the following commands to start and stop the app:

```clojure
(go) ;; starts the app
(stop) ;; stops the app
(reset) ;; resets a running app, reloading all the code
```


Go to http://localhost:3333 on your machine and you should see the app running. This is running on port 3000 on the virtual machine if you need to check it from there.

## Testing

To execute the project's tests, run:

```sh
lein test
```

We also have integration tests that run tests against a Mongo database.
To run these tests:

```sh
lein inttest
```
