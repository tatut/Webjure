;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CPT - Clojure Page Templates
;;
;; A *FAST* of a JPT/ZPT alike templating system in Clojure.
;; Compiles XML template files into byte code via the Clojure compiler.
;;

(ns webjure.cpt
  (:use webjure.xml)
  (:refer-clojure))

;; (set! *warn-on-reflection* true)

(defmacro output [& things]
  `(do
     ~@(map (fn [t]
	      (if (string? t)
		`(.write ~'output ~t)
		`(.write ~'output (str ~t)))) things)))

(comment
  (defn output "Apply str to things and print the result" [& things]
    (print (apply str things))))


(defn- load-template-xml [path]
  (let [xml-string (slurp path)]
    (.getDocumentElement
     (load-dom 
      (if (.startsWith xml-string "<?xml")
	xml-string
	(str "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" xml-string))))))
 
(def +cpt-ns+  "http://webjure.org/namespaces/cpt")

(defn- cpt-attribute? [{name :name}]
  (or (= name "xmlns:cpt")
      (.startsWith name "cpt:")))

(defn- attr-seq 
  ([elt] (attr-seq (.getAttributes elt) 0))
  ([node-map index]
     (if (>= index (.getLength node-map))
       nil
       (cons (let [attr (.item node-map index)]
	       {:name (.getName attr)
		:value (.getValue attr)
		:ns (.getNamespaceURI attr)})
	     (attr-seq node-map (+ index 1))))))

(defmulti handle-node (fn [_ elt] (type-of elt)))

(defn- string-reader [str]
  (java.io.PushbackReader. (java.io.StringReader. str)))

(defn- read-many 
  ([str] (read-many (string-reader str) []))
  ([rdr acc]
     (let [item (read rdr false :eof)]
       (if (= item :eof)
	 acc
	 (read-many rdr (conj acc item))))))

(defn- read-first "Read the first form in the given input string. Returns the read item and the remaining string."
  [string]
  (let [rdr (string-reader string)
	item (read rdr)]
    (loop [acc ""
	   ch (.read rdr)]
      (if (= -1 ch)
	[item acc]
	(recur (str acc (char ch))
	       (.read rdr))))))
  

(defn- get-and-remove-attribute [elt attr]
  (let [value (.getAttribute elt attr)]
    (.removeAttribute elt attr)
    (if (empty? value)
      nil
      value)))

(defmacro ^{:private true} define-attribute-handler [name attribute ctx-var attr-var elt-var & body]
  `(defn ~name [~ctx-var ~elt-var]     
      (let [~attr-var (get-and-remove-attribute ~elt-var ~attribute)]
        (if (not (nil? ~attr-var))
	  (do ~@body)
	  nil))))

(declare handle-element)

(define-attribute-handler handle-let "cpt:let" ctx value elt
  `(let [~@(read-many value)]
     ~(handle-element ctx elt)))

(define-attribute-handler handle-when "cpt:when" ctx value elt
  `(when ~(read-string value)
     ~(handle-element ctx elt)))

(define-attribute-handler handle-repeat "cpt:repeat" ctx value elt
  (let [[var items] (read-many value)
	var-idx (symbol (str var "-idx"))]
    `(let [items# ~items
	   item-count# (count items#)]
       (loop [[item# & items#] items#
	      i# 0
	      ~var-idx 0]
       (when (< i# item-count#)
	 (let [~var item#]
	   ~(handle-element ctx (.cloneNode elt true))
	   (recur items# (+ i# 1) (+ 1 ~var-idx))))))))

(define-attribute-handler handle-replace "cpt:replace" ctx value elt
  `(output ~@(read-many value)))

(define-attribute-handler handle-include "cpt:include" ctx value elt
  (let [base-path (.getParentFile (:template-file ctx))
	included-template (java.io.File. base-path value)
	include (load-template-xml (.getAbsolutePath included-template))
	ctx (assoc ctx :template-file included-template)]
    (handle-node ctx include)))

(defn escape-xml "Escape XML entities: &, < and >." [evil]
  (let [^String s (str evil)]
    (.replace (.replace (.replace s "&" "&amp;")
			"<" "&lt;")
	      ">" "&gt;")))

(defn handle-text "Expands code to output text with $ form references." 
  ([text] (handle-text text []))
  ([text acc]     
     (if (empty? text)
       `(output ~@acc)
       (let [dollar-pos (.indexOf text "$")]
	 (if (= -1 dollar-pos)
	   ;; No expansions, output whole text
	   `(output ~@acc ~text)
	   ;; Expansion found
	   (let [before (.substring text 0 dollar-pos)
		 text (.substring text (+ 1 dollar-pos))
		 escape? (not (.startsWith text "@"))
		 text (if escape? text (.substring text 1))
		 [item after] (read-first text)]
	     (handle-text after (concat acc [before (if escape?
						      `(escape-xml ~item)
						      item)]))))))))


	 
(defn handle-element [ctx elt]
  (.normalize elt) ;; ensure Text nodes are intact
  (or (handle-let ctx elt)
      (handle-when ctx elt)
      (handle-repeat ctx elt)
      (handle-replace ctx elt)
      (handle-include ctx elt)
      
      ;; Normal handling, after most specials have been taken care of
      (let [tag (.getTagName elt)
	    attrs (filter #(not (cpt-attribute? %)) (attr-seq elt))
	    children (children elt)]	
	(if (and (empty? attrs) (empty? children))
	  `(output ~(str "<" tag "/>"))
	  `(do (output ~(str "<" tag))
	       ~@(let [cpt-attributes (first (filter #(= "cpt:attributes" (:name %)) (attr-seq elt)))]
		   (when cpt-attributes
		     `[ (doseq [[n# v#] ~(read-string (:value cpt-attributes))]
			  (output " " n#)
			  (when v#
			    (output "=\"" v# "\""))) ]))
	       
	       ~@(map (fn [{name :name, value :value}]
			`(do 
			   (output ~(str " " name "=\""))
			   ~(handle-text value)
			   (output "\"")))
		      attrs)
	       (output ">")
	       ~@(map #(handle-node ctx %) children)
	       (output ~(str "</" tag ">")))))))
      
(defmethod handle-node :element [ctx elt]
  (handle-element ctx elt))

(defmethod handle-node :default [ctx node]
  true)

(defmethod handle-node :text [ctx node]
  ;; NOTE: If the document is not in the normalized form, text may be split into
  ;; multiple adjacent text nodes. We may need to preprocess and join them... 
  (handle-text (.getWholeText node)))


(defn output-form? [form]
  (and (coll? form)
       (= 'webjure.cpt/output (first form))))

(defmacro ^{:private true} define-form-reduction [name initial-test-fn reduce-test-fn reduce-fn]
  `(defn ^{:private true} ~name [form#]
     (if (not (~initial-test-fn form#))
       form#
       (loop [acc# []
	      [current# & items#] form#]
	 (if (nil? current#)
	   (apply list acc#)
	   (if (not (~reduce-test-fn current#))
	     (recur (conj acc# (~name current#)) items#)
	     ;; current item is reduceable
	     (if (~reduce-test-fn (last acc#))
	       ;; previous item is reduceable, combine with that
	       (recur (conj (vec (butlast acc#))
			    (~reduce-fn (last acc#) current#))
		      items#)
	       (recur (conj acc# current#) items#))))))))

;; Define reduction to reduce adjacent string literals
;; eg. (output "foo" "bar") => (output "foobar")
(define-form-reduction optimize-output-form 
  output-form? string? str)
  
;; Define reduction to reduce adjacent output calls to a single call
;; eg. (do (output "foo") (output "bar")) => (do (output "foo" "bar"))
;; Calls optimize-output-form on the resulting output calls to further
;; optimize them.
(define-form-reduction optimize-adjacent-output-forms 
  #(and (not (vector? %)) (coll? %))
  output-form?
  (fn [left right]
    (optimize-output-form `(output ~@(rest left) ~@(rest right)))))

(defn- do-form? [form]
  (and (coll? form) (= 'do (first form))))

(defn flatten-nested-do "Flatten code in nested do structures. Turns (do a b (do c d)) into (do a b c d)" 
  [form]
  (if (or (vector? form) (not (coll? form)))
    form ;; not a list form, just return this
    (if (not (do-form? form))
      ;; not a (do ...) form, apply optimization recursively
      (map flatten-nested-do form)
      (loop [acc []
	     [item & items] (rest form)]
	(if (nil? item)
	  `(do ~@acc)
	  (let [optimized-item (flatten-nested-do item)]
	    (if (do-form? optimized-item)
	      (recur (concat acc (vec (rest optimized-item)))
		     items)
	      (recur (conj (vec acc) optimized-item)
		     items))))))))
    
(defn optimize [form]
  (optimize-adjacent-output-forms (flatten-nested-do form)))

	
(def *template-path* nil)

(defn- template-path []
  (or *template-path* (System/getProperty "webjure.cpt.path")))

(defmacro define-template 
  "Define a template as a function at compile time."
  [name file]  
  (let [file (java.io.File. (template-path) file)
	ctx {:template-file file}]
    (if (not (.canRead file))
      (throw (IllegalArgumentException. (str "Unable to define template \"" (.getCanonicalPath file) "\". The specified file cannot be read. (Maybe you need to define \"webjure.cpt.path\" system property)")))
      `(defn ~name 
	 ([~'here] (~name *out* ~'here))
	 ([~'^java.io.Writer output ~'here]
	 ~@(optimize (handle-node ctx (load-template-xml (.getCanonicalPath file)))))))))

(defmacro template [file]
  "Compile a template into a function. (fn [here] ...)"
  (let [ctx {:template-file (java.io.File. file)}]
    `(fn [~'output ~'here]
       ~@(optimize (handle-node ctx (load-template-xml file))))))