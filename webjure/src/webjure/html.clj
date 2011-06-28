(ns webjure.html
    (:refer-clojure))

(defn- append [^java.lang.Appendable out & stuff]
  (doseq [thing stuff]
    (.append out (str thing))))

(defprotocol HtmlFormat
  (format-item [thing out]))

(defn- html-format-tag [tag out]
  (let [tagname (name (first tag))
	attrs   (second tag)
	content (if (map? attrs) (rest (rest tag)) (rest tag))]
    (append out "<" tagname)
    (if (map? attrs)
      (doseq [[key value] attrs]
	;; append the attribute value, replace disallowed " character with '
	(append out
		" " (name key) "=\"" (.replace (str value) "\"" "'") "\"")))
    (if (empty? content)
      (append out " />")
      (do	
	(append out ">")
	(doseq [c content]
	  (format-item c out))
	(append out "</" tagname ">")))))

(extend-protocol HtmlFormat
  clojure.lang.Cons
  (format-item [tag out] (html-format-tag tag out))

  clojure.lang.PersistentList
  (format-item [tag out] (html-format-tag tag out))

  clojure.lang.Keyword
  (format-item [tag out] (append out "<" (name tag) " />"))

  clojure.lang.IFn
  (format-item [fun out] (append out (fun)))
  
  nil
  (format-item [nothing out]) ;; Nothing in, nothing out

  java.lang.String
  (format-item [string out]
	       (append out (.replace (.replace (.replace string "&" "&amp;") "<" "&lt;") ">" "&gt;")))


  java.lang.Object
  (format-item [obj out]
	       (format-item (str obj) out))
  )

(defn html-format
  "Format object as HTML to output. If called without
specifying output the object is formatted into a newly
created StringBuilder and the value returned."
  ([obj] (let [out (StringBuilder.)]
	   (html-format out obj)
	   (str out)))
  ([out obj]
     (format-item obj out)))