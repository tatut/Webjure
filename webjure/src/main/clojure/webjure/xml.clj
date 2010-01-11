;;; Defines recursive DOM tree parsing functions
;;; Author: Tatu Tarvainen
;;; Time-stamp: <2008-01-22 10:48:03 tadex>
;;;
;;; Sort of regular expressions for DOM trees:
;;; (<>* "name" ...)             Allows 0 or more child elements with the name "name".
;;;                              This is an optimized form of (? (<>+ "name ...))
;;;
;;; (<>+ "name" ...)             Allows 1 or more child elements with the name "name".
;;;
;;; (<>? "name" ...)             Allows 0 or 1 child elements with the name "name".
;;;                              This is an optimized form of (? (<> "name" ...)).
;;;
;;; (<> "name" ...)              allows exactly 1 child element with the name "name"
;;;
;;; (attr "name" ...)            allows only elements that have an attribute named "name"
;;;
;;; (attr ["name" "value"] ...)  allows only elements that have an attribute named "name"
;;;                              with the value "value"
;;;
;;; (? ...)                      The optional operator. 
;;;                              Catches failures of the sub-parsers and continues normally.
;;;
;;; (|| ...)                     The OR operator. Tries all sub-parsers and returns when the
;;;                              first one matches. If no sub-parser matches, the OR fails.
;;;
;;; (fn [node parse-state] ...)  apply function to each mached node 
;;;                             
;;; (=> var state ...)           helper macro for (fn [node] ...) that creates a function
;;;                              that returns nil at the end (unless the return value is a
;;;                              an error dictionary.
;;;
;;; All parser definition can have any amount of sub-parsers which are
;;; run in case of a match. Sub-parsers are invoked in the order they
;;; are specified (effectively ANDing them). If a parser has two sub-parsers
;;; and the first one fails, the second will not be run.

;;; A state object is passed along as the parsing is done
;;; the user fn functions can modify state by returning a new one 
;;; (default parsing functions just return the same state they got as
;;; a parameter).
;;; A return value for a handler can be [:error node description] or
;;; [:state new-state-obj].
;;;
;;; The helper macros: (error node ...description...) and (yield new-state-obj)
;;; produce the above return values.
;;;
;;; Helper functions collect and collect-as provide easy ways to capture
;;; parsed objects to user state in seq or map.
;;;
;;; ---- a very simple example ----
;;; The following parsing code:
;;;   (parse tree [] (<> "foo" (<>* "bar" (collect text))))
;;; Run on this tree:
;;;   <foo>
;;;      <bar>one</bar>
;;;      <bar>two</bar>
;;;      <bar>three</bar>
;;;   </foo>
;;; Will return the value:
;;;   ["one" "two" "three"]


(ns webjure.xml
    (:refer-clojure))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DOM utilities and accessors ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Return the type of the element
(defn type-of [#^org.w3c.dom.Node node]
  (let [node-type (. node (getNodeType))]
    (cond 
     (= node-type (. org.w3c.dom.Node ELEMENT_NODE)) :element
     (= node-type (. org.w3c.dom.Node ATTRIBUTE_NODE)) :attribute
     (= node-type (. org.w3c.dom.Node TEXT_NODE))  :text
     (= node-type (. org.w3c.dom.Node CDATA_SECTION_NODE)) :cdata-section
     (= node-type (. org.w3c.dom.Node ENTITY_REFERENCE_NODE)) :entity-reference
     (= node-type (. org.w3c.dom.Node ENTITY_NODE)) :entity
     (= node-type (. org.w3c.dom.Node PROCESSING_INSTRUCTION_NODE)) :processing-instruction
     (= node-type (. org.w3c.dom.Node COMMENT_NODE)) :comment
     (= node-type (. org.w3c.dom.Node DOCUMENT_NODE)) :document
     (= node-type (. org.w3c.dom.Node DOCUMENT_TYPE_NODE)) :document-type
     (= node-type (. org.w3c.dom.Node DOCUMENT_FRAGMENT_NODE)) :document-fragment
     (= node-type (. org.w3c.dom.Node NOTATION_NODE)) :notation)))

;; Get the name of given node
(defn name-of [#^org.w3c.dom.Node node]
  (. node (getNodeName)))

;; Check if the given node is an element (optionally check 
;; the element name also)
(defn element? 
  ([node] (= :element (type-of node)))
  ([node name] (and (= :element (type-of node))
                   (= name (name-of node)))))

;; Fetch named attribute of node
(defn attribute [#^org.w3c.dom.Element node name]
  (let [attr-node (. node (getAttributeNode name))]
    (and attr-node (. attr-node (getValue)))))

;; Return all children as a list
(defn children [#^org.w3c.dom.Node parent]
  (let [#^org.w3c.dom.NodeList 
	all-children (. parent (getChildNodes))
        child-count (. all-children (getLength))]
    (loop [i 0
	   acc []]
      (if (== i child-count)
	acc
	(recur (+ i 1)
	       (conj acc (. all-children (item i))))))))


(defn child-elements [#^org.w3c.dom.Element parent]
  (filter element? (children parent)))

;; Fetch text content 
(defn text [#^org.w3c.dom.Element node]
  (reduce str (map (fn [#^org.w3c.dom.CharacterData x] (. x (getData)))
                   (filter (fn [x] (= :text (type-of x))) (children node)))))

;;; Load an XML-file and return the DOM tree
(defn load-dom [file-or-istream]
  (let [builder (. (. javax.xml.parsers.DocumentBuilderFactory (newInstance)) (newDocumentBuilder))]
    (. builder (parse #^java.io.InputStream
                      (if (instance? java.io.File file-or-istream)
                        (new java.io.FileInputStream #^java.io.File file-or-istream)
                        file-or-istream)))))

(defn matches? [spec str]
  ;; PENDING: allow xpath style matching of word inside value 
  (= spec str))

;;; Fetch all child elements with the given name
(defn child-elements-by-name [#^org.w3c.dom.Element parent name]
  (filter 
   (fn [child] (element? child name))
   (children parent)))

(defn child-element [#^org.w3c.dom.Element parent name]
  (. (. parent (getElementsByTagName name)) (item 0)))


(defmacro error [node & items]
  `[:error ~node (reduce str (map str (list ~@items)))])

(defn yield [new-state]
  `[:state ~new-state])

(defn error-state? [state]
  (= :error (first state)))

;; Run the given sub-parsers on node 
(defn #^{:private true} run-sub-parsers [node current-state parsers]
  (loop [ps parsers
         state current-state]
    (if (or (error-state? state) 
            (empty? ps))
      state
      (recur (rest ps)
	     ((first ps) node state)))))

;; Run the given sub-parsers on each child
(defn do-children [children current-state parsers]
  (loop [ch children
         state current-state]
    (if (or (error-state? state)
            (empty? ch))
      state
      (recur (rest ch)
             (run-sub-parsers (first ch) state parsers)))))
    

(defn parse [tree initial-state & parsers]
  (let [res (run-sub-parsers tree (yield initial-state) parsers)]
    (if (error-state? res)
      res
      (second res))))

;; Parse as a sub-parser, returns a function that takes the
;; node to parse.
;; This can be used to change state for sub parsers.
(defn parse* [new-state & parsers]
  (fn [node]
      (let [res (run-sub-parsers node (yield new-state) parsers)]
        (if (error-state? res)
          res
          (second res)))))

(def p* parse*) ;; short hand for parse* 

;; Evaluate exprs for side-effects, does not change state
(defmacro => [nodesym statesym & exprs]
  `(fn [~nodesym state#]       
       (let [~statesym (second state#)]
	 (let [newstate# (do ~@exprs)]
	   (if (nil? newstate#)
	     state#
	     newstate#)))))


;; Allow exactly one child with the given name
(defn <> [name & sub-parsers]
  (fn [parent state]      
      (let [children (child-elements-by-name parent name)
            count (count children)]
        (if (== count 1)
	  (do-children children state sub-parsers)
          (error  parent "Expected exactly one " name 
                  " child element in node " (name-of parent))))))

      
;; Allow zero or more children with the given name
(defn <>* [name & sub-parsers]
  (fn [parent state]
      (do-children (child-elements-by-name parent name) state sub-parsers)))

;; Allow zero or one child with the given name
(defn <>? [name & sub-parsers]
  (fn [parent state]
      (let [children (child-elements-by-name parent name)]
        (if (not (nil? (second children)))
          (error parent "Expected at most one " name " child element in node " (name-of parent))
          (do-children children state sub-parsers)))))

;; Allow one or more children with the given name
(defn <>+ [name & sub-parsers]
  (fn [parent state]
      (let [children (child-elements-by-name parent name)]
        (if (nil? (first children))
          (error parent "Expected one or more " name " child elements in node " (name-of parent))
          (do-children children state sub-parsers)))))


;; Require attributes (and optionally value)
;; attr-spec = attr-name | [attr-name attr-value]
;; FIXME: attr has some problem with state
(defn attr [attr-spec & sub-parsers]
  ;; FIXME: implement as before, now just requires named attribute
  (fn [parent state] 
      (if (not (element? parent))
        (error parent "Tried to check attribute on a non-element node")
        
        (let [attr-value (if (instance? java.lang.String attr-spec) nil (second attr-spec)) 
              attr-name (or (and attr-value (first attr-spec)) attr-spec)
              attr-node (. #^org.w3c.dom.Element parent (getAttributeNode attr-name))]
          (if (nil? attr-node)
            (error parent "Element " (name-of parent) " does not have required attribute " attr-name)
            
            (if (or (nil? attr-value) (matches? attr-value (. attr-node (getValue))))
              (run-sub-parsers attr-node state sub-parsers)
              (error parent "Element attribute " attr-name " does not match " attr-value)))))))

        
;;   (let ((name (if (pair? attr-spec)
;;                   (car attr-spec)
;;                   attr-spec))
;;         (value (if (pair? attr-spec)
;;                    (cadr attr-spec))))

;;     (lambda (parent error)
;;       (if (not (eqv? Node.ELEMENT_NODE$ (.getNodeType parent)))
;;           (error (cons parent {Tried to check attribute on a non-element node: [parent]}))

;;           (let ((attr (.getAttributeNode parent name)))
            
;;             (if (eq? attr #null)
;;                 (error (cons parent {Element [(.getNodeName parent)] doesn't have required attribute [name]}))

;;                 (if (or (not (pair? attr-spec)) (and value (string=? value (.getValue attr))))
;;                     (run-sub-parsers attr sub-parsers error)
;;                     (error (cons parent {Attribute [name] doesn't have the required value [value]})))))))))

;;; Catch failures of the sub-parsers and continue as if
;;; no error occured.
(defn ? [& sub-parsers]
  (fn [node]
      ;; PENDING: Should we catch each sub-parsers separately?
      ;; We could wrap then in fns that catch exceptions
      (try (run-sub-parsers node sub-parsers)
           (catch java.lang.RuntimeException x true))))


;;; The OR operator
(defn || [& sub-parsers]
  (fn [node state]
      (loop [sp sub-parsers]
        (if (empty? sp)
          ;; No sub-parser succeeded, throw an exception
          (error node "OR operator failed for node " (name-of node))

          (if (not (try ((first sp) node) true
                        (catch java.lang.RuntimeException ex false)))
            (recur (rest sp)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parsing actions helpers
;;


;; Collect the value obtained by applying fun to the node
;; as a keyed map value. Yields a new state with the new
;; key/value mapped. The old state must be a map.
;; If the fun is omitted, identity is used (collecting the node).
(defmacro collect-as 
  ([key] (collect-as key identity))
  ([key fun]
   `(=> elt# st#
	(yield (assoc st# ~key (~fun elt#))))))
      
;; Collect a new sequence value obtained by applying fun to the
;; node. Yields a new state with the new value conj'ed to the
;; previous state (which must be a seq).
;; If the fun is omitted, identity is used (collecting the node).
(defmacro collect 
  ([] (collect identity))
  ([fun] `(=> elt# st#
	      (yield (conj st# (~fun elt#))))))
  

;; Modify state by applying a function to the previous state and the element
(defmacro modify [fun]
  `(=> elt# st#
       (yield (~fun st# elt#))))

(defmacro modify-key [key fun]
  `(=> elt# st# 
       (yield (assoc st#
		     ~key (~fun (st# ~key) elt#)))))