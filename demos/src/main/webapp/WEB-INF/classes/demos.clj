
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
	 session-get session-set
	 send-output slurp-post-data))


(defn dbg [& u]
  (. (. System err)
     (println (apply pr-str u))))


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
      (:li (:a {:href ~(url "/session")} "Session test"))
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
  (let [db (session-get "db"
                        (fn [] (if (nil? loc)
                                 nil
                                 (connect-to-db loc))))]
    (if (nil? db)
      (dbtest-ask-location)
      `(:html
        (:body
         ~(let [results (sql/query db "SELECT c.COUNTRY, c.COUNTRY_ISO_CODE, c.REGION, (SELECT COUNT(*) FROM cities WHERE country_iso_code=c.country_iso_code) as cities FROM countries c ORDER BY country ASC")
                columns (:columns (meta results))]            
            (format-table (map first columns) results
                          ["List cities" (fn [row]
                                           (url "/dbtest-cities" {:country (second row)}))])))))))

(defh "/dbtest-cities" [country "country"] {:output :html}
  (let [cities (sql/query (session-get "db")
                          "SELECT * FROM cities WHERE country_iso_code=?" country)]    
    `(:html
      (:body
       ~(format-table (map first (:columns (meta cities))) cities)
       (:b ~(str (:rows (meta cities)) " cities.")))
      (:br)
      (:a {:href ~(url "/dbtest")} "&laquo; back to countries"))))



;;;;;;;;;;;;;
;; AJAX REPL
;;
;; a very simplistic version
;; there were some problems with using PiperReader/-Writer approach
;; with thread deadlocking... 


(defn ajaxrepl-js []
  ;; FIXME: Move me to a resource file
  (reduce 
   str 
   (interleave
    ["var req;"
     "function replCallback(txt) {"
     "  if(req.readyState == 4) {"
     "    if(req.status == 200) {"
     "      if(req.responseText.length > 0) appendContent('=> '+req.responseText);"
     "      readRepl();"
     "    } else {"
     "      alert('Unable to read repl: '+req.statusText);"
     "    }"
     "  }"
     "} "
      
     "function readRepl() { "
     "  req = new XMLHttpRequest();"
     "  req.onreadystatechange = replCallback;"
     (str "  req.open('GET', '" (url "/ajaxrepl-out") "', true);")
     "  req.send(null);"
     "} "
      
     "function appendContent(txt) {"
     "  var elt = document.getElementById('replout');"
     "  elt.innerHTML += txt + '\\n';"
     "  elt.scrollTop = elt.scrollHeight;"
     "} "
          
     "function keyHandler(event) { if(event.keyCode == 13) write(); } "

     "function write() { "
     "   var elt = document.getElementById('replin');"
     "   var r = new XMLHttpRequest();"
     (str "   r.open('POST', '" (url "/ajaxrepl-in") "', true);")
     "   appendContent(elt.value);"
     "   r.send(elt.value);"
     "   elt.value = '';"
     "}"

     "window.onload = readRepl;"]
    (repeat "\n"))))

;; The main page
(defn ajaxrepl []
  (html-format
   (response-writer)
   
   `(:html
     (:head (:title "Webjure AJAX REPL")
	    (:script {:type "text/javascript"
		      :language "javascript"}
		      ~(ajaxrepl-js)))

     (:body 
      (:h3 "Webjure AJAX REPL")
      (:div 
       {:id "replout"
        :style "width: 600px; height: 400px; overflow: auto; font-family: monospace; color: silver; background-color: black; white-space: pre;"}
	"")

      (:form ;{:onsubmit "return write();"}
	     (:textarea {:onkeypress "keyHandler(event)" :id "replin" :style "width: 600px; height: 70px;"} ""))))))

(publish ajaxrepl "/ajaxrepl")

;;; A REPL using Refs 

(defn repl-session []
  (session-get "repl-messages" (fn [] (ref []))))
     


;; Return new content since last update
;; if there is no new content, wait for some
(defn ajaxrepl-out []
  (let [repl-messages (repl-session)]
    (dbg repl-messages)
    (dosync        
     (let [output (reduce str
                          (interleave @repl-messages (repeat "\n")))]
       (dbg "sending: " output)
       (webjure/send-output "text/plain" output))
     (ref-set repl-messages []))))
(webjure/publish ajaxrepl-out "/ajaxrepl-out")

(defn ajaxrepl-eval [str]
  (eval (read (new java.io.PushbackReader (new java.io.StringReader str)))))

(defn ajaxrepl-in []
  (dosync
   (let [repl-messages (repl-session)
         input (webjure/slurp-post-data)]
     (alter repl-messages conj (ajaxrepl-eval input))
     (webjure/send-output "text/plain" "OK"))))
(webjure/publish ajaxrepl-in "/ajaxrepl-in")

		      
;;; Session test

(defh "/session" [] {:output :html}
  `(:html
    (:head (:title "Webjure session test"))
    (:body
     ~(let [count (session-get "count")
            greeting (if (nil? count)
                       "Hello first time user"
                       (str "Hello, this has been called " count " times."))]
        (session-set "count" (if (nil? count) 1 (inc count)))
        greeting))))


