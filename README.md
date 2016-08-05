YES
===

First download the jetty runner. This is an entire web server is a jar.

    wget http://central.maven.org/maven2/org/eclipse/jetty/jetty-runner/9.4.0.M0/jetty-runner-9.4.0.M0.jar

I'm no monster, so I'm not putting the jar in the git repo. (We can automate pulling this down later)

Then use that in your classpath argument to scala.

    scala -cp jetty-runner-9.4.0.M0.jar helloworld.scala

In another terminal window, let's call it.

    curl localhost:8901

You should see <h1>Ci and Pet and Pet</h1>
