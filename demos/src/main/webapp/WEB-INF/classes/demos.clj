
;; Demos to be placed here

(in-namespace 'webjure-demos)

(refer '(webjure
	 *request* *response*
	 request-headers request-path
	 require
	 html-format
	 response-writer
	 publish
	 send-output
	 slurp-post-data
	 format-date))


(defn index []
  (. *response* (setContentType "text/html")) ;; for links browser
  (html-format
   (response-writer)
   
   (let [url (fn [x] (strcat "http://localhost:8080/webjure/" x))]
     `(:html 
       (:body 
	(:h3 "Webjure, a web framework like thing.")
	(:p "Welcome to webjure, a clojure web framework. Not much is done yet, but feel free "
	    "to look at the demos.")
	(:ul
	 (:li (:a {:href ~(url "index")} "This page, a simple sexp markup page"))
	 (:li (:a {:href ~(url "info")} "Dump request info"))
	 (:li (:a {:href ~(url "dbtest")} "Database test"))
	 (:li (:a {:href ~(url "ajaxrepl")} "an AJAX REPL")))
	

	(:div {:style "position: relative; left: 50%;"}
	      (:div {:style "text-align: center; width: 300px; position: absolute; left: -150; border: dotted black 2px; background-color: yellow; padding: 10px;"}
		    (:b "Important notice: ") "Have a nice day!"
		    (:br)
		    (:div {:style "font-size: small;"} ~(format-date "dd.MM.yyyy hh:mm")))))))))


(publish index "/index")

(defn format-map-as-table [keylabel valuelabel themap]
  `(:table
    (:tr (:th ~keylabel) (:th ~valuelabel))
    ~@(map (fn [key]
	       `(:tr (:td ~key) (:td ~(reduce (fn [x y] (strcat x ", " y)) (get themap key)))))
	   (keys themap))))

(defn format-table [headers values & actions]
  `(:table
    (:tr 
     ~@(map (fn [hdr] `(:th ~hdr)) headers)
     (:th "Actions"))
    
    ~@(map (fn [row] `(:tr 
		       ~@(map (fn [v] `(:td ~(str v))) row)
		       (:td ~@(map (fn [action]
				       `(:a {:href ~((second action) row)}
					    ~(first action))) actions))
		       ))
	   values)))

(defn info []
  (. *response* (setContentType "text/html"))
  (html-format
   (response-writer)
   
   `(:html 
     (:body
      (:h3 "Request headers")
      ~(format-map-as-table "Name" "Values" (request-headers))
      (:br)
      
      (:h3 "Path info")
      (:p ~(request-path))))))

(publish info "/info*")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; db test using derby tours
;;

(require "sql")

(def *db* nil)
(defn connect-to-db [location]
  (if *db*
    *db*
    (do (sql/register-driver "org.apache.derby.jdbc.EmbeddedDriver")
	(def *db* (sql/connect (strcat "jdbc:derby:" location)))
	*db*)))

(defn dbtest-ask-location []
  (. *response* (setContentType "text/html"))
  (html-format
   (response-writer)
   `(:html
     (:body
      (:form {:action "http://localhost:8080/webjure/dbtest" :method "POST"}
	     (:b "Where is the derby tours db located?")
	     (:input {:type "text" :name "db" :size "80"})
	     "(example: /Users/tadex/software/derby/demo/databases/toursdb)"
	     (:br)
	     (:input {:type "submit" :value "Go!"}))))))

(defn dbtest []
  (let [loc (. *request* (getParameter "db"))]
    (if loc (connect-to-db loc))
    (if (not *db*)
      (dbtest-ask-location)
      (do
	(. *response* (setContentType "text/html"))
	(html-format
	 (response-writer)
	 
	 `(:html
	   (:body
	    ~(let [results (sql/query *db* "SELECT c.*, (SELECT COUNT(*) FROM cities WHERE country_iso_code=c.country_iso_code) as cities FROM countries c ORDER BY country ASC")
			   columns (:columns (meta results))]
	       (format-table (map first columns) results
			     ["List cities" (fn [row]
						(strcat "http://localhost:8080/webjure/dbtest-cities?country="
							(second row)))])))))))))

(defn dbtest-cities []
  (. *response* (setContentType "text/html"))
  (let [country (. *request* (getParameter "country"))
	cities (sql/query *db* "SELECT * FROM cities WHERE country_iso_code=?" country)]
    (html-format
     (response-writer)

     `(:html
       (:body
	~(format-table (map first (:columns (meta cities))) cities)
	(:b ~(strcat (str (:rows (meta cities))) " cities.")))
       (:br)
       (:a {:href "http://localhost:8080/webjure/dbtest"} "&laquo; back to countries")))))
       
(publish dbtest "/dbtest")
(publish dbtest-cities "/dbtest-cities")

;;;;;;;;;;;;;
;; AJAX REPL
;;
;; a very simplistic version
;; there were some problems with using PiperReader/-Writer approach
;; with thread deadlocking... 


(def ajaxrepl-js 
     ;; FIXME: Move me to a resource file
     (strcat 
      "var req;"
      "function replCallback(txt) {"
      "  if(req.readyState == 4) {"
      "    if(req.status == 200) {"
      "      if(req.responseText.length > 0) append('=> '+req.responseText);"
      "      read();"
      "    } else {"
      "      alert('Unable to read repl: '+req.statusText);"
      "    }"
      "  }"
      "} "
      
      "function read() { "
      "  req = new XMLHttpRequest();"
      "  req.onreadystatechange = replCallback;"
      "  req.open('GET', 'http://localhost:8080/webjure/ajaxrepl-out', true);"
      "  req.send();"
      "} "
      
      "function append(txt) {"
      "  var elt = document.getElementById('replout');"
      "  elt.innerHTML += txt + '\\n';"
      "  elt.scrollTop = elt.scrollHeight;"
      "} "
          
      "function keyHandler(event) { if(event.keyCode == 13) write(); } "

      "function write() { "
      "   var elt = document.getElementById('replin');"
      "   var r = new XMLHttpRequest();"
      "   r.open('POST', 'http://localhost:8080/webjure/ajaxrepl-in', true);"
      "   append(elt.value);"
      "   r.send(elt.value);"
      "   elt.value = '';"
      "}"

      "window.onload = read;"))

;; The main page
(defn ajaxrepl []
  (html-format
   (response-writer)
   
   `(:html
     (:head (:title "Webjure AJAX REPL")
	    (:script {:type "text/javascript"
		      :language "javascript"}
		      ~ajaxrepl-js))

     (:body 
      (:h3 "Webjure AJAX REPL")
      (:div 
       {:id "replout"
        :style "width: 600px; height: 400px; overflow: auto; font-family: monospace; color: silver; background-color: black; white-space: pre;"}
	"")

      (:form ;{:onsubmit "return write();"}
	     (:textarea {:onkeypress "keyHandler(event)" :id "replin" :style "width: 600px; height: 70px;"} ""))))))

(publish ajaxrepl "/ajaxrepl")


(defn ensure-ajax-queue []
  (let [session (. *request* (getSession))
	queue (. session (getAttribute "ajaxrepl"))]
    (if queue
      queue
      
      ;; Create and store in session
      (let [queue (new java.util.concurrent.ArrayBlockingQueue 5)]
	(. session (setAttribute "ajaxrepl" queue))
	(ensure-ajax-queue)))))


(defn ajaxrepl-out []
  (let [queue (ensure-ajax-queue) 
	value (. queue (poll 1000 (. java.util.concurrent.TimeUnit MILLISECONDS)))]
    (send-output "text/plain"  
		 (if (nil? value) ""
		     (binding [clojure/*out* (new java.io.StringWriter)]
		       (pr (. webjure.servlet.WebjureServlet (eval value)))
		       (str *out*))))))
(publish ajaxrepl-out "/ajaxrepl-out")

(defn ajaxrepl-in []
  (let [queue (ensure-ajax-queue)]
    (. queue (put (slurp-post-data)))
    (scan queue)
    (send-output "text/plain" (str queue))))
(publish ajaxrepl-in "/ajaxrepl-in")

  