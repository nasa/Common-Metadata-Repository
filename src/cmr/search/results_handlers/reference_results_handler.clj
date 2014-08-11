(ns cmr.search.results-handlers.reference-results-handler
  "Handles the XML reference format."
  (:require [cmr.search.data.elastic-results-to-query-results :as elastic-results]
            [cmr.search.data.elastic-search-index :as elastic-search-index]
            [cmr.search.services.query-service :as qs]
            [cmr.search.services.url-helper :as url]
            [clojure.data.xml :as x]
            [clojure.set :as set]
            [cheshire.core :as json]))

(defmethod elastic-search-index/concept-type+result-format->fields [:granule :xml]
  [concept-type result-format]
  ["granule-ur"
   "provider-id"])

(defmethod elastic-search-index/concept-type+result-format->fields [:collection :xml]
  [concept-type result-format]
  ["entry-title"
   "provider-id"
   "short-name"
   "version-id"
   "_score"])

(def concept-type->name-key
  "A map of the concept type to the key to use to extract the reference name field."
  {:collection :entry-title
   :granule :granule-ur})

(defmethod elastic-results/elastic-result->query-result-item :xml
  [context query elastic-result]
  (let [name-key (concept-type->name-key (:concept-type query))
        {concept-id :_id
         revision-id :_version
         score :_score
         {[name-value] name-key} :fields} elastic-result]
    {:concept-id concept-id
     :revision-id revision-id
     :location (format "%s%s" (url/reference-root context) concept-id)
     :name name-value
     :score score}))

(defn- reference->xml-element
  "Converts a search result reference into an XML element"
  [reference]
  (let [{:keys [concept-id revision-id location name score]} reference]
    (x/element :reference {}
               (x/element :name {} name)
               (x/element :id {} concept-id)
               (x/element :location {} location)
               (x/element :revision-id {} (str revision-id))
               (x/element :score {} score))))

(defmethod qs/search-results->response :xml
  [context query results]
  (let [{:keys [hits took items]} results
        {:keys [pretty?]} query
        xml-fn (if pretty? x/indent-str x/emit-str)]
    (xml-fn
      (x/element :results {}
                 (x/element :hits {} (str hits))
                 (x/element :took {} (str took))
                 (x/->Element :references {}
                              (map reference->xml-element items))))))





