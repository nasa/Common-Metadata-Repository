(ns cmr.search.services.query-execution.tags-results-feature
  "This enables the :include-tags feature for collection search results. When it is enabled
  collection search results will include the list of tags that are associated with the collection."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [cmr.common.util :as util]
            [cmr.search.services.query-execution :as query-execution]
            [cmr.search.data.elastic-search-index :as idx]
            [cmr.search.models.query :as qm]
            [cmr.search.models.group-query-conditions :as gc]))

(defn- tag->unzipped-tag
  "Returns the unzipped tag by gunzip the :associated-concept-ids-gzip-b64 field and replace it
  with associated-concept-ids."
  [tag]
  (let [{:keys [namespace value associated-concept-ids-gzip-b64]} tag]
    {:namespace namespace
     :value value
     :associated-concept-ids (some-> associated-concept-ids-gzip-b64
                                     util/gzip-base64->string
                                     edn/read-string)}))

(defn- coll-with-tags
  "Returns the collection with tags associated with it as a list of tag namespace and value tuples."
  [tags coll]
  (if-let [matching-tags (seq (filter
                                #(.contains (set (:associated-concept-ids %)) (:id coll))
                                tags))]
    (assoc coll :tags (mapv #(vector (:namespace %) (:value %)) matching-tags))
    coll))

(defn- tag-query
  "Returns the query for searching tags with the given include-tags parameter value and list of
  collection concept-ids."
  [tags-value coll-concept-ids]
  (let [tag-namespaces (map str/trim (str/split tags-value #","))]
    (qm/query
      {:concept-type :tag
       :condition (gc/and-conds
                    [(qm/string-conditions :namespace tag-namespaces false true :or)
                     (qm/string-conditions :associated-concept-ids coll-concept-ids true)])
       :skip-acls? true
       :page-size :unlimited
       :result-fields [:namespace :value :associated-concept-ids-gzip-b64]})))

(defmethod query-execution/post-process-query-result-feature :tags
  [context query elastic-results query-results feature]
  (let [coll-concept-ids (seq (keep :id (:items query-results)))
        tags (some->> coll-concept-ids
                      (tag-query (get-in query [:result-options :tags]))
                      (idx/get-tags context)
                      (map tag->unzipped-tag))]
    (util/update-in-each query-results [:items] (partial coll-with-tags tags))))
