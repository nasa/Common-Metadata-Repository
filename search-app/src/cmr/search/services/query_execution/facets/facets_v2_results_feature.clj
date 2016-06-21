(ns cmr.search.services.query-execution.facets.facets-v2-results-feature
  "Returns facets v2 along with collection search results. See
  https://wiki.earthdata.nasa.gov/display/CMR/Updated+facet+response"
  (:require [cmr.common-app.services.search.query-execution :as query-execution]
            [cmr.common-app.services.kms-fetcher :as kms-fetcher]
            [cmr.search.services.query-execution.facets.facets-results-feature :as frf]
            [cmr.search.services.query-execution.facets.links-helper :as lh]
            [camel-snake-kebab.core :as csk]
            [cmr.common.util :as util]
            [ring.util.codec :as codec]
            [clojure.string :as str]
            [clojure.set :as set]
            [cmr.transmit.connection :as conn]))

(def UNLIMITED_TERMS_SIZE
  "The maximum number of allowed results to return from any terms query."
  10000)

(def DEFAULT_TERMS_SIZE
  "The default limit for the number of results to return from any terms query for v2 facets."
  50)

(defn- terms-facet
  "Construct a terms query to be applied for the given field. Size specifies the number of results
  to return."
  [field size]
  {:terms {:field field :size size}})

(defn- hierarchical-aggregation-builder
  "Build an aggregations query for the given hierarchical field."
  [field field-hierarchy size]
  (when-let [subfield (first field-hierarchy)]
    {subfield {:terms {:field (str (name field) "." (name subfield))
                       :size size}
               :aggs (merge {:coll-count frf/collection-count-aggregation}
                            (hierarchical-aggregation-builder field (rest field-hierarchy) size))}}))

