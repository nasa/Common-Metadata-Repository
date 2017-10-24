(ns cmr.transmit.variable
  "This contains functions for interacting with the variable API."
  (:require
   [cmr.transmit.connection :as conn]
   [cmr.transmit.http-helper :as h]))

(defn- variables-url
  [conn]
  (format "%s/variables" (conn/root-url conn)))

(h/defsearcher search-for-variables :search variables-url)
