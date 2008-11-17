 
(ns webjure.sql
    (:refer-clojure))

(defn register-driver [drv]
  (. Class (forName drv)))

(defn connect 
  ([url] (. java.sql.DriverManager (getConnection url)))
  ([url user pass] (. java.sql.DriverManager (getConnection url user pass))))


(defn prepare-statement [#^java.sql.Connection con query query-args]
  (let [stmt (. con (prepareStatement query))]
    ;; Set query arguments
    (loop [i 1
	   args query-args]
      (if (not (= nil args))
	(do
	  (. stmt (setObject i (first args)))
	  (recur (+ 1 i) (rest args)))))
    stmt))
  
(defn #^{:private true}
  dbg [& items]
  (. (. System err) (println (reduce str (map str items)))))

;; Execute query and return results as a list of lists.
;; Result set metadata is attached to the returned list.
(defn query [#^java.sql.Connection con query & query-args]
  (let [stmt (prepare-statement con query query-args)
        result-set   (. stmt (executeQuery))
	metadata     (. result-set (getMetaData))
	column-count (. metadata (getColumnCount))
	columns      (doall (map (fn [i]
				     [(. metadata (getColumnName i))
				      (. metadata (getColumnClassName i))]) (range 1 (+ 1 column-count))))]
    (loop [acc []
	   row-count 0]	   
      (if (= false (. result-set (next)))
	(let [result-list (or (reverse acc) (list))]
	  (with-meta result-list {:columns columns :rows row-count}))
	(recur (conj acc (doall (map (fn [i] (. result-set (getObject i)))
						     (range 1 (+ 1 column-count)))))
	       (+ 1 row-count))))))

(defn update [#^java.sql.Connection con update & update-args]
  (let [stmt (prepare-statement con update update-args)]
    (. stmt (executeUpdate))
    (. stmt (close))))

(defn call-with-tx [#^java.sql.Connection con func]
  (let [autocommit (. con (getAutoCommit))]
    (when autocommit
      (. con (setAutoCommit false)))
    (try (func)
	 (. con (commit))
	 (catch java.sql.SQLException se
		(. con (rollback))
		false))
    (when autocommit
      (. con (setAutoCommit true)))))
    
