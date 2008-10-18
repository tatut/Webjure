
(ns html
    (:refer-clojure))

(defn append [#^java.lang.Appendable out & #^String stuff]
  (doseq thing stuff (. out (append (str thing)))))

(defn html-format-default [out obj]
  (append out (str obj)))

(def +type-dispatch-table+ {})
(defn html-format 
  ([#^Object obj]
   (let [app (new java.lang.StringBuilder)]
     (html-format app obj)
     (. app (toString))))
  ([out #^Object obj]
   (let [type (or (and (seq? obj) :seq)
		  (and (string? obj) :string)
		  (. obj (getClass)))
	      formatter (get +type-dispatch-table+ type)]
     (if (= nil formatter)
       ;;(html-format-default out obj)
       (throw (new java.lang.IllegalArgumentException 
		   (str "No formatter for type: " (str type))))
       (apply formatter (list out obj))))))


(defn html-format-tag [out tag]
  (let [tagname (name (first tag))
	attrs   (second tag)
        content (if (map? attrs) (rest (rest tag)) (rest tag))]
    (append out "<" tagname)
    (if (map? attrs)
      (doseq kw (keys attrs)
	;; append the attribute value, replace disallowed " character with '
	(append out " " (name kw) "=\"" (. (str (get attrs kw)) (replace "\"" "'"))
		"\"")))
    (if (= nil content)
      (append out " />")
      (do	
	(append out ">")
	(doseq c content
	  (html-format out c))
	(append out "</" tagname ">")))))
      
(defn html-format-string [out str]
  (append out (. (. str (replace "<" "&lt;")) (replace ">" "&gt;"))))

(def +type-dispatch-table+
  {:seq    html-format-tag
   :string html-format-string
   })
   