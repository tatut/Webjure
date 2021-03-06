
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Webjure - a web framework for Clojure
;;
;; Author: Tatu Tarvainen
;;

(ns webjure
    (:refer-clojure)
    (:require (webjure html json)))

;;;;;;;;;;;;;;;;;;;;;;
;; Global vars 

(def +version+ "Webjure 0.8")

;; The *request* and *response* vars are bound to the servlet/portlet request and
;; response objects fo the request currently being handled.
(def *request*) 
(def *response*) 

(def #^{:doc "The matched handler info is bound here during dispatch."}
     *matched-handler* nil)


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
  (doseq [thing stuff]
      (. out (append (str thing)))))

(defn 
  #^{:doc "URL encode a string."}
  urlencode 
  ([#^String s] (urlencode s "UTF-8"))
  ([#^String s #^String encoding] 
     (. java.net.URLEncoder (encode s encoding))))

(defn 
  #^{:doc "Decode an URL encoded string."}
  urldecode
  ([#^String s] (urldecode s "UTF-8"))
  ([#^String s #^String encoding]
     (. java.net.URLDecoder (decode s encoding))))
     

;;;;;;;;;;;;;;;;;;;;;
;; Handler dispatch

;; List of handlers as [fn url-pattern]
(def *handlers* (ref (list)))

;; This is called to register a handler
(defn 
  #^{:doc "Publish a handler function for the given URL pattern. The pattern may be a string or a regular expression pattern."}
  publish [fn url-pattern]
  (if (not (instance? clojure.lang.IFn fn))
    (throw (new java.lang.IllegalArgumentException "First argument must be function")))
  (dosync 
   (ref-set *handlers*
	    (conj (filter 
		   #(not (= url-pattern (second %))) ; filter out previous handlers with the same pattern
		   @*handlers*)
		  [fn url-pattern]))))

(defn 
  #^{:private true :doc "Check if handler matches the input URL. Returns a match object (map) or nil."}
  handler-matches? [[handler-fn url-pattern] pattern]
  (cond
   (instance? java.util.regex.Pattern url-pattern)
   (let [m (re-find url-pattern pattern)]
     (if m
       {:handler handler-fn
        :priority 0
        :groups m}
	nil))
    
   (or (and (ends-with? url-pattern "*")
	    (starts-with? pattern (substr url-pattern 0 (- (strlen url-pattern) 1)))
	    true)
       (and (= pattern url-pattern)))
   {:handler handler-fn :priority (strlen url-pattern)}))


(defn 
  #^{:private true}
  find-handler [url-pattern]
  (let [matching-handlers (filter #(not (nil? %))
				  (map (fn [handler] 
					   (handler-matches? handler url-pattern)) @*handlers*))
	shortest-match    (first (sort-by :priority matching-handlers))]
    shortest-match))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Access to servlet info
;; Getters for request / response 


;; Servlets use a lot of old Enumerations, which need to be turned into sequences
(defn 
  #^{:doc "Convert a Java Enumeration into a Clojure sequence."}
  enumeration->list [#^java.util.Enumeration en]
  (loop [acc (list)]
    (if (not (. en (hasMoreElements)))
      (reverse acc)
      (recur (conj acc (. en (nextElement)))))))

(defprotocol Request
  "Webjure request abstraction"
  (get-request-path [x])
  (get-request-headers [x])
  (get-request-param [x name])
  (get-request-param-values [x name])
  (get-request-params [x])
  (get-request-base-url [x])
  (get-request-input-stream [x])
  (create-url [x mode-or-path args])
  (get-request-session-attribute [x attribute])
  (set-request-session-attribute [x attribute value])
  (remove-request-session-attribute [x attribute]))

(defprotocol Response
  "Webjure response abstraction"
  (get-response-writer [x])
  (send-response-error [x error-code error-message])
  (set-response-content-type [x type])
  (send-response-redirect [x to]))

;; Implement the Request abstraction for HTTP Servlets 
(extend-protocol Request
  javax.servlet.http.HttpServletRequest
  (get-request-path [req] (.getPathInfo req))
  (get-request-headers
   [req]
   (let [names (enumeration->list (.getHeaderNames req))]
     (zipmap names
	     (map #(enumeration->list (.getHeaders req %)) names))))
  (get-request-param
   [req name]
   (.getParameter req name))
  (get-request-param-values
   [req name]
   (seq (.getParameterValues req name)))
  (get-request-params
   [req]
   (let [params (seq (.getParameterMap req))]
     (zipmap (map first params)
	     (map #(seq (second %)) params))))
  (get-request-base-url
   [req]
   (str (.getScheme req)
	"://"
	(.getServerName req)
	(let [port (.getServerPort req)]
	  (if (not (or (== port 80) (== port 443)))
	    (str ":" port)
	    ""))
	(.getContextPath req)))
  (get-request-input-stream
   [req]
   (.getInputStream req))
  (create-url
   [req path args]
   (str
    (get-request-base-url req)
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
  (get-request-session-attribute
   [req attribute]
   (.getAttribute (.getSession req) attribute))
  (set-request-session-attribute
   [req attribute value]
   (.setAttribute (.getSession req) attribute value))
  (remove-request-session-attribute
   [req attribute]
   (.removeAttribute (.getSession req) attribute))
  )

(extend-protocol Response
  javax.servlet.http.HttpServletResponse
  (get-response-writer [res] (.getWriter res))
  (send-response-error [res error-code error-message] (.sendError res error-code error-message))
  (set-response-content-type [res type]
			     (.setContentType res type))
  (send-response-redirect [res to]
			  (.sendRedirect res to)))

		      
(defn 
  #^{:doc "Returns the request path information (servlet only)"}
  request-path 
  ([] (request-path *request*))
  ([request] (get-request-path request)))

(defn 
  #^{:doc "Get a Writer object for this response."}
  response-writer 
  ([] (response-writer *response*))
  ([response] (. response (getWriter))))

(defn request-headers 
  ([] (request-headers *request*))
  ([request] (get-request-headers request)))


;; Dynamically calculate and return the app baseurl
;; based on the current request
(defn base-url 
  ([] (base-url *request*))
  ([request]
     (get-request-base-url request)))

;; FIXME: implement Request abstraction fro PortletRequest 
(defn 
  ^{:private true}
  create-portlet-url [^javax.portlet.PortletRequest request mode args] 
  (let [url (if (= :action mode)
              (. *response* (createActionURL))
              (. *response* (createRenderURL)))]
    (doseq [key (keys args)]
      (. url (setParameter key (get args key))))
    (. url (toString))))

;; Generate an HREF link.
;; For portlets the mode-or-path must be :action or :render
;; and for servlets it must be a string path element
(defn url "Generate an HREF URL given a path (or mode for portlets) and GET parameters."
  ([mode-or-path] (url mode-or-path {}))
  ([mode-or-path args]
     (create-url *request* mode-or-path args)))


(defn request-parameter "Get the value of a single valued request parameter."
  [^String name]
  (get-request-param *request* name))


(defn   
  multi-request-parameter "Get the values of a multi valued request parameter as a sequence."
  [^String name]
  (get-request-param-values *request* name))


;; Return mapping {"param name" [values], ...}
;; of request parameters
(defn request-parameters "Return a mapping of parameter names to sequences of values."
  ([] (request-parameters *request*))
  ([request] 
     (get-request-params request)))

       
       
(defn generate-request-binding [sym accessor]
  (cond 
   (instance? String accessor)
   `[~sym (request-parameter ~accessor)]

   (instance? Number accessor)
   `[~sym (nth (*matched-handler* :groups) ~accessor)]

   :default
   (let [multi (:multiple accessor)
	 name (:name accessor)
	 group (:group accessor)
	 validator (or (:validator accessor) 'identity)]
     `[~sym ~(if group
	       `(~validator (nth (*matched-handler* :groups) ~group))
	       (if multi
		 `(map ~validator (multi-request-parameter ~name))
		 `(~validator (request-parameter ~name))))])))

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
	       (if (empty? binding)
		 forms
		 (recur 
		  (concat forms (apply generate-request-binding binding))
		  (split-at 2 (second splits))))))] 
     ~@body))

  
;; Fetch the value of a session attribute
;; if initial-value is specified and the given
;; attribute does not exist in the session, 
;; the initial-value stored in the session and
;; returned. If initial-value is an IFn then
;; it is invoked to produce the value to store.
(defn session-get "Get a stored value from the client session by key. Initial value (which may be a function that generates a value) will be used if specified and no value is stored in the session."
  ([attribute] (get-request-session-attribute *request* attribute))
  ([attribute initial-value]
     (let [val (get-request-session-attribute *request* attribute)]
       (if (nil? val)
	 (let [new-value (if (fn? initial-value)
			   (initial-value)
			   initial-value)]
	   (set-request-session-attribute *request* attribute new-value)
	   new-value)
	 val))))

(defn session-set "Store a value by key in the client session." 
  [name value]
  (set-request-session-attribute *request* name value))

(defn session-remove "Remove a value by key in the client session."
  [name]
  (remove-request-session-attribute *request* name))

(defn send-error 
  ([code message] (send-error *response* code message))
  ([response code message]     
     (send-response-error response code message)))


;; The main dispatch function. This is called from WebjureServlet
(defn dispatch [^String method 
		request 
		response]
  (binding [*request* request
	    *response* response]
    (binding [*matched-handler* (find-handler (request-path *request*))]
      (if (= nil *matched-handler*)
	;; No handler found, give a 404
	;; PENDING: Add 404-handler support (a special dispatch url, like :default)
	(send-error 404 (str "No matching handler found for path: " (request-path request)))
	
	;; Run the handler
	((*matched-handler* :handler))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Useful code for webjure apps

(defn send-output "Send string output to client with given content-type."
  ([content-type content] (send-output *response* content-type content))
  ([response ^String content-type ^String content]
   (set-response-content-type response content-type)
   (.append (get-response-writer response) content)))

(defn slurp-post-data "Read POST data and return it as a  string."
  ([] (slurp-post-data *request*))
  ([request]
     (let [sb (new StringBuilder)]
       (with-open [in (new java.io.BufferedReader 
			   (new java.io.InputStreamReader 
				(get-request-input-stream request)))]
	 (loop [ch (.read in)]
	   (if (< ch 0)
	     (.toString sb)
	     (do
	       (.append sb (char ch))
	       (recur (.read in)))))))))



(defn format-date "Format date using a SimpleDateFormat pattern."
  ([^String fmt ^java.util.Date date] (.format (java.text.SimpleDateFormat. fmt) date))
  ([^String fmt] (.format (java.text.SimpleDateFormat. fmt) (java.util.Date.))))


;; Define a handler function
;; options is a map of automagic behaviour.
;; currently supported is {:output <type>} (<type> can be :html)
;; that automatically sends the return value as a response 
(defmacro defh [url request-bindings options & body]
  `(publish (fn []
              (request-bind ~request-bindings
                            ~@(cond
			       ;; Output anything that is printed
			       (= :print (options :output))
			       `((set-response-content-type *response* (or ~(options :content-type) "text/html"))
				 (binding [*out* (response-writer)]
				   ~@body))

			       ;; Output CSV 
			       (= :csv (options :output))
			       `((set-response-content-type *response* (or ~(options :content-type) "text/csv"))
				 (webjure.csv/csv-format (do ~@body)))
				       
				   
			       ;; Output text/plain
			       (= :text (options :output))
			       `((send-output "text/plain" (do ~@body)))
			       
			       ;; Output HTML with optional doctype declaration
			       (= :html (options :output))
			       `((let [out# (response-writer)
				       doctype# ~(:doctype options)]
				   (set-response-content-type *response* "text/html")
				   (if doctype#
				     (append out# (str doctype# "\n")))
				   (webjure.html/html-format out# (do ~@body))))

			       ;; Output JSON 
			       (= :json (options :output))
			       `((set-response-content-type *response* "application/json")
				 (webjure.json/serialize (response-writer)
							 (do ~@body)))

			       :default body)))
	    ~url))

  
(defmacro define-static-resource [file content-type url-path]
  (let [content (slurp file)]
    `(defh ~url-path [] {}
       (send-output ~content-type ~content))))
