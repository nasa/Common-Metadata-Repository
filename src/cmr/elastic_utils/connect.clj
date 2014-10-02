(ns cmr.elastic-utils.connect
  "Provide functions to invoke elasticsearch"
  (:require [clojurewerkz.elastisch.rest :as esr]
            [clojurewerkz.elastisch.rest.admin :as admin]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]))

(defn- connect-with-config
  "Connects to ES with the given config"
  [config]
  (let [{:keys [host port]} config]
    (info (format "Connecting to single ES on %s %d" host port))
    (esr/connect (str "http://" host ":" port))))

(defn try-connect
  [config]
  (try
    (connect-with-config config)
    (catch Exception e
      (errors/internal-error!
        (format "Unable to connect to elasticsearch at: %s. with %s" config e)))))

(defn health
  "Returns the health state of elasticsearch."
  [context elastic-key-in-context]
  (let [conn (get-in context [:system elastic-key-in-context :conn])
        health-detail (admin/cluster-health conn :wait_for_status "yellow" :timeout "30s")
        status (:status health-detail)]
    (if (some #{status} ["green" "yellow"])
      {:ok? true}
      {:ok? false
       :problem health-detail})))
