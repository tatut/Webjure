
(ns webjure.csv.test
  (:refer-clojure)
  (:use clojure.test)
  (:use webjure.csv)
  (:import (java.util Date)))

(defn csv [data]
  (with-out-str 
    (csv-format *out* data)))

(deftest simple-output-without-headers
  (let [result (csv {:rows [["foo" "bar"]]})]
    (is (= result "foo;bar\n"))))

(deftest simple-output-with-headers
  (let [result (csv 
		{:headers [{:label "Name"}
			   {:label "Shoe size"
			    :format #(if (nil? %) "shoe size unknown" (str %))}]
		 :rows [["Tatu" 45]
			["Rolf" 38]
			["Tutte" nil]]})
	lines (seq (.split result "\n"))]
    ;; we should have four lines, 1 header + 3 rows
    (is (= (count lines) 4))

    ;; first line should be headers
    (is (= (first lines) "Name;Shoe size"))


    ;; Rolf has tiny feet
    (is (= (nth lines 2) "Rolf;38"))

    ;; formatter for the 2nd column of the last line should output 
    ;; "shoe size unknown"
    (is (= (last lines) "Tutte;shoe size unknown"))
))

(deftest output-user-objects-with-separator
  (let [result (csv {:rows [{:name "Rolf Teflon" :birthday (Date. 81 3 8)}
			    {:name "Tutte" :birthday (Date. 99 11 2)}
			    {:name "Foo Barsky" :birthday (Date. 70 0 1)}]
		     :headers [{:label "Name" :accessor :name} 
			       {:label "Year born"
				:accessor #(+ 1900 (.getYear (:birthday %)))}]
		     :separator \|})
	lines (seq (.split result "\n"))]
    
    (is (= (count lines) 4))

    (is (= (nth lines 2) "Tutte|1999"))))
