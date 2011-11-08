;;
;; Inline editor support implementations
;;


(ns webjure.sql.crud.editors
  (:refer-clojure)
  (:use webjure.sql)
  (:use webjure)
  (:use webjure.sql.crud))


;; Inline editor for simple short text input
;; Shows the text normally (with a hover edit icon). When the text is clicked
;; it is replaced with an input field containing the text.
;; Pressing enter in the input field will submit the replacement text.

(defn text-inline-edit []
  (reify InlineEdit
    (render-inline-edit-view [this field current-value]
      (let [s (gensym)
            post-url (crud-url "/inline-edit" {})]
        (str "<span class=\"editable\" onclick=\"event.stopPropagation(); $('#" s "').hide(); $('#" s "_edit').show(); if(typeof(init"s")=='undefined') { init"s" = 1; $('#" s "_input').keypress(function(e) { if(e.which==13) { $.post('" post-url  "', {value: $('#" s "_edit input').val()}); } }); } return false;\">"
             "<span class=\"current\" id=\"" s "\">" current-value "</span>"
             "<span style=\"display: none;\" id=\"" s "_edit\"><input id=\"" s "_input\" type=\"text\" value=\"" (.replace current-value "\"" "%22") "\" /></span>"
             
             "</span>"
             )))

    (update-value [this id field new-value]
      (println "Got new " field " value for row " id ": " new-value))))
    
  
