;; A Quick and Dirty CRUD for SQL tables
;;
;; Main entry point is the ui macro which is meant to be called from inside a
;; Webjure handler.


(ns webjure.sql.crud
  (:refer-clojure)
  (:use webjure.sql)
  (:use webjure)
  (:use webjure.cpt))

(defprotocol ListView
  (render-list-view [this]))

(defn page-link [here page]
  (let [{:keys [limit order dir]} here]
    (str "?start=" (* page limit) "&limit=" limit
	 (when order (str "&order=" order))
	 (when dir (str "&dir=" dir)))))

(defn listing-header-sort-attributes [here header]
  (let [field (get (:fields here) header)]
    (if  (not (:sortable field))
      []
      {"class" (if (= (:order here) (name header))
		 (if (= "asc" (:dir here)) "sortAscending" "sortDescending")
		 "normal")
       "onclick" (str "document.location = '"
		      (page-link (merge here
					{:order (name header)
					 :dir (if (= "asc" (:dir here)) "desc" "asc")})
				 (/ (:start here) (:limit here)))
		      "';")})))

(define-template generic-listing-template "src/main/resources/crud/generic-listing-template.cpt")

(extend-protocol ListView
  Object
  (render-list-view [obj] (str obj))

  Boolean
  (render-list-view [b]
		    (str "<span class=\"center\">"
			 (if b
			   "&#x2612;"   ;; ballot box with x
			   "&#x2610;")  ;; ballot box
			 "</span>"))
  nil
  (render-list-view [n] "<span class=\"sqlNull\">NULL</span>")
  )

(def +default-batch-size+ 25)

(defn determine-query-tables [home-table field-list fields]
  "Determine all tables that are involved in the query. Returns a mapping
from table name to it's query alias, eg. {\"firsttable\" \"t1\", \"secondtable\" \"t2\"}."
  (loop [[f & fs] field-list
	 tables {home-table "t1"}
	 counter 2]
    (if (nil? f)
      tables
      (let [{join :join} (fields f)]
	(if (not join)
	  (recur fs tables counter)
	  (recur fs (assoc tables
		      (:table join) (str "t" counter))
		 (+ counter 1)))))))

(defn determine-from-tables [home-table field-list fields]
  "Create a SQL FROM fragment with joins, eg. \"firsttable t1 LEFT JOIN secondtable t2 ON t1.sec_id=t2.id\". " 
  (let [tables (determine-query-tables home-table field-list fields)]
    (loop [[f & fs] field-list
	   from (str home-table " t1")]
      (if (nil? f)
	from
	(let [{join :join} (fields f)]
	  (if (not join)
	    (recur fs from)
	    (recur fs (let [{:keys [table home-field foreign-field]} join]
			(str from " LEFT JOIN " table " " (tables table) " ON "
			     (tables home-table) "." (name home-field) "=" (tables table) "." (name foreign-field))))))))))

(defn determine-foreign-keys [tables fields]
  "Determine additional foreign keys we need to fetch. Returns a list of field references."
  (apply vector
	 (filter #(not (nil? %))
		 (map (fn [[field-name {join :join}]]
			(when join
			  [(:table join) (:foreign-field join)
			   (str (tables (:table join)) "."  (name (:foreign-field join)))]))
		      fields))))



(defn string-join
  ([coll] (string-join ", " coll))
  ([sep coll]
     (reduce str
	     (butlast (interleave coll (repeat sep))))))

