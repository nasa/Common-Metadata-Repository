(ns cmr.ingest.services.additional-attribute-validation
  "Provides functions to validate the additional attributes during collection update"
  (:require [cmr.common.util :as util]
            [cmr.umm.core :as umm]
            [cmr.umm.collection.product-specific-attribute :as psa]
            [cmr.transmit.metadata-db :as mdb]
            [cmr.transmit.search :as search]
            [cmr.ingest.services.helper :as h]))

(defn- needs-to-check-aa-range?
  [aa prev-aa]
  (let [{aa-begin :parsed-parameter-range-begin aa-end :parsed-parameter-range-end} aa
        {prev-aa-begin :parsed-parameter-range-begin prev-aa-end :parsed-parameter-range-end} prev-aa]
    (or (and aa-begin (util/greater-than? aa-begin prev-aa-begin))
        (and aa-end prev-aa-end (util/less-than? aa-end prev-aa-end))
        (and aa-end (nil? prev-aa-end)))))

(defn- range-search-params
  "Returns granule searche in terms of search params and error message for additional attribute range change"
  [aa]
  (let [{aa-name :name aa-type :data-type aa-begin :parameter-range-begin
         aa-end :parameter-range-end} aa
        type (name aa-type)
        params (concat (when aa-begin
                         [(format "%s,%s,,%s" type aa-name aa-begin)])
                       (when aa-end
                         [(format "%s,%s,%s," type aa-name aa-end)]))]
    {:params {"attribute[]" params
              "options[attribute][or]" true}
     :error-msg (format "Collection additional attribute [%s] cannot be changed since there are existing granules outside of the new value range."
                        aa-name)}))

(defn- build-aa-deleted-searches
  "Returns granule searches in terms of search params and error messages for deleted additional attributes"
  [aas]
  (map #(hash-map :params {"attribute[]" [(:name %)]}
                  :error-msg (format "Collection additional attribute [%s] is referenced by existing granules, cannot be removed."
                                     (:name %)))
       aas))

(defn- build-aa-type-range-searches
  "Add granule searches for additional attributes with type and range changes to the given list of searches
  and returns it."
  [searches existing-aas prev-aas]
  (let [prev-aas-map (group-by :name prev-aas)
        build-fn (fn [searches aa]
                   (let [{aa-name :name aa-type :data-type} aa
                         prev-aa (first (get prev-aas-map aa-name))
                         {prev-aa-type :data-type} prev-aa]
                     (if (= aa-type prev-aa-type)
                       (if (needs-to-check-aa-range? aa prev-aa)
                         (conj searches (range-search-params aa))
                         searches)
                       (conj searches
                             {:params {"attribute[]" [aa-name]}
                              :error-msg (format "Collection additional attribute [%s] was of DataType [%s], cannot be changed to [%s]."
                                                 aa-name (psa/gen-data-type prev-aa-type) (psa/gen-data-type aa-type))}))))]
    (reduce build-fn searches existing-aas)))

(defn- build-aa-granule-searches
  "Returns a list of granule searches for additional attributes validation. Granule search is a map
  with keys of :params and :error-msg."
  [aas prev-aas]
  (let [aa-names (map :name aas)
        deleted-aas (filter #(not (some #{(:name %)} aa-names)) prev-aas)
        deleted-aa-searches (build-aa-deleted-searches deleted-aas)
        pre-aa-names (map :name prev-aas)
        existing-aas (filter #(some #{(:name %)} pre-aa-names) aas)]
    (build-aa-type-range-searches deleted-aa-searches existing-aas prev-aas)))

(defn- append-common-params
  "Returns the has-granule search by appending the common search params to the params value of the given search"
  [collection-concept-id search-map]
  (update-in search-map [:params] assoc
             :collection-concept-id collection-concept-id
             "options[attribute][exclude_collection]" true
             "options[attribute][exclude_boundary]" true
             :page-size 1
             :page-num 1))

(defn- has-granule?
  "Execute the given has-granule search, returns the error message if there are granules found by the search."
  [context search-map]
  (let [{:keys [params error-msg]} search-map
        gran-count (-> (search/find-granules context params)
                       (get-in [:feed :entry])
                       count)]
    (when (> gran-count 0)
      error-msg)))

(defn additional-attributes-validation
  "Validates collection additional attributes update do not invalidate any existing granules"
  [context concept]
  (let [{:keys [provider-id extra-fields additional-attributes]} concept
        {:keys [entry-title]} extra-fields
        prev-concept (first (h/find-visible-collections context {:provider-id provider-id
                                                                 :entry-title entry-title}))]
    (when prev-concept
      (let [has-granule-searches (->> (umm/parse-concept prev-concept)
                                      :product-specific-attributes
                                      (build-aa-granule-searches additional-attributes)
                                      (map (partial append-common-params (:concept-id prev-concept))))
            search-error (some #(has-granule? context %) has-granule-searches)]
        (when search-error
          [search-error])))))



