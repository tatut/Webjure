(ns webjure.cpt.test
  (:refer-clojure)
  (:use clojure.test)
  (:use webjure.cpt))

(set! *warn-on-reflection* true)

;; Very simple template
(define-template simple1 "simple.cpt")
    
;; A template that loops over messages and includes
;; a subtemplate for each item
(define-template inbox "inbox.cpt")

(define-template repeat-binding "repeat-binding.cpt")

(defn t [template here]
  (with-out-str (template here)))

(deftest simple-template 
  (is (= (t simple1 [1 2 3]) "<foo>\n123\n</foo>")))
  

(deftest test-inbox
  (is (= (t inbox {:messages [ {:title "Welcome to CPT" :body "something or other..."}
			       {:title "Another message" :body "lorem ipsum etc"} ]})
	 "<html>\n  <head><title>Inbox</title></head>\n  <body>\n    <div>\n      <h3>Unnumbered list of messages</h3>\n      <span>\n\t\n\t<span class=\"message\">\n  Title: Welcome to CPT <br/>\n  Message: something or other... <br/>\n</span><span class=\"message\">\n  Title: Another message <br/>\n  Message: lorem ipsum etc <br/>\n</span>\n      </span>\n    </div>\n  </body>    \n</html>")))

(deftest test-repeat-binding
    (is (= (t repeat-binding [[1 "one"] [2 "two"]])
	   "<foo>\n<value id=\"1\">one</value><value id=\"2\">two</value>\n</foo>")))