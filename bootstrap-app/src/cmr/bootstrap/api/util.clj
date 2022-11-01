(ns cmr.bootstrap.api.util
  "Utility functions for the bootstrap API."
  (:require
   [cmr.common.concepts :as concepts]
   [clojure.string :as string]
   [cmr.bootstrap.services.bootstrap-service :as service]))

(defn synchronous?
  "Returns true if the params contains the :synchronous key and it's value
  converted to lower case equals the string \"true\"."
  [params]
  (= "true"
     (when (:synchronous params)
       (string/lower-case (:synchronous params)))))

(defn- get-dispatcher-type
  "Given params and a request-type, return the dispatcher type. Returned value
  will be a keyword."
  [params request-type]
  (if (synchronous? params)
    :synchronous-dispatcher
    (get service/request-type->dispatcher request-type :core-async-dispatcher)))

(defn get-dispatcher
  "Returns the correct dispatcher to use based on the system configuration and
  the request."
  ([context request-type]
   (get-dispatcher context {} request-type))
  ([context params request-type]
   (get-in context [:system (get-dispatcher-type params request-type)])))
