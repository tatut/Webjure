
;; Working with XML-based feed formats

(ns webjure.xml.feeds
    (:refer-clojure)
    (:use webjure.xml))

(defn parse-atom1-link [link-node]
  (parse link-node
	 {}
	 (attr "href" (collect-as :url #(.getValue %)))
	 (attr "rel" (collect-as :rel #(.getValue %)))))

(defn parse-atom1-entry [entry-node]
  (parse entry-node 
	 {:links {}}
	 (<> "title" (collect-as :title text))
	 (<>* "link" (modify-key :links #(let [lnk (parse-atom1-link %2)]
					   (assoc %1 (lnk :rel) lnk))))

	 (<>? "author"
	      (<>? "email" (collect-as :email text))
	      (<>? "name" (collect-as :author text)))))

(defn load-atom1-feed [file-or-istream]
  (parse
   (. (load-dom file-or-istream) (getDocumentElement))
   {:entries []}
   (<> "title" (collect-as :title text))
   (<>? "subtitle" (collect-as :subtitle text))
   (<>* "entry" 
	(modify-key :entries #(conj %1 (parse-atom1-entry %2))))))


;; example modified from Wikipedia:
(def +atom-test-xml+
     "<?xml version=\"1.0\" encoding=\"utf-8\"?>
<feed xmlns=\"http://www.w3.org/2005/Atom\">
 
 <title>Example Feed</title>
 <subtitle>A subtitle.</subtitle>
 <link href=\"http://example.org/feed/\" rel=\"self\"/>
 <link href=\"http://example.org/\"/>
 <updated>2003-12-13T18:30:02Z</updated>
 <author>
   <name>John Doe</name>
   <email>johndoe@example.com</email>
 </author>
 <id>urn:uuid:60a76c80-d399-11d9-b91C-0003939e0af6</id>
 
 <entry>
   <title>Atom-Powered Robots Run Amok</title>
   <link href=\"http://example.org/2003/12/13/atom03\"/>
   <id>urn:uuid:1225c695-cfb8-4ebb-aaaa-80da344efa6a</id>
   <updated>2003-12-13T18:30:02Z</updated>
   <summary>Some text.</summary>
 </entry>

 <entry>
   <title>Webjure gains preliminary support form feeds</title>
   <link href=\"http://example.org/2008/11/18/story\"/>
   <id>urn:uuid:this-is-my-story-and-im-sticking-to-it-2008118tt</id>
   <summary>We can parse these.</summary>
 </entry>
</feed>")