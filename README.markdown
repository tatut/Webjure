
# Webjure, a web programming framework for Clojure

## Overview

Webjure is a simple web framework for Clojure.
It provides basic routing, request and response functionality on top
of Java servlets and then gets out of your way. 

The defh macro can be used for more automagic behaviour
when defining handlers. It can be used to automatically read and parse
input parameters (both parts of the URL path and GET/POST params).
The defh can also automatically send the response as HTML or JSON.


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

A slightly more complex example. An in-memory wiki.

<script src="http://gist.github.com/385152.js?file=wiki.clj"></script>


## Installation

To install, you will need Apache Maven 2. You will also need to manually install (or deploy, if you host your own Maven repository) the Clojure 1.1 jar file for Maven to find it.

Run "mvn install" in the main directory.

## Running

After installation is done, you can depend on the webjure jar in your own web projects (or just copy the .jar from the target directory). To check out the demos, go to the "demos" subdirectory and run "mvn jetty:run" and point your browser to http://localhost:8080/webjure-demos/index.

Emacs users: To hack on the demos live set your inferior-lisp-program to "telnet localhost 27272" and M-x inferior-lisp.



