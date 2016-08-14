YES
===

First download everything we need for Jetty and Jersey. This is an entire web server and the stuff to make nice web services.

It's a bash script with loads of wget commands, that places the jars into the directory dependencies. 

I'm being awkward and not using gradle or whatever.

    bash deps.bash

Then use that in your classpath argument to scala.

    scala -cp "dependencies/*" rest.scala

In another terminal window, let's call it.

    curl localhost:8901/hello

You should see some output.
