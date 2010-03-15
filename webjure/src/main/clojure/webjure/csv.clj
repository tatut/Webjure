
(ns webjure.csv
  (:refer-clojure))

(defn csv-format 
  "Format comma separated values (CSV) to the given output.
   The output must be a java.io.Writer.

   The CSV is a map containing the following keys:
   :rows             Sequence of rows to format, each row is a seq 
                     of values or a user object (*).
   :separator        Separator between values, defaults to \\; if omitted.
   :line-separator 
   :headers          Sequence of headers for the values, should have the
                     same amount of values as each row. May be omitted if
                     the values are simple seqs.
 
   A header is a map containing the following keys:
   :label            Optional label for the for column.
   :format           Function (1-arg) to format the values, defaults to 
                     clojure.core/str if omitted.
   :accessor         Function (1-arg) to get the column value from the row
                     object. Used only if the rows are user objects.
   
   A header row is formatted in the output if any of the given headers 
   contain a label.

   (*) If the rows are not seqs of values but user objects (a map, some Java 
       object, etc), the headers must be specified and each header must have
       the :accessor defined.
"
  [#^java.io.Writer output csv]
  (let [headers (or (:headers csv) [])
	separator (or (:separator csv) \;)
	output-header-row (not (nil? (some :label headers)))
	write-line (fn [values]
		     (.append output
			      (str (reduce str
					   (butlast (interleave values (repeat separator)))) "\n"))
		     ;; for debugging purposes
		     (.flush output))
	fmt (fn [column value]
	      ;; apply the column specific formatting function (or str)
	      (let [hdr (nth headers column {})]
		((or (:format hdr) str) value)))]

    ;; Output header row, if necessary 
    (if output-header-row
      (write-line (map :label headers)))

    ;; Output all the rows
    (doseq [row (:rows csv)]
      (write-line
       (if (or (vector? row) (seq? row))
	 ;; Row is a sequence or vector of values, format all values
	 (loop [acc []
		column 0 
		[value & values] (replace {nil ::csv-no-value} row)]
	   (if (nil? value)
	     acc
	     (recur (conj acc (fmt column (if (= ::csv-no-value value) nil value)))
		    (+ 1 column)
		    values)))

	 ;; Row is a user object, loop headers and use accessors to get values
	 (loop [acc []
		column 0
		[hdr & headers] headers]
	   (if (nil? hdr)
	     acc
	     (recur (conj acc (fmt column ((:accessor hdr) row)))
		    (+ 1 column)
		    headers))))
	 ))))

	