(in-namespace 'webjure)


;;;;;;;;;;;;;;;;;;;;;;
;; Global vars 

;; The *request* and *response* vars are bound to the HttpServletRequest
;; and HttpServletResponse of the servlet request that is currently being handled
(def #^javax.servlet.http.HttpServletRequest *request*) 
(def #^javax.servlet.http.HttpServletResponse *response*) 


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Module require functionality

(defn require [#^String module]
  (. webjure.servlet.WebjureServlet (require module)))


;;;;;;;;;;;;;;;;;;;;;
;; String utilities

(defn starts-with? [#^String str #^String prefix]
  (. str (startsWith prefix)))

(defn ends-with? [#^String str #^String suffix]
  (. str (endsWith suffix)))

(defn strlen [#^String str]
  (. str (length)))

(defn substr 
  ([#^String s start] (. s (substring start)))
  ([#^String s start end] (. s (substring start end))))

(defn append [#^java.lang.Appendable out & #^String stuff]
  (doseq thing stuff (. out (append (str thing)))))


;;;;;;;;;;;;;;;;;;;;;
;; Handler dispatch

;; List of handlers as [fn url-pattern]
(def *handlers* (list))

;; This is called to register a handler
(defn publish [fn url-pattern]
  (def *handlers*
       (cons [fn url-pattern]
	     *handlers*)))

(defn handler-matches? [handler pattern]
  (let [url-pattern (second handler)]
    (or
     (and (ends-with? url-pattern "*")
	  (starts-with? pattern (substr url-pattern 0 (- (strlen url-pattern) 1)))
	  true)
     (and (eql? pattern url-pattern)))))

(defn find-handler [url-pattern]
  (let [matching-handlers (filter (fn [handler] (handler-matches? handler url-pattern)) *handlers*)
	shortest-match    (first (sort-by (fn [handler] (strlen (second handler))) matching-handlers))]
    shortest-match))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Access to servlet info
;; Getters for request / response 


;; Servlets use a lot of old Enumerations, which need to be turned into sequences
(defn enumeration->list [#^java.util.Enumeration en]
  (loop [acc (list)]
    (if (not (. en (hasMoreElements)))
      (reverse acc)
      (recur (cons (. en (nextElement)) acc)))))


(defn request-path 
  ([] (request-path *request*))
  ([#^javax.servlet.http.HttpServletRequest request] (. request (getPathInfo))))

(defn response-writer 
  ([] (response-writer *response*))
  ([#^javax.servlet.http.HttpServletResponse response] (. response (getWriter))))

(defn request-headers 
  ([] (request-headers *request*))
  ([#^javax.servlet.http.HttpServletRequest request]
   (loop [acc {} 
	      header-names (enumeration->list (. request (getHeaderNames)))]
     (if (eql? nil header-names)
       acc
       (let [name (first header-names)]
	 (recur (assoc acc name (enumeration->list (. request (getHeaders name))))
		(rest header-names)))))))

	 
    
;;(defmacro request-bind [bindings & body]
;;  `(let [~@(reduce append
;;		   (map create-request-binding bindings))]
;;     ~@body))


(defn send-error 
  ([code message] (send-error *response* code message))
  ([#^javax.servlet.http.HttpServletResponse response code message]
   (. response (sendError code message))))


;; The main dispatch function. This is called from WebjureServlet
(defn dispatch [#^String method 
		#^javax.servlet.http.HttpServletRequest request 
		#^javax.servlet.http.HttpServletResponse response]
  (binding [*request* request
	    *response* response]
    (let [handler (first (find-handler (request-path request)))]
      (if (eql? nil handler)
	;; No handler found, give a 404
	;; PENDING: Add 404-handler support (a special dispatch url, like :default)
	(send-error 404 (strcat "No matching handler found for path: " (request-path request)))
	
	;; Run the handler
	(handler)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Useful code for webjure apps

(defn send-output 
  ([content-type content] (send-output *response* content-type content))
  ([#^javax.servlet.http.HttpServletResponse response #^String content-type #^String content]
   (. response (setContentType content-type))
   (. (. response (getWriter)) (append content))))

(defn slurp-post-data 
  ([] (slurp-post-data *request*))
  ([#^javax.servlet.http.HttpServletRequest request]
   (let [sb (new StringBuilder)
	    in (new java.io.BufferedReader (new java.io.InputStreamReader (. request (getInputStream))))]
     (loop [ch (. in (read))]
       (if (< ch 0)
	 (. sb (toString))
	 (do
	   (. sb (append (char ch)))
	   (recur (. in (read)))))))))
  

(defn is-map? [m] (instance? m clojure.lang.Associative))
(defn is-seq? [s] (instance? s clojure.lang.ISeq))
(defn is-string? [s] (instance? s java.lang.String))


(defn html-format-default [out obj]
  (append out (str obj)))

(def +type-dispatch-table+ {})
(defn html-format [out #^Object obj]
  (let [type (or (and (is-seq? obj) :seq)
		 (and (is-string? obj) :string)
		 (. obj (getClass)))
	formatter (get +type-dispatch-table+ type)]
    (if (eql? nil formatter)
      ;;(html-format-default out obj)
      (throw (new java.lang.IllegalArgumentException (strcat "No formatter for type: " (str type))))
      (apply formatter (list out obj)))))


(defn html-format-tag [out tag]
  (let [tagname (name (first tag))
	attrs   (second tag)
        content (if (is-map? attrs) (rest (rest tag)) (rest tag))]
    (append out "<" tagname)
    (if (is-map? attrs)
      (doseq kw (keys attrs)
	(append out " " (name kw) "=\"" (str (get attrs kw)) "\"")))
    (if (eql? nil content)
      (append out " />")
      (do	
	(append out ">")
	(doseq c content
	  (html-format out c))
	(append out "</" tagname ">")))))
      
(defn html-format-string [out str]
  (append out str))

(def +type-dispatch-table+
  {:seq    html-format-tag
   :string html-format-string
   })
   
(defn format-date
  ([#^String fmt #^java.util.Date date] (. (new java.text.SimpleDateFormat fmt) (format date)))
  ([#^String fmt] (. (new java.text.SimpleDateFormat fmt) (format (new java.util.Date)))))