(defn- nested-facet
  "Returns the nested aggregation query for the given hierarchical field. Size specifies the number
  of results to return."
  [field size]
  {:nested {:path field}
   :aggs (hierarchical-aggregation-builder field
                                           (remove #{:url} (field kms-fetcher/nested-fields-mappings))
                                           size)})

(defn- facets-v2-aggregations
  "This is the aggregations map that will be passed to elasticsearch to request faceted results
  from a collection search. Size specifies the number of results to return. Only a subset of the
  facets are returned in the v2 facets, specifically those that help enable dataset discovery."
  [size]
  {:science-keywords (nested-facet :science-keywords size)
   :platform (terms-facet :platform-sn size)
   :instrument (terms-facet :instrument-sn size)
   :data-center (terms-facet :data-center size)
   :project (terms-facet :project-sn2 size)
   :processing-level-id (terms-facet :processing-level-id size)})

(def v2-facets-root
  "Root element for the facet response"
  {:title "Browse Collections"
   :type :group})

(def fields->human-readable-label
  "Map of facet fields to their human readable label."
  {:data-center "Organizations"
   :project "Projects"
   :platform "Platforms"
   :instrument "Instruments"
   :processing-level-id "Processing levels"
   :science-keywords "Keywords"})

(def sorted-facet-map
  "A map that sorts the keys of the facet map so it is presented in a pleasing way to Users
  of the API. The nested hierarchical maps API can be hard to understand if the maps are ordered
  randomly."
  (util/key-sorted-map [:title :type :applied :count :links :has_children :children]))

(defn- generate-group-node
  "Returns a group node for the provided title, applied?, and children."
  [title applied? children]
  {:title title
   :type :group
   :applied applied?
   :has_children (some? children)
   :children children})

(defn- generate-filter-node
  "Returns a function to generate a child node with the provided base-url, query-params, and
  field-name. Returned function takes two arguments (a term and a count of collections containing
  that term)."
  [base-url query-params field-name some-term-applied?]
  (fn [[term count]]
    (let [links (if some-term-applied?
                    (lh/create-link base-url query-params field-name term)
                    (lh/create-apply-link base-url query-params field-name term))]
     (merge sorted-facet-map
            {:title term
             :type :filter
             :applied (= :remove (first (keys links)))
             :links links
             :count count
             :has_children false}))))

(defn- field-applied?
  "Returns whether any value is set in the passed in query-params for the provided hierarchical
  field."
  [query-params parent-field subfield]
  (let [subfield-reg-ex (re-pattern (str parent-field ".*" subfield ".*"))
        relevant-query-params (filter (fn [[k v]] (re-matches subfield-reg-ex k)) query-params)]
    (some? (seq relevant-query-params))))

(defn- generate-hierarchical-filter-node
  "Generates a filter node for a hierarchical field. Takes a title, count, links and sub-facets."
  [title count links sub-facets]
  (merge sorted-facet-map
         {:has_children false
          :applied false}
         sub-facets
         {:title title
          :applied (= :remove (first (keys links)))
          :links links
          :count count
          :type :filter}))

(defn- generate-hierarchical-children
  "Generate children nodes for a hierarchical facet v2 response.
  recursive-parse-fn - function to call to recursively generate any children filter nodes.
  generate-links-fn - function to call to generate the links field in the facets v2 response for
                      the passed in field.
  field - the hierarchical subfield to generate the filter nodes for in the v2 response.
  elastic-aggregations - the portion of the elastic aggregations response to parse to generate
                         the part of the facets v2 response related to the passed in field."
  [recursive-parse-fn generate-links-fn field elastic-aggregations]
  ;; Each value for this field has its own bucket in the elastic aggregations response
  (for [bucket (get-in elastic-aggregations [field :buckets])
        :let [value (:key bucket)
              count (get-in bucket [:coll-count :doc_count] (:doc_count bucket))
              links (generate-links-fn value)
              sub-facets (recursive-parse-fn bucket)]]
    (generate-hierarchical-filter-node value count links sub-facets)))

(defn- parse-hierarchical-bucket-v2
  "Recursively parses the elasticsearch aggregations response and generates version 2 facets.
  parent-field - The top level field name for a hierarchical field - for example :science-keywords
  field-hierarchy - An ordered array of all the unprocessed subfields within the parent field
                    hierarchy. For example the first time the function is called the array may be
                    [:category :topic :term] and on the next recursion it will be [:topic :term]
                    The recursion ends once the field hierarchy is empty.
  base-url - The root URL to use for the links that are generated in the facet response.
  query-params - the query parameters from the current search as a map with a key for each
                 parameter name and the value as either a single value or a collection of values.
  elastic-aggregations - the portion of the elastic-aggregations response to parse. As each field
                         is parsed recursively the aggregations are reduced to just the portion
                         relevant to that field."
  [parent-field field-hierarchy base-url query-params elastic-aggregations]
  ;; Iterate through the next field in the hierarchy. Return nil if there are no more fields in
  ;; the hierarchy
  (when-let [field (first field-hierarchy)]
    (let [snake-parent-field (csk/->snake_case_string parent-field)
          snake-field (csk/->snake_case_string field)
          applied? (field-applied? query-params snake-parent-field snake-field)
          ;; Index in the param name does not matter
          param-name (format "%s[0][%s]" snake-parent-field snake-field)
          ;; If no value is applied in the search for the given field we can safely call create
          ;; apply link. Otherwise we need to determine if an apply or a remove link should be
          ;; generated.
          generate-links-fn (if applied?
                              (partial lh/create-link-for-hierarchical-field base-url query-params
                                       param-name)
                              (partial lh/create-apply-link-for-hierarchical-field base-url
                                       query-params param-name))
          recursive-parse-fn (partial parse-hierarchical-bucket-v2 parent-field
                                      (rest field-hierarchy) base-url query-params)
          children (generate-hierarchical-children recursive-parse-fn generate-links-fn field
                                                   elastic-aggregations)]
      (when (seq children)
        (generate-group-node (csk/->snake_case_string field) true children)))))

(defn- get-search-terms
  "TODO"
  [base-field subfield query-params]
  (let [base-field (csk/->snake_case_string base-field)
        subfield (csk/->snake_case_string subfield)
        field-regex (re-pattern (format "%s.*%s\\]" base-field (subs subfield 0 (count subfield))))
        matching-keys (keep #(re-matches field-regex %) (keys query-params))]
    (flatten (vals (select-keys query-params matching-keys)))))

(defn- get-terms-at-depth
  "TODO"
  [facet depth terms]
  (if (<= depth 0)
      (concat terms (keep :title (:children facet)))
      (when (:children facet)
        (apply concat terms (for [child-facet (:children facet)]
                              (get-terms-at-depth child-facet (dec depth) terms))))))

(defn- get-terms-for-subfield
  "TODO"
  [hierarchical-facet subfield field-hierarchy]
  (let [facet-depth (.indexOf field-hierarchy subfield)]
    (get-terms-at-depth hierarchical-facet facet-depth [])))

(defn- get-missing-subfield-term-tuples
  "TODO"
  [field field-hierarchy hierarchical-facet query-params]
  (remove nil?
    (apply concat
           (keep (fn [subfield]
                   (when-let [search-terms (seq (get-search-terms field subfield query-params))]
                     (let [terms-in-facets (map str/lower-case
                                                (get-terms-for-subfield hierarchical-facet subfield
                                                                        field-hierarchy))]
                       (for [term search-terms]
                         (when-not (some #{(str/lower-case term)} terms-in-facets)
                           [subfield term])))))
                 field-hierarchy))))

(defn hierarchical-bucket-map->facets-v2
  "Takes a map of elastic aggregation results for a nested field. Returns a hierarchical facet for
  that field."
  [field bucket-map base-url query-params]
  (let [field-hierarchy (field kms-fetcher/nested-fields-mappings)
        hierarchical-facet (parse-hierarchical-bucket-v2 field field-hierarchy base-url
                                                         query-params bucket-map)
        subfield-term-tuples (get-missing-subfield-term-tuples field field-hierarchy
                                                               hierarchical-facet query-params)
        snake-case-field (csk/->snake_case_string field)
        facets-with-zero-matches (for [[subfield search-term] subfield-term-tuples
                                       :let [param-name (format "%s[0][%s]"
                                                                snake-case-field
                                                                (csk/->snake_case_string subfield))
                                             link (lh/create-link-for-hierarchical-field
                                                   base-url query-params param-name search-term)]]
                                   (generate-hierarchical-filter-node search-term 0 link nil))]
    (if (seq facets-with-zero-matches)
        (update-in hierarchical-facet [:children] #(concat % facets-with-zero-matches))
        hierarchical-facet)))

(defn- create-hierarchical-v2-facets
  "Parses the elastic aggregations and generates the v2 facets for all hierarchical fields."
  [elastic-aggregations base-url query-params]
  (let [hierarchical-fields [:science-keywords]]
    (keep (fn [field]
              (when-let [sub-facets (hierarchical-bucket-map->facets-v2
                                      field (field elastic-aggregations)
                                      base-url
                                      query-params)]
                (let [field-reg-ex (re-pattern (str (csk/->snake_case_string field) ".*"))
                      applied? (some? (seq (filter (fn [[k v]] (re-matches field-reg-ex k))
                                                   query-params)))]
                  (merge sorted-facet-map
                         (assoc sub-facets
                                :title (field fields->human-readable-label)
                                :applied applied?)))))
          hierarchical-fields)))

(defn- add-terms-with-zero-matching-collections
  "Takes a sequence of tuples and a sequence of search terms. The tuples are of the form search term
  and number of matching collections. For any search term provided that is not in the tuple of value
  counts, a new tuple is added with the search term and a count of 0."
  [value-counts search-terms]
  (let [all-facet-values (map #(str/lower-case (first %)) value-counts)
        missing-terms (remove #(some (set [(str/lower-case %)]) all-facet-values) search-terms)]
    (reduce #(conj %1 [%2 0]) value-counts missing-terms)))

(defn- create-flat-v2-facets
  "Parses the elastic aggregations and generates the v2 facets for all flat fields."
  [elastic-aggregations base-url query-params]
  (let [flat-fields [:platform :instrument :data-center :project :processing-level-id]]
    (remove nil?
      (for [field-name flat-fields
            :let [search-terms-from-query (lh/get-values-for-field query-params field-name)
                  value-counts (add-terms-with-zero-matching-collections
                                (frf/buckets->value-count-pairs (field-name elastic-aggregations))
                                search-terms-from-query)
                  snake-case-field (csk/->snake_case_string field-name)
                  applied? (some? (or (get query-params snake-case-field
                                            (get query-params (str snake-case-field "[]")))))
                  children (map (generate-filter-node base-url query-params field-name applied?)
                                value-counts)]]
        (when (seq children)
          (generate-group-node (field-name fields->human-readable-label) applied? children))))))

(defn- parse-params
  "Parse parameters from a query string into a map. Taken directly from ring code."
  [params encoding]
  (let [params (codec/form-decode params encoding)]
    (if (map? params) params {})))

(defn- collection-search-root-url
  "The root URL for executing a collection search against the CMR."
  [context]
  (let [public-search-config (set/rename-keys (get-in context [:system :public-conf])
                                              {:relative-root-url :context})]
    (format "%s/collections.json" (conn/root-url public-search-config))))

(defn create-v2-facets
  "Create the facets v2 response. Parses an elastic aggregations result and returns the facets."
  [context aggs]
  (let [base-url (collection-search-root-url context)
        query-params (parse-params (:query-string context) "UTF-8")
        ; facets (generate-removal-links base-url query-params)
        facets (concat (create-hierarchical-v2-facets aggs base-url query-params)
                       (create-flat-v2-facets aggs base-url query-params))]
    (if (seq facets)
      (assoc v2-facets-root :has_children true :children facets)
      (assoc v2-facets-root :has_children false))))

(defmethod query-execution/pre-process-query-result-feature :facets-v2
  [_ query _]
  ;; With CMR-1101 we will support a parameter to specify the number of terms to return. For now
  ;; always use the DEFAULT_TERMS_SIZE
  (assoc query :aggregations (facets-v2-aggregations DEFAULT_TERMS_SIZE)))

(defmethod query-execution/post-process-query-result-feature :facets-v2
  [context _ {:keys [aggregations]} query-results _]
  (assoc query-results :facets (create-v2-facets context aggregations)))
