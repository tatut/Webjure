(ns webjure.html.test
  (:refer-clojure)
  (:use clojure.test)
  (:use webjure.html))


(deftest simple-element
  (is (= (html-format `(:a {:href "foo"} "something"))
	 "<a href=\"foo\">something</a>")))

(deftest empty-element
  (is (= (html-format `(:br))
	 "<br />")))

(deftest escaped-text
  (is (= (html-format `(:div "< and > are escaped here"))
	 "<div>&lt; and &gt; are escaped here</div>")))

(deftest raw-text
  (is (= (html-format `(:div ~(fn [] "< and > are not escaped here")))
	 "<div>< and > are not escaped here</div>")))