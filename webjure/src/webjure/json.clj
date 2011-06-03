
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; JSON (JavaScript Object Notation) serialization support for Webjure
;;
;; Author: Tatu Tarvainen
;; Since: 2008-11-23 
;; Time-stamp: <2009-05-12 14:19:49>

(ns webjure.json
    (:refer-clojure))

(defn #^{:private true} determine-serializer [_ thing]
  (cond 
   (nil? thing) :null
   (map? thing) :map
   (coll? thing) :array
   (string? thing) :string
   :default :default))

(defmulti #^{:doc "Serialize Clojure data structure in JSON format to output. Takes a writer and an object."}
	  serialize determine-serializer)

(defn #^{:doc "Serialize Clojure data structure in JSON format to a string."}
  serialize-str [thing]
  (let [out (new java.io.StringWriter)]
    (serialize out thing)
    (str out)))

(defmethod serialize :null [out _]
  (.append out "null"))

(defmethod serialize :map [out the-map]
  (.append out "{")
  (loop [first-entry true
	 entries the-map]
    (when-let [[key value] (first entries)]
	(do 
	  (if (not first-entry)
	    (.append out ", "))
	  (serialize out key) 
	  (.append out ": ")
	  (serialize out value)
	  (recur false (rest entries)))))
  (.append out "}"))

(def +control-chars-replacements+ 
     [["\\" "\\\\"]
      ["\"" "\\\""]
      ["\b" "\\b"]
      ["\f" "\\f"]
      ["\n" "\\n"]
      ["\r" "\\r"]
      ["\t" "\\t"]])

(defmethod serialize :string [out string]
  (.append out "\"")
  (loop [string string
         ctrls +control-chars-replacements+]
    (if (empty? ctrls)
      (.append out string)
      (let [[ctrl replacement] (first ctrls)]
	(recur (.replace string ctrl replacement)
	       (rest ctrls)))))
  (.append out "\""))

(defmethod serialize :default [out something]
  (.append out (str something)))

(defmethod serialize :array [out things]
  (.append out "[")
  (loop [first-element true
	 things (seq things)]
    (if (empty? things)
      (.append out "]")
      (do (if (not first-element)
	    (.append out ", "))
	  (serialize out (first things))
	  (recur false (rest things))))))

