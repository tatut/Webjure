
(defn parse-atom1-entry [entry-node]
  (parse entry-node {}
	 (<> "link" (collect-as :url (fn [lnk] (attribute lnk "href"))))
	 (<> "author"
	     (<> "email" (collect-as :email text))
	     (<> "name" (collect-as :author text)))))

