(ns cmr.indexer.data.concepts.service
  "Contains functions to parse and convert service and service association concepts."
  (:require
   [cmr.common.log :refer (debug info warn error)]
   [cmr.indexer.data.concept-parser :as concept-parser]
   [cmr.transmit.metadata-db :as mdb]))

(defn- service-association->service-concept
  "Returns the service concept and service association for the given service association."
  [context service-association]
  (let [{:keys [service-concept-id]} service-association
        service-concept (mdb/find-latest-concept
                         context
                         {:concept-id service-concept-id}
                         :service)]
    (when-not (:deleted service-concept)
      service-concept)))

(defn- has-formats?
  "Returns true if the given service has more than one SupportedFormats value."
  [context service-concept]
  (let [service (concept-parser/parse-concept context service-concept)
        supported-formats (get-in service [:ServiceOptions :SupportedFormats])]
    (> (count supported-formats) 1)))

(defn service-associations->elastic-doc
  "Converts the service association into the portion going in the collection elastic document."
  [context service-associations]
  (let [service-concepts (remove nil?
                                 (map #(service-association->service-concept context %)
                                      service-associations))]
    {:has-formats (boolean (some #(has-formats? context %) service-concepts))}))
