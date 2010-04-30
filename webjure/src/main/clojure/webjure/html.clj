
(ns webjure.html
    (:refer-clojure))

(defn #^{:private true}
  append [#^java.lang.Appendable out & #^String stuff]
  (doseq [thing stuff]
      (. out (append (str thing)))))


(defn #^{:private true} html-format-default [out obj]
  (append out (str obj)))

(def #^{:private true} +type-dispatch-table+ {})
(defn html-format 
  ([#^Object obj]
   (let [app (new java.lang.StringBuilder)]
     (html-format app obj)
     (. app (toString))))
  ([out #^Object obj]
   (let [type (or (and (seq? obj) :seq)
		  (and (string? obj) :string)
		  (and (fn? obj) :fn)
		  (. obj (getClass)))
	      formatter (get +type-dispatch-table+ type)]
     (if (= nil formatter)
       ;;(html-format-default out obj)
       (throw (new java.lang.IllegalArgumentException 
		   (str "No formatter for type: " (str type))))
       (apply formatter (list out obj))))))


(defn #^{:private true} html-format-tag [out tag]
  (let [tagname (name (first tag))
	attrs   (second tag)
        content (if (map? attrs) (rest (rest tag)) (rest tag))]
    (append out "<" tagname)
    (if (map? attrs)
      (doseq [kw (keys attrs)]
	;; append the attribute value, replace disallowed " character with '
	(append out " " (name kw) "=\"" (. (str (get attrs kw)) (replace "\"" "'"))
		"\"")))
    (if (empty? content)
      (append out " />")
      (do	
	(append out ">")
	(doseq [c content]
	  (html-format out c))
	(append out "</" tagname ">")))))
      
(defn #^{:private true} html-format-string [out str]
  (append out (. (. str (replace "<" "&lt;")) (replace ">" "&gt;"))))

(def #^{:private true} +type-dispatch-table+
  {:seq    html-format-tag
   :string html-format-string
   :fn #(append %1 (%2))
   })
   