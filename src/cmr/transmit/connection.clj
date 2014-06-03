(ns cmr.transmit.connection
  "Contains functions that allow creating application connections. We'll eventually use this for
  implementing CMR-538"
  (:require [camel-snake-kebab :as csk]))

(defn create-app-connection
  "Creates a 'connection' to an application"
  [host port]
  {:host host
   :port port})

