(defproject webjure "0.9.1-SNAPSHOT"
  :description "Webjure - a Clojure web framework"
  :dependencies [[org.clojure/clojure "1.2.0"]
		 [org.clojure/clojure-contrib "1.2.0"]]
  :dev-dependencies [[leiningen/lein-swank "1.2.0-SNAPSHOT"]
		     [javax.servlet/servlet-api "2.4"]
		     [org.mortbay.jetty/jetty "6.1.26"]]
  :aot [webjure
	webjure.servlet
	webjure.sql webjure.sql.crud webjure.sql.crud.editors
	webjure.cpt
	webjure.html
	webjure.xml webjure.xml.feeds
	webjure.json
	webjure.csv
	webjure.profiler
	;;webjure.websocket
	]
  :repl-init-script "src/init.clj"
  :omit-source true
  )