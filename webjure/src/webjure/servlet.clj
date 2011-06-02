(ns webjure.servlet
  (:refer-clojure)
  (:use webjure)
  (:import (javax.servlet ServletException UnavailableException))
  (:import (javax.servlet.http
	    HttpServlet HttpServletRequest HttpServletResponse))
  (:gen-class :name webjure.servlet.WebjureServlet
	      :extends javax.servlet.http.HttpServlet))


(defn -doGet [this ^HttpServletRequest request ^HttpServletResponse response]
  (dispatch "GET" request response))

(defn -doPost [this ^HttpServletRequest request ^HttpServletResponse response]
  (dispatch "POST" request response))

(defn -init [this]
  (require (.getInitParameter (.getServletConfig this) "startupNamespace")))


