
;; Demos to be placed here

(in-ns 'webjure-demos)
(clojure/refer 'clojure)

(refer 'webjure :only '(
         url
	 defh
	 format-date
	 html-format
	 publish
	 *request* *response* request-headers request-path require response-writer	 
         request-parameters
	 session-get
	 send-output slurp-post-data))

;;(defn url [& u]
;;  (reduce str (webjure/base-url) u))


(defh "/index" [] {:output :html}
  `(:html 
    (:body 
     (:h3 "Webjure, a web framework like thing.")
     (:p "Welcome to webjure, a clojure web framework. Not much is done yet, but feel free "
         "to look at the demos.")
     (:ul
      (:li (:a {:href ~(url "/index")} "This page, a simple sexp markup page"))
      (:li (:a {:href ~(url "/info" {:some "value" :another "one"})} "Dump request info"))
      (:li (:a {:href ~(url "/dbtest")} "Database test"))
      (:li (:a {:href ~(url "/ajaxrepl")} "an AJAX REPL")))
     
     
     (:div {:style "position: relative; left: 50%;"}
           (:div {:style "text-align: center; width: 300px; position: absolute; left: -150; border: dotted black 2px; background-color: yellow; padding: 10px;"}
                 (:b "Important notice: ") "Have a nice and RESTful day!"
                 (:br)
                 (:div {:style "font-size: small;"} ~(format-date "dd.MM.yyyy hh:mm")))))))


(defn format-map-as-table [keylabel valuelabel themap]
  `(:table
    (:tr (:th ~keylabel) (:th ~valuelabel))
    ~@(map (fn [key]
	       `(:tr (:td ~key) (:td ~(reduce (fn [x y] (str x ", " y)) (get themap key)))))
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

(defh "/info*" [] {:output :html}
  `(:html 
    (:body
     (:h3 "Request headers")
     ~(format-map-as-table "Name" "Values" (request-headers))
     (:br)
     
     (:h3 "Request parameters")
     ~(format-map-as-table "Name" "Values" (request-parameters))
     (:br)
                                             
     (:h3 "Path info")
     (:p ~(request-path)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; db test using derby tours
;;

(require "sql")

(defn connect-to-db [location]
  (do (sql/register-driver "org.apache.derby.jdbc.EmbeddedDriver")
      (sql/connect (str "jdbc:derby:" location))))

(defn dbtest-ask-location []
  `(:html
    (:body
     (:form {:action ~(url "/dbtest") :method "POST"}
	    (:b "Where is the derby tours db located?")
	    (:input {:type "text" :name "db" :size "80"})
	    "(example: /Users/tadex/software/derby/demo/databases/toursdb)"
	    (:br)
	    (:input {:type "submit" :value "Go!"})))))

(defh "/dbtest" [loc {:name "db" :optional true}] {:output :html}
  (let [db (or (session-get "db")
	       (and loc (connect-to-db loc)))]
    (if db
      (dbtest-ask-location)
      (do
	(. *response* (setContentType "text/html"))
	(html-format
	 (response-writer)
	 
	 `(:html
	   (:body
	    ~(let [results (sql/query db "SELECT c.*, (SELECT COUNT(*) FROM cities WHERE country_iso_code=c.country_iso_code) as cities FROM countries c ORDER BY country ASC")
			   columns (:columns (meta results))]
	       (format-table (map first columns) results
			     ["List cities" (fn [row]
						(url "/dbtest-cities?country=" (second row)))])))))))))

;; (defn dbtest-cities []
;;   (. *response* (setContentType "text/html"))
;;   (let [country (. *request* (getParameter "country"))
;; 	cities (sql/query db "SELECT * FROM cities WHERE country_iso_code=?" country)]
;;     (html-format
;;      (response-writer)

;;      `(:html
;;        (:body
;; 	~(format-table (map first (:columns (meta cities))) cities)
;; 	(:b ~(strcat (str (:rows (meta cities))) " cities.")))
;;        (:br)
;;        (:a {:href ~(url "/dbtest")} "&laquo; back to countries")))))
       
;; (publish dbtest "/dbtest")
;; (publish dbtest-cities "/dbtest-cities")

;;;;;;;;;;;;;
;; AJAX REPL
;;
;; a very simplistic version
;; there were some problems with using PiperReader/-Writer approach
;; with thread deadlocking... 


(def ajaxrepl-js 
     ;; FIXME: Move me to a resource file
     (str 
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


;;;;; WORKING POLLING VERSION
;; (defn ensure-ajax-queue []
;;   (let [session (. *request* (getSession))
;; 	queue (. session (getAttribute "ajaxrepl"))]
;;     (if queue
;;       queue
;;       ;; Create and store in session
;;       (let [queue (new java.util.concurrent.ArrayBlockingQueue 5)]
;; 	(. session (setAttribute "ajaxrepl" queue))
;; 	(ensure-ajax-queue)))))

;; (defn ajaxrepl-out []
;;   (let [queue (ensure-ajax-queue) 
;; 	value (. queue (poll 1000 (. java.util.concurrent.TimeUnit MILLISECONDS)))]
;;     (send-output "text/plain"  
;; 		 (if (nil? value) ""
;; 		     (binding [clojure/*out* (new java.io.StringWriter)]
;; 		       (pr (. webjure.servlet.WebjureServlet (eval value)))
;; 		       (str *out*))))))
;; (publish ajaxrepl-out "/ajaxrepl-out")

;; (defn ajaxrepl-in []
;;   (let [queue (ensure-ajax-queue)]
;;     (. queue (put (slurp-post-data)))
;;     (scan queue)
;;     (send-output "text/plain" (str queue))))
;; (publish ajaxrepl-in "/ajaxrepl-in")

;;;;;;; PIPE VERSION
(defn ensure-ajax-io [req]
  (let [session (. req (getSession))
	initialized (. session (getAttribute "ajaxrepl"))]
    (if initialized
      [(. session (getAttribute "ajaxrepl-in")) 
       (. session (getAttribute "ajaxrepl-out"))]

      ;; Create and store in session
      (let [out (new java.io.PipedWriter)
	    in (new java.io.PipedReader)];(new java.io.PushbackReader (new java.io.PipedReader out))]
	(. in (connect out))
	(. session (setAttribute "ajaxrepl-in" in))
	(. session (setAttribute "ajaxrepl-out" out))
	(. session (setAttribute "ajaxrepl" true))
	(ensure-ajax-io req)))))

;; Return new content since last update
;; if there is no new content, wait for some

;(defn ajaxrepl-out [m req resp]
;  (let [in (first (ensure-ajax-io req))]
;    (webjure/send-output resp "text/plain"
;			 (str (eval (read in))))))
(defn ajaxrepl-out [m req resp]
  (let [in (first (ensure-ajax-io req))]
    (. Thread (sleep 5000))
    (webjure/send-output resp "text/plain"
			 (str in))));(str (. in (read))))))

(webjure/publish ajaxrepl-out "/ajaxrepl-out")

(defn ajaxrepl-in [m req resp]
  (let [out (second (ensure-ajax-io req))]
    (. out (append (webjure/slurp-post-data req)))
    (. out (flush))
    (webjure/send-output resp "text/plain" (str out))))
(webjure/publish ajaxrepl-in "/ajaxrepl-in")

		      
  