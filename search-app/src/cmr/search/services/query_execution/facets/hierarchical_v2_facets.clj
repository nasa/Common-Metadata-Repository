(ns cmr.search.services.query-execution.facets.hierarchical-v2-facets
  "Functions for generating v2 facet responses for hierarchical fields. Hierarchical fields are any
  fields which contain some subfields such as science keywords which have subfields of Category,
  Topic, Term, and Variable Levels 1, 2, and 3. On the query parameter API hierarchical fields are
  specified with field[index][subfield] such as science_keyword[0][category]."
  (:require [cmr.common-app.services.kms-fetcher :as kms-fetcher]
            [cmr.search.services.query-execution.facets.facets-results-feature :as frf]
            [cmr.search.services.query-execution.facets.facets-v2-helper :as v2h]
            [cmr.search.services.query-execution.facets.links-helper :as lh]
            [cmr.common.util :as util]
            [camel-snake-kebab.core :as csk]
            [clojure.string :as str]))

(defn- nested-fields-mappings
  "Returns nested field mappings for the given field, ignoring humanizer suffixes"
  [field]
  (let [stripped-field (str/replace (str/replace (name field) #"-h$" "") #"\.humanized$" "")]
    (kms-fetcher/nested-fields-mappings (keyword stripped-field))))

(defn- get-max-subfield-index
  "Return the maximum subfield index from the hierarchical-field-mappings for any of the supplied
  subfields. Return index as 1 based instead of 0 based. A value of 0 indicates that there are no
  subfields which are present in the hierarchical-field-mappings."
  [subfields hierarchical-field-mappings]
  (if-let [indices (seq
                    (keep (fn [subfield]
                            (.indexOf hierarchical-field-mappings
                                      (csk/->kebab-case-keyword subfield)))
                          subfields))]
    (inc (apply max indices))
    0))

(def min-hierarchical-depth
  "Minimum depth to request for hierarchical aggregations queries from elastic. Default to a minimum
  depth of 3 levels (e.g. Category, Topic, and Term for science keywords)."
  3)

(def num-levels-below-subfield
  "Number of levels below the lowest level subfield to request for hierarchical aggregations queries
  from elastic."
  2)

(defn get-depth-for-hierarchical-field
  "Returns what depth should be used when requesting aggregations for facets for a hierarchical
  field based on the query-params. Default to a minimum depth of 'min-hierarchical-depth'.
  Otherwise return the smaller of 'num-levels-below-subfield' below the lowest level subfield
  present in the query parameters or the full depth of the field. Note that this is strictly to
  improve the performance of the aggregations query in Elasticsearch. We further prune the results
  to limit based on what terms have been applied as part of building the facet response from the
  elasticsearch results."
  [query-params parent-field]
  (let [parent-field-snake-case (csk/->snake_case_string parent-field)
        field-regex (re-pattern (format "%s\\[\\d+\\]\\[(.*)\\]" parent-field-snake-case))
        matching-subfields (keep #(second (re-matches field-regex %)) (keys query-params))
        all-subfields (remove #{:url} (nested-fields-mappings parent-field))]
    (max min-hierarchical-depth
         (min (count all-subfields)
              (+ num-levels-below-subfield
                 (get-max-subfield-index matching-subfields all-subfields))))))

(defn- hierarchical-aggregation-builder
  "Build an aggregations query for the given hierarchical field."
  [field field-hierarchy size]
  (when-let [subfield (first field-hierarchy)]
    {subfield {:terms {:field (str (name field) "." (name subfield))
                       :size size}
               :aggs (merge {:coll-count frf/collection-count-aggregation}
                            (hierarchical-aggregation-builder field (rest field-hierarchy) size))}}))

(defn nested-facet
  "Returns the nested aggregation query for the given hierarchical field. Size specifies the number
  of results to return."
  ([field size]
   (nested-facet field size nil))
  ([field size depth]
   (let [subfields (if depth
                       (take depth (nested-fields-mappings field))
                       (nested-fields-mappings field))]
     {:nested {:path field}
      :aggs (hierarchical-aggregation-builder
             field (remove #{:url} subfields) size)})))

(defn- field-applied?
  "Returns whether any value is set in the passed in query-params for the provided hierarchical
  field."
  [query-params parent-field subfield]
  (let [subfield-reg-ex (re-pattern (str parent-field ".*" subfield ".*"))
        relevant-query-params (filter (fn [[k v]] (re-matches subfield-reg-ex k)) query-params)]
    (some? (seq relevant-query-params))))

(comment
 (seq (get-indexes-in-params {"science_keywords[0][category]" "blah"
                              "science_keywords[12][category]" "blahz"
                              "science_keywords[4][ctegory]" "blah"}
                             "science_keywords" "category" "blah")))

(defn- get-indexes-in-params
  "Returns a list of all of the indexes for the given hierarchical field within the query-params
  that have the provided value.
  Example: Params of {\"foo[2][bar]\" \"alpha\"} \"foo\" \"bar\" \"alpha\" returns #{\"2\"}."
  [query-params base-field subfield value]
  (when (and base-field subfield value)
    (let [value-lowercase (str/lower-case value)
          subfield-reg-ex (re-pattern (str base-field "\\[(\\d+)\\]\\[" subfield "\\]"))
          relevant-indexes (keep (fn [[k v]]
                                   (when (= value-lowercase (str/lower-case v))
                                     (second (re-matches subfield-reg-ex k))))
                                 query-params)]
      (set (map #(Integer/parseInt %) relevant-indexes)))))

(defn- get-index-for-field-and-value
  "Returns the first index for the given hierarchical field within the query-params
  that has the provided value.
  Example: Params of {\"foo[2][bar]\" \"alpha\"} \"foo\" \"bar\" \"alpha\" returns #{\"2\"}."
  [query-params base-field subfield value]
  (when (and base-field subfield value)
    (let [value-lowercase (str/lower-case value)
          subfield-regex (re-pattern (str base-field "\\[(\\d+)\\]\\[" subfield "\\]"))]
      (some-> (for [[k v] query-params
                    :when (= value-lowercase (str/lower-case v))]
                (second (re-matches subfield-regex k)))
              first
              Integer/parseInt))))

(defn- find-applied-children
  "Returns a sequence of tuples for any child facet that is applied in the current search query.
  Searches the children facets recursively. The tuples are of the form [subfield value].

  facet - hierarchical v2 facet
  field-hierarchy - the part of the hierarchy that applies at the current depth of the facet
  include-root? - True if the top level term should be included."
  [facet field-hierarchy include-root?]
  (when (:applied facet)
    (let [applied-children (remove nil?
                                   (mapcat #(find-applied-children % (rest field-hierarchy) true)
                                           (:children facet)))]
      (if include-root?
        (conj applied-children [(first field-hierarchy) (:title facet)])
        applied-children))))

(comment
 (has-siblings? "science_keywords" "category" "blah" "topic" "atm"
                {"science_keywords[0][category]" "blah"
                 "science_keywords[12][category]" "blahz"
                 "science_keywords[4][ctegory]" "blah"
                 "science_keywords" "category"})

 (has-siblings? "science_keywords" "category" "blah" "topic" "atm"
                {"science_keywords[0][category]" "blah"
                 "science_keywords[1][category]" "blahz"
                 "science_keywords[4][topic]" "joe"
                 "science_keywords[5][topic]" "abc"
                 "science_keywords[6][topic]" "ATM"
                 "science_keywords[4][category]" "Blahz"
                 "science_keywords[5][category]" "bLAH"
                 "science_keywords[6][category]" "Blah"
                 "science_keywords" "category"}))

(defn- has-siblings?
  "Returns true if the given hierarchical field and value have any applied sibling values in the
  provided query params. Comparisons to the provided value are made case in a case insensitive
  manner."
  [query-params base-field parent-subfield parent-value subfield value]
  (some?
    (when (and parent-value value)
      (let [parent-value-lowercase (str/lower-case parent-value)
            value-lowercase (str/lower-case value)
            query-params (util/map-values str/lower-case query-params)
            subfield-regex (re-pattern (str base-field "\\[(\\d+)\\]\\[" subfield "\\]"))
            same-level-indexes (for [[k v] query-params
                                     :when (not= value-lowercase (str/lower-case v))]
                                  (second (re-matches subfield-regex k)))]
        ;; Filter the query-params to just those with the same index, parent-subfield, and
        ;; parent-value when compared case insensitively
        (seq (for [idx same-level-indexes
                   :let [query-param (str base-field "[" idx "][" parent-subfield "]")]
                   :when (= parent-value-lowercase (get query-params query-param))]
               query-param))))))

(defn- generate-hierarchical-children
  "Generate children nodes for a hierarchical facet v2 response.
  recursive-parse-fn - function to call to recursively generate any children filter nodes.
  generate-links-fn - function to call to generate the links field in the facets v2 response for
                      the passed in field.
  field - the hierarchical subfield to generate the filter nodes for in the v2 response.
  elastic-aggregations - the portion of the elastic aggregations response to parse to generate
                         the part of the facets v2 response related to the passed in field."
  [recursive-parse-fn has-siblings-fn generate-links-fn field field-hierarchy elastic-aggregations]
  ;; Each value for this field has its own bucket in the elastic aggregations response
  (for [bucket (get-in elastic-aggregations [field :buckets])
        :let [value (:key bucket)
              count (get-in bucket [:coll-count :doc_count] (:doc_count bucket))
              sub-facets (recursive-parse-fn value bucket)
              ;; Sort alphabetically
              sub-facets (when (seq (:children sub-facets))
                           (update sub-facets :children
                                   #(sort-by :title util/compare-natural-strings %)))
              children-values-to-remove (find-applied-children sub-facets field-hierarchy false)
              has-siblings? (has-siblings-fn value)
              links (generate-links-fn value has-siblings? children-values-to-remove)]]
    (v2h/generate-hierarchical-filter-node value count links sub-facets)))

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
  elastic-aggs - the portion of the elastic-aggregations response to parse. As each field is parsed
                 recursively the aggregations are reduced to just the portion relevant to that
                 field."
  [parent-field parent-subfield field-hierarchy base-url query-params ancestors-map parent-value
   elastic-aggs]
  ;; Iterate through the next field in the hierarchy. Return nil if there are no more fields in
  ;; the hierarchy
  (when-let [field (first field-hierarchy)]
    (let [snake-parent-field (csk/->snake_case_string parent-field)
          snake-parent-subfield (when parent-subfield (csk/->snake_case_string parent-subfield))
          snake-field (csk/->snake_case_string field)
          ancestors-map (if parent-value
                          (assoc ancestors-map snake-parent-subfield parent-value)
                          ancestors-map)
          applied? (field-applied? query-params snake-parent-field snake-field)
          ;; Index in the param name does not matter
          param-name (format "%s[0][%s]" snake-parent-field snake-field)
          parent-indexes (get-indexes-in-params query-params snake-parent-field
                                                snake-parent-subfield parent-value)
          ;; If no value is applied in the search for the given field we can safely call create
          ;; apply link. Otherwise we need to determine if an apply or a remove link should be
          ;; generated.
          generate-links-fn (if applied?
                              (partial lh/create-link-for-hierarchical-field base-url query-params
                                       param-name ancestors-map parent-indexes)
                              (partial lh/create-apply-link-for-hierarchical-field base-url
                                       query-params param-name ancestors-map parent-indexes))
          recursive-parse-fn (partial parse-hierarchical-bucket-v2 parent-field field
                                      (rest field-hierarchy) base-url query-params ancestors-map)
          has-siblings-fn (partial has-siblings? query-params snake-parent-field
                                   snake-parent-subfield parent-value snake-field)
          children (generate-hierarchical-children recursive-parse-fn has-siblings-fn
                                                   generate-links-fn field field-hierarchy
                                                   elastic-aggs)]
      (when (seq children)
        (v2h/generate-group-node (csk/->snake_case_string field) true children)))))

(defn- get-search-terms-for-hierarchical-field
  "Returns all of the search terms applied in the passed in query params for the provided
  hierarchical field."
  [base-field subfield query-params]
  (let [base-field (csk/->snake_case_string base-field)
        subfield (csk/->snake_case_string subfield)
        field-regex (re-pattern (format "%s.*%s\\]" base-field (subs subfield 0 (count subfield))))
        matching-keys (keep #(re-matches field-regex %) (keys query-params))]
    (flatten (vals (select-keys query-params matching-keys)))))

(defn- get-terms-at-depth
  "Recursively parse the provided facet response for a hierarchical field to get a list of all of
  the facet terms at the provided depth in the tree. Returns a sequence of the terms."
  [facet depth terms]
  (if (<= depth 0)
      (concat terms (keep :title (:children facet)))
      (when (:children facet)
        (apply concat terms (for [child-facet (:children facet)]
                              (get-terms-at-depth child-facet (dec depth) terms))))))

(defn- get-terms-for-subfield
  "Returns all of the terms for the provided subfield within a hierarchical facet response."
  [hierarchical-facet subfield field-hierarchy]
  (let [facet-depth (.indexOf field-hierarchy subfield)]
    (get-terms-at-depth hierarchical-facet facet-depth [])))

(defn- get-missing-subfield-term-tuples
  "Compares the query-params to the hierarchical-facet response to look for any search terms in
  the query-params which are not present in the hierarchical facet response. Returns a sequence of
  tuples with any missing terms. Each tuple contains a subfield and a search term."
  [field field-hierarchy hierarchical-facet query-params]
  (let [field-hierarchy (if (= :science-keywords-h field)
                          ;; Special case for science keywords to ignore the first field (category)
                          ;; since we do not actually return categories in the v2 facet response
                          (rest field-hierarchy)
                          field-hierarchy)]
    (remove nil?
      (apply concat
         (keep (fn [subfield]
                 (when-let [search-terms (seq (get-search-terms-for-hierarchical-field
                                               field subfield query-params))]
                   (let [terms-in-facets (map str/lower-case
                                              (get-terms-for-subfield hierarchical-facet subfield
                                                                      field-hierarchy))]
                     (for [term search-terms]
                       (when-not (some #{(str/lower-case term)} terms-in-facets)
                         [subfield term])))))
               field-hierarchy)))))

(defn- prune-hierarchical-facet
  "Limits a hierarchical facet to a single level below the lowest applied facet. If
  one-additional-level? is set to true it will not prune at the current level, but at one filter
  node below the current level. This is used for example to always return Category and Topic for
  science keywords."
  [hierarchical-facet one-additional-level?]
  (if (:children hierarchical-facet)
    (if (or one-additional-level? (:applied hierarchical-facet))
      ;; The initial facet can have a pseudo-group node that gets replaced by later processing.
      ;; In this case we want to return two additional levels instead of just one.
      (let [additional-level? (= :group (:type hierarchical-facet))]
        (update hierarchical-facet :children (fn [original-children]
                                               (mapv #(prune-hierarchical-facet % additional-level?)
                                                     original-children))))
      ;; Else prune the children
      (dissoc hierarchical-facet :children))
    hierarchical-facet))

(defn- create-facets-with-zero-matches
  "Helper function to create v2 facets for terms which are included in the search query, but have
  zero matching collections. This allows the user to easily remove an applied facet."
  [base-url query-params field subfield-term-tuples]
  (for [[subfield search-term] subfield-term-tuples
         :let [param-name (format "%s[0][%s]"
                                  (csk/->snake_case_string field)
                                  (csk/->snake_case_string subfield))
               link (lh/create-link-for-hierarchical-field
                     base-url query-params param-name nil nil search-term false nil)]]
     (v2h/generate-hierarchical-filter-node search-term 0 link nil)))

(def earth-science-category-string
  "Constant for the string used for the Earth Science category within humanized science keywords."
  "Earth Science")

(defn- remove-non-earth-science-keywords
  "V2 facets only include science keyword facets which have a category of Earth Science. Removes
  any science keywords facets that have any other category."
  [hierarchical-facet field]
  (if (= :science-keywords-h field)
    (let [updated-facet (update hierarchical-facet :children
                                (fn [children]
                                  (filter #(= earth-science-category-string (:title %)) children)))]
      (when-let [earth-science-facets (first (:children updated-facet))]
        ;; Do not return the Earth Science category facet itself, just return the topics below
        (assoc updated-facet :children (:children earth-science-facets))))
    hierarchical-facet))

(defn- hierarchical-bucket-map->facets-v2
  "Takes a map of elastic aggregation results for a nested field. Returns a hierarchical facet for
  that field."
  [field bucket-map base-url query-params]
  (let [field-hierarchy (nested-fields-mappings field)
        ;; TODO Figure out a better way to pass in the params without all these nils
        hierarchical-facet (-> (parse-hierarchical-bucket-v2 field nil field-hierarchy base-url
                                                             query-params nil nil bucket-map)
                               (prune-hierarchical-facet true)
                               (remove-non-earth-science-keywords field))
        subfield-term-tuples (get-missing-subfield-term-tuples field field-hierarchy
                                                               hierarchical-facet query-params)
        facets-with-zero-matches (create-facets-with-zero-matches base-url query-params field
                                                                  subfield-term-tuples)]
    (if (seq facets-with-zero-matches)
      ;; Add in links to remove any hierarchical fields that have been applied to the query-params
      ;; but do not have any matching collections.
      (update hierarchical-facet :children #(concat % facets-with-zero-matches))
      hierarchical-facet)))

(defn create-hierarchical-v2-facets
  "Parses the elastic aggregations and generates the v2 facets for all hierarchical fields."
  [elastic-aggregations base-url query-params]
  (let [hierarchical-fields [:science-keywords-h]]
    (keep (fn [field]
              (when-let [sub-facets (hierarchical-bucket-map->facets-v2
                                      field (field elastic-aggregations)
                                      base-url
                                      query-params)]
                (let [field-reg-ex (re-pattern (str (csk/->snake_case_string field) ".*"))
                      applied? (some? (seq (filter (fn [[k v]] (re-matches field-reg-ex k))
                                                   query-params)))]
                  (merge v2h/sorted-facet-map
                         (assoc sub-facets
                                :title (field v2h/fields->human-readable-label)
                                :applied applied?)))))
          hierarchical-fields)))
