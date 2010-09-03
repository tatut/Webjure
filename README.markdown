# Webjure, a web programming framework for Clojure

## Overview

Webjure is a simple web framework for Clojure.
It provides basic routing, request and response functionality on top
of Java servlets and then gets out of your way. 

The defh macro can be used for more automagic behaviour
when defining handlers. It can be used to automatically read and parse
input parameters (both parts of the URL path and GET/POST params).
The defh can also automatically send the response as HTML or JSON.

Webjure also provides Clojure Page Templates (see webjure.cpt namespace) 
can be used for dynamic HTML templates similar to ZPT/JPT. The templates
are compiled to bytecode for good performance.

## Hello world

A hello world in Webjure is very simple:

    (ns my-hello-world
        (:refer-clojure)
        (:use webjure))
    
    (defh "/hello" [] {:output :html}
       `(:html
          (:head (:title "Hello world from Webjure"))
          (:body
           "Hello world!")))

## Wiki

For a slightly more complex example, see [in-memory wiki](http://gist.github.com/385152).


## Installation


To install, you will need Apache Maven 2. 

Run "mvn install" in the main directory.

## Running

After installation is done, you can depend on the webjure jar in your own web projects (or just copy the .jar from the target directory). To check out the demos, go to the "demos" subdirectory and run "mvn jetty:run" and point your browser to http://localhost:8080/webjure-demos/index.

SLIME users: To hack on the demos, first start swank by going to http://localhost:8080/webjure-demos/start-swank?port=4005 and then use slime-connect from Emacs.




