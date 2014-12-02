(ns cmr.ingest.services.ingest
  (:require [clj-time.core :as t]
            [cmr.common.time-keeper :as tk]
            [cmr.oracle.connection :as conn]
            [cmr.transmit.metadata-db :as mdb]
            [cmr.transmit.echo.rest :as rest]
            [cmr.ingest.data.indexer :as indexer]
            [cmr.ingest.data.provider-acl-hash :as pah]
            [cmr.ingest.services.messages :as msg]
            [cmr.ingest.services.validation :as v]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.services.errors :as serv-errors]
            [cmr.common.services.messages :as cmsg]
            [cmr.common.date-time-parser :as p]
            [cmr.common.util :as util]
            [cmr.umm.core :as umm]
            [clojure.string :as string]
            [cmr.system-trace.core :refer [deftracefn]]))

(defmulti add-extra-fields
  "Parse the metadata of concept, add the extra fields to it and return the concept."
  (fn [context concept]
    (:concept-type concept)))

(defmethod add-extra-fields :collection
  [context concept]
  (let [collection (umm/parse-concept concept)
        {{:keys [short-name version-id]} :product
         {:keys [delete-time]} :data-provider-timestamps
         entry-title :entry-title} collection]
    (assoc concept :extra-fields {:entry-title entry-title
                                  :short-name short-name
                                  :version-id version-id
                                  :delete-time (when delete-time (str delete-time))})))

(defn- get-granule-parent-collection-id
  "Find the parent collection id for a granule given its provider id and collection ref. This will
  correctly handle situations where there might be multiple concept ids that used a short name and
  version id or entry title but were previously deleted."
  [context provider-id collection-ref]
  (let [params (util/remove-nil-keys (merge {:provider-id provider-id}
                                            collection-ref))
        coll-concepts (mdb/find-collections context params)
        ;; Find the latest version of the concepts that aren't deleted. There should be only one
        matching-concepts (->> coll-concepts
                               (group-by :concept-id)
                               (map (fn [[concept-id concepts]]
                                      (->> concepts (sort-by :revision-id) reverse first)))
                               (filter (complement :deleted)))]
    (when (> (count matching-concepts) 1)
      (serv-errors/internal-error!
        (format (str "Found multiple possible parent collections for a granule in provider %s"
                     " referencing with %s. matching-concepts: %s")
                provider-id (pr-str collection-ref) (pr-str matching-concepts))))
    (:concept-id (first matching-concepts))))

(defmethod add-extra-fields :granule
  [context concept]
  (let [granule (umm/parse-concept concept)
        {:keys [collection-ref granule-ur]
         {:keys [delete-time]} :data-provider-timestamps} granule
        parent-collection-id (get-granule-parent-collection-id
                               context (:provider-id concept) collection-ref)]
    (when-not parent-collection-id
      (cmsg/data-error :not-found
                       msg/parent-collection-does-not-exist
                       granule-ur
                       collection-ref))
    (assoc concept :extra-fields {:parent-collection-id parent-collection-id
                                  :delete-time (when delete-time (str delete-time))})))

(deftracefn save-concept
  "Store a concept in mdb and indexer and return concept-id and revision-id."
  [context concept]
  (v/validate concept)
  (let [concept (add-extra-fields context concept)
        time-to-compare (t/plus (tk/now) (t/minutes 1))
        delete-time (get-in concept [:extra-fields :delete-time])
        delete-time (if delete-time (p/parse-datetime delete-time) nil)]
    (if (and delete-time (t/after? time-to-compare delete-time))
      (serv-errors/throw-service-error
        :bad-request
        (format "DeleteTime %s is before the current time." (str delete-time)))
      (let [{:keys [concept-id revision-id]} (mdb/save-concept context concept)]
        (indexer/index-concept context concept-id revision-id)
        {:concept-id concept-id, :revision-id revision-id}))))

(deftracefn delete-concept
  "Delete a concept from mdb and indexer."
  [context concept-attribs]
  (let [{:keys [concept-type provider-id native-id]}  concept-attribs
        concept-id (mdb/get-concept-id context concept-type provider-id native-id)
        revision-id (mdb/delete-concept context concept-id)]
    (indexer/delete-concept-from-index context concept-id revision-id)
    {:concept-id concept-id, :revision-id revision-id}))

(deftracefn health
  "Returns the health state of the app."
  [context]
  (let [db-health (conn/health (pah/context->db context))
        echo-rest-health (rest/health context)
        metadata-db-health (mdb/get-metadata-db-health context)
        indexer-health (indexer/get-indexer-health context)
        ok? (every? :ok? [db-health echo-rest-health metadata-db-health indexer-health])]
    {:ok? ok?
     :dependencies {:oracle db-health
                    :echo echo-rest-health
                    :metadata-db metadata-db-health
                    :indexer indexer-health}}))