(defn generate-listing [db table opt]
  `(with-open [db# (~db)]
     (let [start# (Long/parseLong (or (request-parameter "start") "0"))
	   limit# (let [lim# (request-parameter "limit")]
		    (if lim#
		      (Long/parseLong lim#)
		      (or ~(:batch-size opt) +default-batch-size+)))
	   order# (request-parameter "order")
	   dir# (request-parameter "dir")
	   list-fields# ~(:list-fields opt)
	   primary-key# ~(:primary-key opt)
	   fields# ~(:fields opt)
	   tables# ~(determine-query-tables table (:list-fields opt) (:fields opt))
	   from-tables# ~(determine-from-tables table (:list-fields opt) (:fields opt))
	   foreign-key-fields# ~(determine-foreign-keys
				 (determine-query-tables table (:list-fields opt) (:fields opt))
				 (:fields opt))	   
	   field-ref# (fn [f#]
			(let [{join# :join} (fields# (keyword f#))]
			  (if (not join#)
			    (str "t1." f#)
			    (str (tables# (:table join#)) "." (name (:field join#))))))
	   ]
       (set-response-content-type *response* "text/html; charset=UTF-8")
       (println "REQUEST HEADERS: " (request-headers))
       (with-open
	   [out#  (if (.contains (or (first (get (request-headers) "Accept-Encoding")) "")
				 "gzip")
		    (do (.setHeader *response* "Content-Encoding" "gzip")
			(java.io.OutputStreamWriter. (java.util.zip.GZIPOutputStream. (.getOutputStream *response*)) "UTF-8"))
		    (response-writer))]
	 (~(or (:list-template opt) 'webjure.sql.crud/generic-listing-template)
	  out#
	  {:list-fields list-fields#
	   :fields fields#
	   :start start#
	   :limit limit#
	   :order order#
	   :dir dir#
	   :rows (let [sql# (str 
			     "SELECT t1." (name primary-key#) ", "
			     (string-join
			      (concat (map #(nth % 2) foreign-key-fields#)
				      (map (fn [field-name#]
					     (let [{join# :join} (fields# field-name#)]
					       (if join#
						 (str (tables# (:table join#)) "." (name (:field join#))
						      " as " (name field-name#))
						 (str "t1." (name field-name#)))))
					   list-fields#)))
			     " FROM " from-tables#
			     (when order#
			       (str " ORDER BY " (field-ref# order#) " " (if (= "asc" dir#) "ASC" "DESC")))
			     (when (or (not (zero? start#)) (not (zero? limit#)))
			       (str " LIMIT " start# ", " limit#)))
		       drop# (+ 1 (count foreign-key-fields#))
		       res# (query db# sql#)]
		   ;; (println "QUERY: " sql#)
		   (map (fn [row#]
			  [(drop drop# row#)
			   (first row#)
			   (zipmap (map first foreign-key-fields#)
				   (take (count foreign-key-fields#)
					 (drop 1 row#)))])
			res#))
	   :total-rows (ffirst (query db# (str "SELECT COUNT( " (name primary-key#) ") FROM " ~table)))})))))
	       

(defn generate-select [db field table display-field value-field]
  `(str
    "<select name=\"" ~(name field) "\">\n"
    (reduce str
	    (map (fn [[value# display#]]
		   (str "  <option value=\"" value# "\">" display# "</option>\n"))
		 (query ~db ~(str "SELECT " (name value-field) ", " (name display-field)
				  " FROM " (name table)))))
    "</select>\n"))

(defn generate-field-editor [db field-name field-info]
  (let [{label :label join :join} field-info]
    (if join
      (generate-select db (:table join) (:field join) (:foreign-field join))
      (str "<input type=\"text\" name=\"" (name field-name) "\"/>"))))


(defmacro ui "Generate a CRUD UI for the given table"
  [db table & options]
  (let [opt-pairs (partition 2 options)
	opt (zipmap (map first opt-pairs)
		    (map second opt-pairs))]
    `(let [delete# (request-parameter "_delete")
	   edit# (request-parameter "_edit")
	   save# (request-parameter "_save")]
       (if delete#
	 (send-output "text/plain" (str "Deleting " delete#))
	 (if edit#
	   (send-output "text/plain" (str "Showing edit form for " edit#))
	   (if save#
	     (send-output "text/plain" (str "Saving " save#))
	     ~(generate-listing db table opt)))))))

(defmacro define-crud-handler [prefix db table & options]
  (let [opt-pairs (partition 2 options)
	opt (zipmap (map first opt-pairs)
		    (map second opt-pairs))]
    `(defh ~(re-pattern (str prefix "(/([^/]+))?$"))
       [pk# 2] {}
       (if pk#
	 (do 
	   (send-output "text/plain" (str (if (request-parameter "edit")
					    "Edit " "View ") pk#)))	   
	 ~(generate-listing db table opt)))))