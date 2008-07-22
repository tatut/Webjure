(in-ns 'webjure)
(clojure/refer 'clojure)


;;;;;;;;;;;;;;;;;;;;;;
;; Global vars 

;; The *request* and *response* vars are bound to the HttpServletRequest
;; and HttpServletResponse of the servlet request that is currently being handled
(def #^webjure.Request *request*) 
(def #^webjure.Response *response*) 


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Module require functionality

(defn require [#^String module]
  (. webjure.Webjure (require module)))


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

(defn urlencode 
  ([#^String s] (urlencode s "UTF-8"))
  ([#^String s #^String encoding] 
     (. java.net.URLEncoder (encode s encoding))))

(defn urldecode
  ([#^String s] (urldecode s "UTF-8"))
  ([#^String s #^String encoding]
     (. java.net.URLDecoder (decode s encoding))))
     

;;;;;;;;;;;;;;;;;;;;;
;; Handler dispatch

;; List of handlers as [fn url-pattern]
(def *handlers* (list))

;; This is called to register a handler
(defn publish [fn url-pattern]
  (if (not (instance? clojure.lang.IFn fn))
    (throw (new java.lang.IllegalArgumentException "First argument must be function")))
  (def *handlers*
       (conj *handlers*
             [fn url-pattern])))

(defn handler-matches? [handler pattern]
  (let [url-pattern (second handler)]
    (or
     (and (ends-with? url-pattern "*")
	  (starts-with? pattern (substr url-pattern 0 (- (strlen url-pattern) 1)))
	  true)
     (and (= pattern url-pattern)))))

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
      (recur (conj acc (. en (nextElement)))))))


(defn request-path 
  ([] (request-path *request*))
  ([#^webjure.Request request] (. (. request (getActualRequest)) (getPathInfo))))

(defn response-writer 
  ([] (response-writer *response*))
  ([#^webjure.Response response] (. response (getWriter))))

(defn request-headers 
  ([] (request-headers *request*))
  ([#^webjure.Request servlet-request]    
     (let [#^javax.servlet.http.HttpServletRequest request 
           (. servlet-request (getActualRequest))]
       (loop [acc {} 
              header-names (enumeration->list 
                            (. request (getHeaderNames)))]
         (if (= nil header-names)
           acc
           (let [name (first header-names)]
             (recur (assoc acc name (enumeration->list (. request (getHeaders name))))
                    (rest header-names))))))))


;; Dynamically calculate and return the app baseurl
;; based on the current request
(defn base-url 
  ([] (base-url (. *request* (getActualRequest))))
  ([#^javax.servlet.http.HttpServletRequest request]
     (str (. request (getScheme))
	  "://"
	  (. request (getServerName))
	  (let [port (. request (getServerPort))]
	    (if (not (or (== port 80) (== port 443)))
	      (str ":" port)
	      ""))
	  (. request (getContextPath)))))

(defn create-servlet-url [#^javax.servlet.HttpServletRequest request path args]
  (str
   (base-url request)
   path
   "?" 
   (reduce str
           (interleave (map (fn [key]
                              (str (urlencode (if (keyword? key)
                                                (substr (str key) 1)
                                                key))
                                   "=" (urldecode (get args key))))
                            (keys args))
                       (repeat "&")))))
                         
(defn create-portlet-url [#^javax.portlet.PortletRequest request mode args] 
  (let [url (if (= :action mode)
              (. *response* (createActionURL))
              (. *response* (createRenderURL)))]
    (doseq key (keys args)
      (. url (setParameter key (get args key))))
    (. url (toString))))

;; Generate an HREF link.
;; For portlets the mode-or-path must be :action or :render
;; and for servlets it must be a string path element
(defn url 
  ([mode-or-path] (url mode-or-path {}))
  ([mode-or-path args]
     (if (string? mode-or-path)
       ;; create HREF from base url
       (create-servlet-url (. *request* (getActualRequest)) mode-or-path args)
       ;; create action or render url for portlet
       (create-portlet-url (. *request* (getActualRequest)) mode-or-path args))))

(defn request-parameter [#^String name]
  (. *request* (getParameter name)))
(defn multi-request-parameter [#^String name]
  (seq (. *request* (getParameterValues name))))

;; Return mapping {"param name" [values], ...}
;; or request parameters
(defn request-parameters 
  ([] (request-parameters *request*))
  ([#^webjure.Request request] 
     (let [param-map (. request (getParameterMap))]
       (loop [acc {}
              param-names (seq (. param-map (keySet)))]
         (if (nil? param-names)
           acc
           (recur
            (assoc acc
              (first param-names) (seq (. param-map (get (first param-names)))))
            (rest param-names)))))))

       
       
(defn generate-request-binding [sym accessor]
  (if (instance? java.lang.String accessor)
    `[~sym (request-parameter ~accessor)]
    (let [multi (:multiple accessor)
	  name (:name accessor)
	  validator (or (:validator accessor) 'identity)]
      (if multi
	`[~sym (map ~validator (multi-request-parameter ~name))]
	`[~sym (~validator (request-parameter ~name))]))))

;; Bind request parameters (GET/POST) to variables
;; A binding is a symbol and an access definition.
;; The access definition can be a string or a map containing
;; options. If the definition is a string the named parameter
;; is just returned as a string.
;; For option map access definitions, the following option
;; keys can be used: :name (the request param name, required),
;; :multiple (if true the value is a seq of values, defaults to no) and
;; :validator (a form that returns the validated value of the parameter value)
;; 
(defmacro request-bind [bindings & body]
  `(let [~@(loop [forms nil
                  splits (split-at 2 bindings)]
	     (let [binding (first splits)]
	       (if (nil? binding)
		 forms
		 (recur 
		  (concat (apply generate-request-binding binding) forms)
		  (split-at 2 (second splits))))))] 
     ~@body))

  
;; Fetch the value of a session attribute
;; if initial-value is specified and the given
;; attribute does not exist in the session, 
;; the initial-value stored in the session and
;; returned. If initial-value is an IFn then
;; it is invoked to produce the value to store.
(defn session-get 
  ([attribute] (. (. *request* (getSession)) (getAttribute attribute)))
  ([attribute initial-value]
   (let [val (session-get attribute)]
     (if (nil? val)
       (let [new-value (or (and (instance? clojure.lang.IFn initial-value)
				(initial-value))
			   initial-value)]
	 (. (. *request* (getSession)) (setAttribute attribute new-value))
	 new-value)
       val))))



(defn send-error 
  ([code message] (send-error *response* code message))
  ([#^webjure.Response response code message]     
     (. (. response (getActualResponse)) (sendError code message))))


;; The main dispatch function. This is called from WebjureServlet
(defn dispatch [#^String method 
		#^webjure.Request request 
		#^webjure.Response response]
  (binding [*request* request
	    *response* response]
    (let [handler (first (find-handler (request-path *request*)))]
      (if (= nil handler)
	;; No handler found, give a 404
	;; PENDING: Add 404-handler support (a special dispatch url, like :default)
	(send-error 404 (str "No matching handler found for path: " (request-path request)))
	
	;; Run the handler
	(handler)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Useful code for webjure apps

(defn send-output 
  ([content-type content] (send-output *response* content-type content))
  ([#^webjure.Response response #^String content-type #^String content]
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
  

(defn is-map? [m] (instance? clojure.lang.Associative m))
(defn is-seq? [s] (instance? clojure.lang.ISeq s))
(defn is-string? [s] (instance? java.lang.String s))


(defn html-format-default [out obj]
  (append out (str obj)))

(def +type-dispatch-table+ {})
(defn html-format [out #^Object obj]
  (let [type (or (and (is-seq? obj) :seq)
		 (and (is-string? obj) :string)
		 (. obj (getClass)))
	formatter (get +type-dispatch-table+ type)]
    (if (= nil formatter)
      ;;(html-format-default out obj)
      (throw (new java.lang.IllegalArgumentException (str "No formatter for type: " (str type))))
      (apply formatter (list out obj)))))


(defn html-format-tag [out tag]
  (let [tagname (name (first tag))
	attrs   (second tag)
        content (if (is-map? attrs) (rest (rest tag)) (rest tag))]
    (append out "<" tagname)
    (if (is-map? attrs)
      (doseq kw (keys attrs)
	(append out " " (name kw) "=\"" (str (get attrs kw)) "\"")))
    (if (= nil content)
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


;; Define a handler function
;; options is a map of automagic behaviour.
;; currently supported is {:output <type>} (<type> can be :html)
;; that automatically sends the return value as a response 
(defmacro defh [url request-bindings options & body]
  `(publish (fn []
              (request-bind ~request-bindings
                            ~@(if (= :html (:output options))
                                `(do
                                   (. *response* (setContentType "text/html"))
                                   (html-format (response-writer)
                                                (do ~@body)))
                                body)))
	    ~url))

  
