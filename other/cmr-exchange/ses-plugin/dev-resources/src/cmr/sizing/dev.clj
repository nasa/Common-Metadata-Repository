(ns cmr.sizing.dev
  "CMR OPeNDAP development namespace."
  (:require
   [clojure.data.xml :as xml]
   [clojure.java.classpath :as classpath]
   [clojure.java.io :as io]
   [clojure.pprint :refer [pprint]]
   [clojure.set :as set]
   [clojure.string :as string]
   [clojure.tools.namespace.repl :as repl]
   [clojusc.twig :as logger]
   [trifl.java :refer [show-methods]])
  (:import
   (java.net URI)
   (java.nio.file Paths)))

