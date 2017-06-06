(ns cmr.ingest.validation.additional-attribute-validation
  "Provides functions to validate the additional attributes during collection update"
  (:require
   [clojure.string :as str]
   [cmr.common.util :as util]
   [cmr.umm-spec.additional-attribute :as aa]))

(defn- lower-case-name
  "For a given addition attribute, return lower-case name"
  [aa]
  (util/safe-lowercase (:Name aa)))

(defn- aa-range-reduced?
  "Returns true if the range of additional attribute is smaller than the range of the previous one."
  [aa prev-aa]
  (let [{aa-begin ::aa/parsed-parameter-range-begin aa-end ::aa/parsed-parameter-range-end}
        (aa/attribute-with-parsed-value aa)
        {prev-aa-begin ::aa/parsed-parameter-range-begin prev-aa-end ::aa/parsed-parameter-range-end}
        (aa/attribute-with-parsed-value prev-aa)]
    (boolean (or (and aa-begin (util/greater-than? aa-begin prev-aa-begin))
                 (and aa-end prev-aa-end (util/less-than? aa-end prev-aa-end))
                 (and aa-end (nil? prev-aa-end))))))

(defn- out-of-range-searches
  "Returns granule searches for finding granules that are outside of the additional attribute range.
  If there are any existing granules outside of the additional attribute range,
  then this additional attribute would be invalid."
  [aa]
  (let [{aa-name :Name aa-type :DataType aa-begin :ParameterRangeBegin
         aa-end :ParameterRangeEnd} aa
        type (str/lower-case aa-type)
        params (concat (when aa-begin
                         [(format "%s,%s,,%s" type aa-name aa-begin)])
                       (when aa-end
                         [(format "%s,%s,%s," type aa-name aa-end)]))]
    {:params {"attribute[]" params
              "options[attribute][or]" true}
     :error-msg (format "Collection additional attribute [%s] cannot be changed since there are existing granules outside of the new value range."
                        aa-name)}))

(defn- build-aa-deleted-searches
  "Returns granule searches for deleted additional attributes. We should not delete any additional
  attributes that are still referenced by existing granules. This function build the search parameters
  for identifying such invalid deletions."
  [aas prev-aas]
  (let [aa-names (set (map lower-case-name aas))
        deleted-aas (remove #(aa-names (lower-case-name %)) prev-aas)]
    (map #(hash-map :params {"attribute[]" [(:Name %)]}
                    :error-msg (format "Collection additional attribute [%s] is referenced by existing granules, cannot be removed."
                                       (:Name %)))
         deleted-aas)))

(defn- single-aa-type-range-search
  "Returns the granule search for the given additional attribute and its previous version"
  [[aa prev-aa]]
  (let [{aa-name :Name aa-type :DataType} aa
        {prev-aa-type :DataType} prev-aa]
    (if (= aa-type prev-aa-type)
      ;; type does not change, check for range change
      (when (aa-range-reduced? aa prev-aa)
        (out-of-range-searches aa))
      ;; additional attribute type is changed, we need to search that no granules reference this additional attribute
      {:params {"attribute[]" [aa-name]}
       :error-msg (format "Collection additional attribute [%s] was of DataType [%s], cannot be changed to [%s]."
                          aa-name (aa/gen-data-type prev-aa-type) (aa/gen-data-type aa-type))})))

(defn- build-aa-type-range-searches
  "Add granule searches for finding invalid additional attribute type and range changes to the given
  list of searches and returns it. It is done by going through the additional attributes that exist
  in the previous version of the collection and construct searches for those that either have type
  changed or range changed to a reduced range that needs to verify that no granules will become
  invalidated due to the changes."
  [aas prev-aas]
  (let [prev-aas-map (group-by lower-case-name prev-aas)
        link-fn (fn [aa]
                  (when-let [prev-aa (first (prev-aas-map (lower-case-name aa)))]
                    [aa prev-aa]))]
    (->> (map link-fn aas)
         (map single-aa-type-range-search)
         (remove nil?))))

(defn- append-common-params
  "Returns the has-granule search by appending the common search params to the given search"
  [collection-concept-id search-map]
  (update-in search-map [:params] assoc
             :collection-concept-id collection-concept-id
             "options[attribute][exclude_collection]" true
             "options[attribute][exclude_boundary]" true))

(defn additional-attribute-searches
  "Constructs and returns a list of granule searches for additional attributes validation during
  collection update. Granule search is a map with keys of :params and :error-msg.
  There are three invalid cases:
  - additional attribute is removed but has existing granules referencing it
  - additional attribute type changed but has existing granules referencing it
  - additional attribute range changed but has existing granules outside of the new range"
  [context concept-id concept prev-concept]
  (let [{aas :AdditionalAttributes} concept
        {prev-aas :AdditionalAttributes} prev-concept]
    (->> (concat (build-aa-deleted-searches aas prev-aas)
                 (build-aa-type-range-searches aas prev-aas))
         (map (partial append-common-params concept-id)))))
