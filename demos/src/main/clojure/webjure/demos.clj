
;; Demos to be placed here

(ns webjure.demos
    (:refer-clojure)
    (:use webjure)
    (:use webjure.html)
    (:use webjure.sql)
    (:use webjure.xml.feeds)
    (:use webjure.wiki)
    (:use webjure.cpt)
    (:use swank.swank)
    (:use clojure.stacktrace))


(defn dbg [& u]
  (. (. System err)
     (println (apply pr-str u))))

(defn menu [& entries]
  (map (fn [[link desc]]
	 `(:li (:a {:href ~link} ~desc)))
       entries))

(declare page)

(def *swank-started* (ref false))

(defh "/start-swank" [port {:name "port" :validator #(if (nil? %) 4005 (Integer/parseInt %))}] 
  {:output :text}
  (if @*swank-started*
    "Swank already started."
    (dosync
     (try (start-repl port)
	  (ref-set *swank-started* true)
	  (str "Swank started on port " port)
	  (catch Exception e
	    (str "Unable to start swank: " (with-out-str (print-cause-trace e))))))))

(defh "/index" [] {:output :print}		   
  (page 
   {:title "Webjure, a tiny web framework for Clojure"
    :content (html-format 
	      `(span
		(:h2 "Welcome to Webjure demos!")
		(:p "Here are the demos.")
		(:ul ~@(menu 
			[(url "/index") "This page, a simple sexp markup page"]
			[(url "/info" {:some "value" :another "one"}) "Dump request info"]
			[(url "/dbtest") "Database test"]
			[(url "/session") "Session test"]
			[(url "/clojurenews") "Clojure news (Atom feed parser test)"]
			[(url "/hello/Test") "Test path binding"]
			[(url "/json?foo=bar&quux=baz") "Return request info as JSON"]))
		(:hr)
		(:p 
		 (:b "Important notice: ") "Have a nice and RESTful day!"
		 (:br)
		 (:div {:style "font-size: small;"} ~(format-date "dd.MM.yyyy hh:mm")))))
    }))
         

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



;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Atom feed parser test

(defh "/clojurenews" []
  {:output :html}
  (with-open [url (.openStream (new java.net.URL "http://clojure.blogspot.com/feeds/posts/default"))]
      (let [feed (load-atom1-feed url)]
	(do (println (str feed))
	    `(:html 
	      (:head (:title ~(feed :title)))
	      (:body 
	       (:h3 ~(feed :subtitle))
	       (:ul
		~@(map (fn [entry]
			 `(:li (:a {:href ~(((entry :links) "alternate") :url)} ~(entry :title))))
		       (feed :entries)))
	       (:hr)
	       "And this is what we parsed from the XML:"
	       (:p)
	       (:textarea {:style "width: 95%; height: 300px;" } 
			  ~(webjure.json/serialize-str feed))))))))


;;;;;;;;;;;;;;;;;;;;
;; defh regex test

(defh #"/hello/(.+?)(/(.+))?$" 
  [first-name 1
   last-name 3]
  {:output :html}

  `(:html
    (:body
     (:p "Hello " ~first-name " " ~(or last-name ""))

     ~@(if (nil? last-name)
	 `((:a {:href ~(url (str "/hello/" first-name "/Something"))}
	       "try with another path component"))))))


;;;;;;;;;;;;;;
;; json test

(defh "/json" []
  {:output :json}
  {"headers" (request-headers)
   "parameters" (request-parameters)})

(define-template page "src/main/resources/page.cpt")

(comment

(defn page [data]
  `(:html 
    {:xmlns "http://www.w3.org/1999/xhtml"}
    (:head
     (:meta {:http-equiv "content-type" :content "text/html; charset=utf-8"})
     (:title ~(or (:title data) "Webjure demos page"))	    
     (:link {:href ~(url "/resource/default.css") :rel "stylesheet" :type "text/css"}))
    (:body
     (:div {:id "header"}
	   (:h1 "Webjure")
	   (:h2 "By Free CSS Templates"))

     (:div {:id "menu"}
	   (:ul 
	    (:li {:class "first"} (:a {:href ~(url "/index")} "Home"))
	    (:li (:a {:href "http://github.com/tatut/Webjure"} "On Github"))))

     (:div {:id "content"}
	   (:div {:id "columnA"}
		 ~@(:content data))
	   
	   (:div {:id "columnB"}
		 (:h2 "Recent Updates")
		 (:p (:strong "January 11 2010") 
		     (:br)
		     "Updated to 0.5-SNAPSHOT, now works with Clojure 1.1 and does AOT compiling."))
		 
	   (:div {:style "clear: both;"} "&nbsp;"))
     (:div {:id "footer"}
	   (:p "Copyright &copy; 2009-2010 Tatu Tarvainen. Designed by " 
	       (:a {:href "http://www.freecsstemplates.org" :class "link1"} "Free CSS Templates"))))))

)