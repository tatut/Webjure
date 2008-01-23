 
(in-namespace 'sql)

(defn register-driver [drv]
  (. Class (forName drv)))

(defn connect 
  ([url] (. java.sql.DriverManager (getConnection url)))
  ([url user pass] (. java.sql.DriverManager (getConnection url user pass))))

;; Execute query and return results as a list of lists.
;; Result set metadata is attached to the returned list.
(defn query [#^java.sql.Connection con query & query-args]
  (let [stmt (. con (prepareStatement query))]

    ;; Set query arguments
    (loop [i 1
	   args query-args]
      (if (not (eql? nil args))
	(do
	  (. stmt (setObject i (first args)))
	  (recur (+ 1 i) (rest args)))))
    
    ;; Execute query
    (let [result-set   (. stmt (executeQuery))
	  metadata     (. result-set (getMetaData))
	  column-count (. metadata (getColumnCount))
          columns      (loop [cols (list)
			      i 1]
			 (if (eql? i (+ 1 column-count))
			   (reverse cols)
			   (recur (cons (list (. metadata (getColumnName i))
					      (. metadata (getColumnClassName i)))
					cols)
				  (+ 1 i))))]
      (loop [acc (list)
	     row-count 0]
	(if (not (. result-set (next)))
	  (let [result-list (or (reverse acc) (list))]
	    (with-meta result-list {:columns columns :rows row-count}))
	  (recur (cons (touch (map (fn [i] (. result-set (getObject i)))
				   (range 1 (+ 1 column-count))))
		       acc)
		 (+ 1 row-count)))))))

