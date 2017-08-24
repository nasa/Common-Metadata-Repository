(ns cmr.client.dev.browser-setup
  (:require
   [clojure.browser.repl :as repl]
   [clojure.pprint :refer [print-table]]
   [clojure.reflect :refer [reflect]]
   [clojure.string :as string]
   [cmr.client.core :as client]))

(repl/connect "http://localhost:9000/repl")

(enable-console-print!)

(println "Hello world!")
