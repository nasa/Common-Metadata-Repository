(ns cmr.search.results-handlers.reference-results-handler
  "Handles the XML reference format."
  (:require
   [clojure.data.xml :as xml]
   [cmr.common-app.services.search :as qs]
   [cmr.common.concepts :as concepts]
   [cmr.elastic-utils.search.es-index :as elastic-search-index]
   [cmr.elastic-utils.search.es-results-to-query-results :as elastic-results]
   [cmr.search.models.query :as query]
   [cmr.search.services.query-execution.facets.facets-results-feature :as frf]
   [cmr.search.services.query-execution.granule-counts-results-feature :as gcrf]
   [cmr.search.services.url-helper :as url]))

(defmethod elastic-search-index/concept-type+result-format->fields [:granule :xml]
  [_concept-type _query]
  ["granule-ur"
   "provider-id"
   "concept-id"])

(defmethod elastic-search-index/concept-type+result-format->fields [:collection :xml]
  [_concept-type _query]
  ["entry-title"
   "provider-id"
   "short-name"
   "version-id"
   "concept-id"
   "deleted"
   "revision-id"
   "_score"])

(defmethod elastic-search-index/concept-type+result-format->fields [:variable :xml]
  [_concept-type _query]
  ["variable-name"
   "provider-id"
   "concept-id"
   "deleted"
   "revision-id"
   "_score"])

(defmethod elastic-search-index/concept-type+result-format->fields [:service :xml]
  [_concept-type _query]
  ["service-name"
   "provider-id"
   "concept-id"
   "deleted"
   "revision-id"
   "_score"])

(defmethod elastic-search-index/concept-type+result-format->fields [:tool :xml]
  [_concept-type _query]
  ["tool-name"
   "provider-id"
   "concept-id"
   "deleted"
   "revision-id"
   "_score"])

(defmethod elastic-search-index/concept-type+result-format->fields [:subscription :xml]
  [_concept-type _query]
  ["subscription-name"
   "provider-id"
   "concept-id"
   "deleted"
   "revision-id"
   "_score"])

(doseq [concept-type-key (concepts/get-generic-concept-types-array)]
  (defmethod elastic-search-index/concept-type+result-format->fields [concept-type-key :xml]
  [_concept-type _query]
  ["name"
   "id"
   "provider-id"
   "concept-id"
   "deleted"
   "revision-id"
   "_score"]))

;; TODO: Generic work: Should use a configuration file here?  Or is just "name" OK to have in code?
;; Then we need a how to create a new concept for search wiki page or read me or something.
(def generic-concepts->name-key
  (zipmap (concepts/get-generic-concept-types-array)
          (repeat (count (concepts/get-generic-concept-types-array)) :name)))

(def concept-type->name-key
  "A map of the concept type to the key to use to extract the reference name field."
  (merge
   {:collection :entry-title
    :granule :granule-ur
    :variable :variable-name
    :service :service-name
    :tool :tool-name
    :subscription :subscription-name}
   generic-concepts->name-key))

(defn- elastic-result->query-result-item
  [context query elastic-result]
  (let [concept-type (:concept-type query)
        name-key (concept-type->name-key concept-type)
        {score :_score
         {name-value name-key concept-id :concept-id deleted :deleted} :_source} elastic-result
        revision-id (elastic-results/get-revision-id-from-elastic-result concept-type elastic-result)]
    {:concept-id concept-id
     :revision-id revision-id
     :deleted deleted
     :location (format "%s%s/%s" (url/reference-root context) concept-id revision-id)
     :name name-value
     :score (query/normalize-score score)}))

(doseq [concept-type (into [] (concat [:collection :granule :variable :service :tool :subscription] (concepts/get-generic-concept-types-array)))]
  (defmethod elastic-results/elastic-result->query-result-item [concept-type :xml]
    [context query elastic-result]
    (elastic-result->query-result-item context query elastic-result)))

(defmethod gcrf/query-results->concept-ids :xml
  [results]
  (->> results
       :items
       (map :concept-id)))

(defmulti reference->xml-element
  "Converts a search result reference into an XML element."
  (fn [echo-compatible? results reference]
    echo-compatible?))

(defmulti results->xml-element
  "Converts the results into an XML element"
  (fn [echo-compatible? include-facets? results]
    echo-compatible?))

;; Normal CMR Search response implementations

(defmethod reference->xml-element false
  [_ results reference]
  (let [{:keys [has-granules-map granule-counts-map]} results
        {:keys [concept-id revision-id location name score deleted]} reference
        granule-count (get granule-counts-map concept-id 0)]
    (xml/element :reference {}
               (xml/element :name {} name)
               (xml/element :id {} concept-id)
               (if deleted
                 (xml/element :deleted {} "true")
                 (xml/element :location {} location))
               (xml/element :revision-id {} (str revision-id))
               (when has-granules-map
                 (xml/element :has-granules {} (or (< 0 granule-count)
                                                 (get has-granules-map concept-id false))))
               (when granule-counts-map
                 (xml/element :granule-count {} granule-count))
               (when score (xml/element :score {} score)))))

(defmethod results->xml-element false
  [_ _include-facets? results]
  (let [{:keys [hits took items facets]} results]
    (xml/element :results {}
               (xml/element :hits {} (str hits))
               (xml/element :took {} (str took))
               (xml/->Element :references {}
                            (map (partial reference->xml-element false results) items))
               (frf/facets->xml-element facets))))

;; TODO: Generic work: I used this because I got a null results back. That is because
;; the generic document isn't being stored correctly.  It is missing cmr elements. Will be working
;; on this next, so I may then be able to delete this.
(defmethod results->xml-element :default
  [_ _include-facets? results]
  (let [{:keys [hits took items facets]} results]
    (xml/element :results {}
               (xml/element :hits {} (str hits))
               (xml/element :took {} (str took))
               (xml/->Element :references {}
                            (map (partial reference->xml-element false results) items))
               (frf/facets->xml-element facets))))

;; ECHO Compatible implementations

(defmethod reference->xml-element true
  [_ _results reference]
  (let [{:keys [concept-id location name score]} reference]
    (xml/element :reference {}
               (xml/element :name {} name)
               (xml/element :id {} concept-id)
               (xml/element :location {} location)
               (when score (xml/element :score {} score)))))

(defmethod results->xml-element true
  [_ include-facets? results]
  (if include-facets?
    ;; Both echo-compatible and include-facets are true,
    ;; We generate response in catalog-rest search-facet format.
    ;; Only facets are returned, not query results
    (frf/facets->echo-xml-element (:facets results))
    (xml/->Element :references {"type" "array"}
                 (map (partial reference->xml-element true results) (:items results)))))

(defn- search-results->response
  [_context query results]
  (let [{:keys [echo-compatible? result-features]} query
        include-facets? (boolean (some #{:facets} result-features))]
    (xml/emit-str (results->xml-element echo-compatible? include-facets? results))))

(doseq [concept-type (into [] (concat [:collection :granule :variable :service :tool :subscription] (concepts/get-generic-concept-types-array)))]
  (defmethod qs/search-results->response [concept-type :xml]
    [context query results]
    (search-results->response context query results)))
