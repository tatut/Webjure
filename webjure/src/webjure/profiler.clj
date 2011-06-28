;; Server page generation profiling support.
;;
;; Some client side scripts, styles and templates copied from:
;; GAE Mini Profiler (https://github.com/kamens/gae_mini_profiler)
;; which is itself heavily inspired by MVC mini profiler (http://code.google.com/p/mvc-mini-profiler/)
;;

(ns webjure.profiler
  (:refer-clojure)
  (:use webjure)
  (:use webjure.cpt))

(def *profiler* nil)

(defprotocol Profiler
  (start-step [this name])
  (end-step [this name])
  (report [this])
  (id [this]))

;; Empty profiler implementation to use when *profiler* is nil
(extend-protocol Profiler
  nil
  (start-step [this name] nil)
  (end-step [this name] nil)
  (report [this] nil)
  (id [this] nil)
  )

(deftype DefaultProfiler [^{:volatile-mutable true } steps
			  ^{:volatile-mutable true} current-step
			  ^{:volatile-mutable true} profiler-id
			  ]

  Profiler
  (start-step [this name]
	      (set! current-step {:name name
				  :start (System/currentTimeMillis)
				  :parent current-step}))

  (end-step [this name]
	    (if (not (= name (:name current-step)))
	      (throw (IllegalArgumentException. "Ending an unknown step."))
	      (let [finished-step (assoc (dissoc current-step :parent)
				    :end (System/currentTimeMillis))
		    parent (:parent current-step)]
		(if parent
		  (set! current-step
			(assoc parent :children
			       (conj (or (:children parent) []) finished-step)))
		  (do (set! steps (conj steps finished-step))
		      (set! current-step nil))))))
  
  (report [this] steps)
  
  (id [this]
      (or profiler-id
	  (set! profiler-id (str "p-" (System/currentTimeMillis) "-" (rand-int Integer/MAX_VALUE))))))


(defn print-profiler-report
  ([report] (print-profiler-report report 0))
  ([report indent]
     (doseq [{:keys [name start end children]} report]
       (println (str (apply str (repeat indent " "))
		     name
		     " "
		     (- end start) " ms"))
       (print-profiler-report children (+ indent 2)))))

(defn inject-profiler-html []
  (let [id (id *profiler*)]
    (if (nil? id)
      "" ;; Not profiling
      (str "<link rel=\"stylesheet\" type=\"text/css\" href=\"/_webjure_profiler/profiler.css\" />\n"
	   "<script type=\"text/javascript\" src=\"/_webjure_profiler/profiler.js\"></script>\n"
	   "<script type=\"text/javascript\">WebjureMiniProfiler.init(\"" id "\", false)</script>"))))

(defn duration-of [{start :start end :end}]
  (- end start))

(defn format-profiler-report
  ([steps] (format-profiler-report steps 0))
  ([steps indent]
     (if (nil? steps)
       nil
       (loop [acc []
	      [step & steps] steps]
	 (if (nil? step)
	   acc
	   (recur (concat acc [{:name (:name step)
				:indent indent
				:total-ms (duration-of step)
				:own-ms (- (duration-of step)
					   (reduce + 0 (map duration-of (:children step))))}]
			  (format-profiler-report (:children step) (+ 1 indent)))
		  steps))))))

(define-template profiler-report "src/main/resources/profiler/report.xml")

(defh "/_webjure_profiler/request" [id "id"] {}
  (let [duration #(- (:end %) (:start %))
	{url :url steps :report} (webjure/session-get id)
	total (reduce + 0 (map duration steps))]
            
    (webjure/set-response-content-type webjure/*response* "text/html")
    (profiler-report
     (response-writer)
     {:total total
      :url url
      :id id
      :report (format-profiler-report steps)
      })))

    
(def *profiler-enabled* (ref (fn [] true)))

(defmacro with-request-profiling [& body]
  `(binding [*profiler* (if (@*profiler-enabled*)
			  (DefaultProfiler. [] nil nil)
			  nil)]
     (let [url# (webjure/request-path)]
       (with-step url#
	 (do ~@body))
       (when *profiler*
	 (webjure/session-set (id *profiler*)
			      {:url url#
			       :report (report *profiler*)})))))

(defmacro with-step [name & body]
  `(let [name# ~name]
     (start-step *profiler* name#)
     (try 
       ~@body
       (finally (end-step *profiler* name#)))))

(define-static-resource "src/main/resources/profiler/profiler.js" "text/javascript"
  "/_webjure_profiler/profiler.js")

(define-static-resource "src/main/resources/profiler/profiler.css" "text/css"
  "/_webjure_profiler/profiler.css")

