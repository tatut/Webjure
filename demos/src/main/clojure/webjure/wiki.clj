(ns webjure.wiki
  (:refer-clojure)
  (:use webjure)
  (:use webjure.html))

(def *pages* (ref {}))

(defn update-page [page-name title body]
  (dosync
   (alter *pages*
	  #(assoc % page-name {:title title :body body}))))

(defn get-page [page-name]
  (@*pages* page-name))

(defn html-page [title & body]
  `(:html
    (:head (:title ~title))
    (:body ~@body)))


(defh #"/wiki/([^/]+)$" [page-name 1]
  {:output :html}
  (let [page (get-page page-name)]
    (if (nil? page)
      (html-page (str "Page does not exist: " page-name)
		 `(:div
		   (:h3 "Page \"" ~page-name "\" does not exist!")
		   (:a {:href ~(url (str "/wiki/" page-name "/edit"))}
		       "Create it")))		 
      
      (html-page (:title page)
		 `(:h3 ~(:title page))
		 `(:a {:href ~(url (str "/wiki/" page-name "/edit"))
		       :style "float: right;"} "edit this page")
		 `(:div ~#(:body page))))))


       
(defh #"/wiki/(.+)/edit$" [page-name 1]
  {:output :html}
  (let [page (get-page page-name)]
    (html-page (if (nil? page)
		 (str "Create page: " page-name)
		 (str "Edit page: " (:title page)))

	       `(:form 
		 {:action ~(url (str "/wiki/" page-name "/save"))
		  :method "POST"}

		 "Title: " (:input {:type "text" :name "title"
				    :value ~(or (:title page) page-name)})
		 (:br)

		 "Body: " (:br)
		 (:textarea {:name "body" :rows "20" :cols "70"}
			    ~(or (:body page) ""))
		 (:br)
		 (:input {:type "submit" :value "Save"})))))

(defn redirect [url]
  (.sendRedirect (.getActualResponse *response*) url))

(defh #"/wiki/(.+)/save$" [page-name 1
			   title "title"
			   body "body"]  
  {}
  (update-page page-name title body)
  (redirect (url (str "/wiki/" page-name))))