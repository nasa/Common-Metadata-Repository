(ns cmr.exchange.geo.dev
  "CMR OPeNDAP development namespace."
  (:require
   [clojure.java.io :as io]
   [clojure.pprint :refer [pprint]]
   [clojure.string :as string]
   [clojure.tools.namespace.repl :as repl]
   [clojusc.twig :as logger]
   [trifl.java :refer [show-methods]])
  (:import
   (net.sf.geographiclib Geodesic PolygonArea)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Initial Setup & Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;TBD
