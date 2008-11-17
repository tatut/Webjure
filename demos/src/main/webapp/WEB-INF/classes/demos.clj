
;; Demos to be placed here

(ns webjure-demos
    (:refer-clojure)
    (:use webjure)
    (:use webjure.html)
    (:use webjure.sql))


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


(defn connect-to-db [location]
  (do (register-driver "org.apache.derby.jdbc.EmbeddedDriver")
      (connect (str "jdbc:derby:" location))))

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
         ~(let [results (query db "SELECT c.COUNTRY, c.COUNTRY_ISO_CODE, c.REGION, (SELECT COUNT(*) FROM cities WHERE country_iso_code=c.country_iso_code) as cities FROM countries c ORDER BY country ASC")
                columns (:columns (meta results))]            
            (format-table (map first columns) results
                          ["List cities" (fn [row]
                                           (url "/dbtest-cities" {:country (second row)}))])))))))

(defh "/dbtest-cities" [country "country"] {:output :html}
  (let [cities (query (session-get "db")
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



;; The main page
(defh "/ajaxrepl" [] {:output :html}
  `(:html
    (:head (:title "Webjure AJAX REPL")
	   (:script {:type "text/javascript"
		    :language "javascript"
		    :src ~(url "/resource/repl.js")}))

    (:body 
     (:h3 "Webjure AJAX REPL")
     (:div 
      {:id "replout"
      :style "width: 600px; height: 400px; overflow: auto; font-family: monospace; color: silver; background-color: black; white-space: pre;"}
      "")
     
     (:form ;{:onsubmit "return write();"}
      (:textarea {:onkeypress "keyHandler(event)" :id "replin" :style "width: 600px; height: 70px;"} "")))))

;;; A REPL using Refs 

(defn repl-session []
  (session-get "repl-messages" (fn [] (agent []))))
     
(defn repl-write [messages new-msg]
  (conj new-msg messages))

(defn repl-fetch [messages writer]
  (. writer (append (reduce str (interleave messages (repeat "\n")))))
  ;; new state is no messages
  [])
  

;; Set the Return new content since last update
;; if there is no new content, wait for some
(defn ajaxrepl-out []
  (. *response* (setContentType "text/plain"))
  (send (repl-session) (. *response* (getWriter))))
(publish ajaxrepl-out "/ajaxrepl-out")

(defn ajaxrepl-eval [str]
  (eval (read (new java.io.PushbackReader (new java.io.StringReader str)))))

(defn ajaxrepl-in []
  (send (repl-session) (ajaxrepl-eval (slurp-post-data))))
(publish ajaxrepl-in "/ajaxrepl-in")

		      
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


