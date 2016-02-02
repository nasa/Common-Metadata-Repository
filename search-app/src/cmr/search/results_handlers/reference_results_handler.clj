(ns cmr.search.results-handlers.reference-results-handler
  "Handles the XML reference format."
  (:require [cmr.common-app.services.search.elastic-results-to-query-results :as elastic-results]
            [cmr.common-app.services.search.elastic-search-index :as elastic-search-index]
            [cmr.common-app.services.search :as qs]
            [cmr.search.services.url-helper :as url]
            [clojure.data.xml :as x]
            [clojure.set :as set]
            [cheshire.core :as json]
            [cmr.search.models.query :as q]
            [cmr.search.services.query-execution.granule-counts-results-feature :as gcrf]
            [cmr.search.services.query-execution.facets-results-feature :as frf]))

(defmethod elastic-search-index/concept-type+result-format->fields [:granule :xml]
  [concept-type query]
  ["granule-ur"
   "provider-id"
   "concept-id"])

(defmethod elastic-search-index/concept-type+result-format->fields [:collection :xml]
  [concept-type query]
  ["entry-title"
   "provider-id"
   "short-name"
   "version-id"
   "concept-id"
   "deleted"
   "_score"])

(def concept-type->name-key
  "A map of the concept type to the key to use to extract the reference name field."
  {:collection :entry-title
   :granule :granule-ur})

(defn- elastic-result->query-result-item
  [context query elastic-result]
  (let [name-key (concept-type->name-key (:concept-type query))
        {revision-id :_version
         score :_score
         {[name-value] name-key [concept-id] :concept-id [deleted] :deleted} :fields} elastic-result]
    {:concept-id concept-id
     :revision-id revision-id
     :deleted deleted
     :location (format "%s%s/%s" (url/reference-root context) concept-id revision-id)
     :name name-value
     :score (q/normalize-score score)}))

(doseq [concept-type [:collection :granule]]
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
    (x/element :reference {}
               (x/element :name {} name)
               (x/element :id {} concept-id)
               (if deleted
                 (x/element :deleted {} "true")
                 (x/element :location {} location))
               (x/element :revision-id {} (str revision-id))
               (when has-granules-map
                 (x/element :has-granules {} (or (< 0 granule-count)
                                                 (get has-granules-map concept-id false))))
               (when granule-counts-map
                 (x/element :granule-count {} granule-count))
               (when score (x/element :score {} score)))))

(defmethod results->xml-element false
  [_ include-facets? results]
  (let [{:keys [hits took items facets]} results]
    (x/element :results {}
               (x/element :hits {} (str hits))
               (x/element :took {} (str took))
               (x/->Element :references {}
                            (map (partial reference->xml-element false results) items))
               (frf/facets->xml-element facets))))

;; ECHO Compatible implementations

(defmethod reference->xml-element true
  [_ results reference]
  (let [{:keys [concept-id location name score]} reference]
    (x/element :reference {}
               (x/element :name {} name)
               (x/element :id {} concept-id)
               (x/element :location {} location)
               (when score (x/element :score {} score)))))

(defmethod results->xml-element true
  [_ include-facets? results]
  (if include-facets?
    ;; Both echo-compatible and include-facets are true,
    ;; We generate response in catalog-rest search-facet format.
    ;; Only facets are returned, not query results
    (frf/facets->echo-xml-element (:facets results))
    (x/->Element :references {"type" "array"}
                 (map (partial reference->xml-element true results) (:items results)))))

(defn- search-results->response
  [context query results]
  (let [{:keys [echo-compatible? result-features]} query
        include-facets? (boolean (some #{:facets} result-features))]
    (x/emit-str (results->xml-element echo-compatible? include-facets? results))))

(doseq [concept-type [:collection :granule]]
  (defmethod qs/search-results->response [concept-type :xml]
    [context query results]
    (search-results->response context query results)))
