(ns cmr.search.services.query-execution.facets.links-helper
  "Functions to create the links that are included within v2 facets. Facets (v2) includes links
  within each value to conduct the same search with either a value added to the query or with the
  value removed. This namespace contains functions to create the links that include or exclude a
  particular parameter.

  Commonly used parameters in the functions include:

  base-url - root URL for the link being created.
  query-params - the query parameters from the current search as a map with a key for each
                 parameter name and the value as either a single value or a collection of values.
  field-name - the query-field that needs to be added to (or removed from) the current search.
  value - the value to apply (or remove) for the given field-name."
  (:require [camel-snake-kebab.core :as csk]
            [ring.util.codec :as codec]
            [clojure.string :as str]
            [cmr.common.util :as util]))

(defn- set-and-options-for-param
  "If the provided field has more than one value applied in the query params, add in an options
  query parameter to AND the multiple values together. If there is already an and options query
  parameter, leave the value set as is. If the field is not present in the query params or there is
  only one value, then remove the and option if it is present in the query parameters. Returns the
  full updated query parameters."
  [query-params field]
  (let [param-snake-case (csk/->snake_case_string field)
        values (remove nil?
                       (flatten
                        ;; Query parameters can contain either a sequence of values or a single
                        ;; value. Wrap them in vectors and then flatten to handle both cases.
                        (concat [(get query-params (str param-snake-case "[]"))]
                                [(get query-params param-snake-case)])))
        include-and-option-for-param? (< 1 (count values))
        and-option (str "options[" param-snake-case "][and]")]
    (if include-and-option-for-param?
      (if (contains? query-params and-option)
        query-params
        (assoc query-params and-option true))
      (dissoc query-params and-option))))

(defn- generate-query-string
  "Creates a query string from a root URL and a map of query params"
  [base-url query-params]
  (if (seq query-params)
    (let [fields [:platform-h :instrument-h :data-center-h :project-h :processing-level-id-h]
          query-params (reduce set-and-options-for-param query-params fields)]
      (format "%s?%s" base-url (codec/form-encode query-params)))
    base-url))

(defn create-apply-link
  "Create a link that will modify the current search to also filter by the given field-name and
  value."
  [base-url query-params field-name value]
  (let [field-name (str (csk/->snake_case_string field-name) "[]")
        existing-values (flatten [(get query-params field-name)])
        updated-query-params (assoc query-params
                                    field-name
                                    (remove nil? (conj existing-values value)))]
    {:apply (generate-query-string base-url updated-query-params)}))

(defn- remove-value-from-query-params
  "Removes the provided value (treated case insensitively) from the query-params. Checks
  query-params for keys that match the field-name or field-name[]. For example both platform and
  platform[] are matched against in the query parameter keys if the field-name is :platform."
  [query-params field-name value]
  (let [value (str/lower-case value)
        field-name-snake-case (csk/->snake_case_string field-name)]
    (reduce
     (fn [query-params field-name]
        (if (coll? (get query-params field-name))
           (update query-params field-name
                   (fn [existing-values]
                     (remove (fn [existing-value]
                               (= (str/lower-case existing-value) value))
                             existing-values)))
           (dissoc query-params field-name)))
     query-params
     [field-name-snake-case (str field-name-snake-case "[]")])))

(defn create-remove-link
  "Create a link that will modify the current search to no longer filter on the given field-name and
  value. Looks for matches case insensitively."
  [base-url query-params field-name value]
  (let [updated-query-params (remove-value-from-query-params query-params field-name value)]
    {:remove (generate-query-string base-url updated-query-params)}))

(defn get-values-for-field
  "Returns a list of all of the values for the provided field-name. Includes values for keys of
   both field-name and field-name[]."
  [query-params field-name]
  (let [field-name-snake-case (csk/->snake_case_string field-name)]
    (remove empty?
            (reduce (fn [values-for-field field]
                      (let [value-or-values (get query-params field)]
                        (if (coll? value-or-values)
                          (conj value-or-values values-for-field)
                          (if (seq value-or-values)
                            (cons value-or-values values-for-field)
                            values-for-field))))
                    []
                    [field-name-snake-case (str field-name-snake-case "[]")]))))

(defn create-link
  "Creates either a remove or an apply link based on whether this particular value is already
  being filtered on within the provided query-params. Returns a tuple of the type of link created
  and the link itself. Looks for matches case insensitively."
  [base-url query-params field-name value]
  (let [field-name-snake-case (csk/->snake_case_string field-name)
        values-for-field (get-values-for-field query-params field-name)
        value-exists (some #{(str/lower-case value)}
                          (keep #(when % (str/lower-case %)) values-for-field))]
    (if value-exists
      (create-remove-link base-url query-params field-name value)
      (create-apply-link base-url query-params field-name value))))

(defn- get-max-index-for-field-name
  "Returns the max index for the provided field-name within the query parameters.

  For example if the query parameters included fields foo[0][alpha]=bar and foo[6][beta]=zeta the
  max index of field foo would be 6. If the field is not found then -1 is returned."
  [query-params base-field]
  (let [field-regex (re-pattern (format "%s\\[\\d+\\]\\[.*\\]" base-field))
        matches (keep #(re-matches field-regex %) (keys query-params))
        indexes (keep #(second (re-matches #".*\[(\d+)\].*" %)) matches)
        indexes-int (map #(Integer/parseInt %) indexes)]
    (if (seq indexes-int)
      (apply max indexes-int)
      -1)))

(comment
 (first #{3 1 2}))

(defn create-apply-link-for-hierarchical-field
  "Create a link that will modify the current search to also filter by the given hierarchical
  field-name and value.
  Field-name must be of the form <string>[<int>][<string>] such as science_keywords[0][topic]."
  [base-url query-params field-name ancestors-map parent-indexes value has-siblings? _]
  (let [[base-field subfield] (str/split field-name #"\[\d+\]")
        index-to-use (if (or has-siblings? (empty? parent-indexes))
                       (inc (get-max-index-for-field-name query-params base-field))
                       (first parent-indexes))
        updated-field-name (format "%s[%d]%s" base-field index-to-use subfield)
        updated-query-params (assoc query-params updated-field-name value)
        ; _ (if has-siblings? (println "The ancestors-map is" ancestors-map))
        updated-query-params (if (or has-siblings? (empty? parent-indexes))
                               ;; Add all of the ancestors for this index
                               (reduce
                                (fn [query-params [k v]]
                                  (let [query-param (format "%s[%d][%s]" base-field index-to-use k)]
                                    (assoc query-params query-param v)))
                                updated-query-params
                                (dissoc ancestors-map "category"))
                               updated-query-params)]

    {:apply (generate-query-string base-url updated-query-params)}))

(defn- get-keys-to-update
  "Returns a sequence of keys that have multiple values where at least one of values matches the
  passed in value. Looks for matches case insensitively."
  [query-params value]
  (let [value (str/lower-case value)]
    (keep (fn [[k value-or-values]]
            (when (coll? value-or-values)
              (when (some #{value} (map str/lower-case value-or-values))
                k)))
          query-params)))

(defn- get-keys-to-remove
  "Returns a sequence of keys that have a single value which matches the passed in value. Looks for
  matches case insensitively."
  [query-params value]
  (let [value (str/lower-case value)]
    (keep (fn [[k value-or-values]]
            (when-not (coll? value-or-values)
              (when (= (str/lower-case value-or-values) value)
                k)))
          query-params)))

(defn get-potential-matching-query-params
  "Returns a subset of query parameters whose keys match the provided field-name and ignoring the
  index.

  As an example, consider passing in a field-name of foo[0][alpha] and query parameters
  of foo[0][alpha]=fish, foo[0][beta]=cat, and foo[1][alpha]=dog. The returned query params would be
  foo[0][alpha]=fish and foo[1][alpha]=dog, but not foo[0][beta]=cat. Note that the same query
  params would also be returned for any field-name of foo[<n>][alpha] where n is an integer.
  Field-name must be of the form <string>[<int>][<string>]."
  [query-params field-name]
  (let [[base-field subfield] (str/split field-name #"\[0\]")
        field-regex (re-pattern (format "%s.*%s" base-field (subs subfield 1 (count subfield))))
        matching-keys (keep #(re-matches field-regex %) (keys query-params))]
    (when (seq matching-keys)
      (select-keys query-params matching-keys))))

(defn- remove-value-from-query-params-for-hierachical-field
  "Removes the value from anywhere it matches in the provided potential-query-param-matches.
  Each key in the potential-query-param-matches may contain a single value or a collection of
  values. If the key contains a single value which matches the passed in value the query parameter
  is removed completely. Otherwise if the value matches one of the values in a collection of values
  only the matching value is removed for that query parameter. All value comparisons are performed
  case insensitively."
  [query-params potential-query-param-matches value]
  (let [updated-query-params (apply dissoc query-params
                                    (get-keys-to-remove potential-query-param-matches value))]
    (reduce (fn [updated-params k]
                (update updated-params k
                 (fn [existing-values]
                   (remove (fn [existing-value]
                             (= value (str/lower-case existing-value)))
                           existing-values))))
            updated-query-params
            (get-keys-to-update potential-query-param-matches value))))

(defn- process-removal-for-field-value-tuple
  "Helper to process a subfield and value tuple to remove the appropriate term from the query
  parameters."
  ([base-field potential-qp-matches query-params field-value-tuple]
   (let [[field value] field-value-tuple
         value (str/lower-case value)
         field-name (format "%s[0][%s]" base-field (csk/->snake_case_string field))
         potential-qp-matches (or potential-qp-matches
                                  (get-potential-matching-query-params query-params field-name))]
     (remove-value-from-query-params-for-hierachical-field query-params potential-qp-matches value))))

(defn- remove-duplicate-params
  "Removes any parameters for the provided field which are exact subsets of another index for the
  same field."
  [query-params base-field]
  (let [potential-qps (filter (fn [[k _]]
                                (re-find (re-pattern base-field) k))
                              query-params)   ;; Eliminate any params we do not care about
        ;; Group the remaining params by index
        param-groups-by-index (group-by (fn [[k _]]
                                          (second (re-find #"\[\d+\]" k)))
                                        potential-qps)
        _ (println "Params grouped by index" param-groups-by-index)
        ;; Iterate (reduce) through each set of parameters
        indexes-to-remove
         (set
          (flatten
           (for [[idx qps] param-groups-by-index
                 :let [qps (util/map-keys #(str/replace % #"\[\d+\]" "")
                                          (into {} qps))
                       _ (println "CDD: qps are" qps)]]
             (keep (fn [[matching-index matching-qps]]
                     (when (and (not= idx matching-index)
                                ;; remove the index
                                (let [matching-qps (util/map-keys #(str/replace % #"\[\d+\]" "")
                                                                  (into {} matching-qps))
                                      ;; TODO change matching-qps so that all the keys are compared by ignoring the index
                                      _ (println "CDD: matching qps are:" matching-qps)
                                      _ (println "IDX" (type idx) "Matching-index" (type matching-index))]
                                  ;; another group of params fully contains this group of params
                                  (and (= qps (select-keys matching-qps (keys qps)))
                                       (or (not= qps matching-qps)
                                           (< (Integer/parseInt (format "%s" idx))
                                              (Integer/parseInt (format "%s" matching-index)))))))
                       idx))
                   param-groups-by-index))))
        query-keys-to-remove (mapcat #(keys (get param-groups-by-index %)) indexes-to-remove)]
    (if (seq query-keys-to-remove)
      (do (println "Wow I am in here and need to remove" query-keys-to-remove)
          (apply dissoc query-params query-keys-to-remove))
      query-params)))

  ;; if select-keys of another group of parameters contains all of these parameters then this one
  ;; is no longer needed.

(defn create-remove-link-for-hierarchical-field
  "Create a link that will modify the current search to no longer filter on the given hierarchical
  field-name and value. Looks for matches case insensitively.
  Field-name must be of the form <string>[<int>][<string>].

  applied-children-tuples - Tuples of [subfield term] for any applied children terms that should
                            also be removed in the remove link being generated."
  ([base-url query-params field-name ancestors-map parent-indexes value has-siblings? applied-children-tuples]
   (create-remove-link-for-hierarchical-field base-url query-params field-name ancestors-map parent-indexes value has-siblings? applied-children-tuples nil))
  ([base-url query-params field-name ancestors-map parent-indexes value has-siblings? applied-children-tuples potential-qp-matches]
   (println "Someone told me to generate a remove link for this field" field-name "index" parent-indexes "value" value)
   (let [[base-field subfield] (str/split field-name #"\[0\]")
         updated-params (reduce (partial process-removal-for-field-value-tuple base-field potential-qp-matches)
                                query-params
                                (conj applied-children-tuples [subfield value]))
         ;; TODO Remove any extraneous parameters
         updated-params (remove-duplicate-params updated-params base-field)]
     {:remove (generate-query-string base-url updated-params)})))

(defn create-link-for-hierarchical-field
  "Creates either a remove or an apply link based on whether this particular value is already
  selected within a query. Returns a map with the key being the type of link created and value is
  the link itself. The Field-name must be a hierarchical field which has the form
  <string>[<int>][<string>].

  applied-children-tuples - Tuples of [subfield term] for any applied children terms that should
                            also be removed if a remove link is being generated."
  [base-url query-params field-name ancestors-map parent-indexes value has-siblings? applied-children-tuples]
  ;; TODO - I'm thinking that I need to pass the parent index into the sub-facets function
  ;; For apply links - if none of the siblings for this parent are applied apply the
  ;; same index as the parent index. Else increment the max index by one and use that.
  ;; For remove links - if there is exactly one other sibling - Nevermind this seems bad.
  ;; TODO - new solution for apply links... if none of the siblings are applied use the
  ;; same index as the parent, otherwise increment the max index by one and duplicate all
  ;; the parent parameters with the new index and add this field with the new index.
  (if (keys (dissoc ancestors-map "category"))
    ;; Check if all ancestors are in the query-params with the parent index
    ;; TODO Get rid of the first split and just use a single regex to get the subfield
    (let [[base-field subfield] (str/split field-name #"\[\d+\]")
          subfield (second (re-find #"\[(.*)\]" subfield))
          ancestor-match-fn (fn [base-field idx ancestors subfield value]
                              ; (let [field-name (str/replace-first field-name "[0]" (format "[%s]" idx))]
                              (into {} (map (fn [[k v]]
                                              [(format "%s[%s][%s]"
                                                       base-field
                                                       idx
                                                       k)
                                               v])
                                            (assoc ancestors subfield value))))
          ancestors-to-match (map (fn [idx]
                                    (ancestor-match-fn base-field idx
                                                       (dissoc ancestors-map "category")
                                                       subfield value))
                                  parent-indexes)
          ancestors-found (map (fn [ancestor]
                                 (into {} (for [[k v] ancestor
                                                :when (= (str/lower-case v)
                                                         (some-> (get query-params k) str/lower-case))]
                                            [k v])))
                               ancestors-to-match)
          ancestors-found? (when (seq ancestors-found)
                             (= (count (first ancestors-to-match)) (apply max (map count ancestors-found))))

          ;; TODO Removing Term2 didn't remove its children for some reason
          ;; TODO Figure out the index for the matching field and value from the ancestors-found
          potential-qp-matches (->> (filter #(= (count (first ancestors-to-match)) (count %))
                                            ancestors-found)
                                    (map keys)
                                    (map first)
                                    (map (fn [qp]
                                           (str/replace-first qp #"\]\[.*\]"
                                                              (format "][%s]" subfield))))
                                    (select-keys query-params))

          ;; TODO change to multiple indexes
          ; idx (some->> potential-qp-matches
          ;              keys
          ;              first
          ;              (re-find #"\[\d+\]")
          ;              second)
          ; _ (println "IDX=" idx)
          ; child-matches (when idx
          ;                 (concat (for [[k v] applied-children-tuples]
          ;                           [(format "%s[%s][%s]" base-field idx (csk/->snake_case_string k))
          ;                            v])))
          indexes (some->> potential-qp-matches
                           keys
                           (map #(re-find #"\[\d+\]" %))
                           (map second)
                           set)
          _ (println "IDX=" indexes)
          child-matches (mapcat
                          (fn [idx]
                            (concat (for [[k v] applied-children-tuples]
                                      [(format "%s[%s][%s]" base-field idx (csk/->snake_case_string k))
                                       v])))
                          indexes)]
        (println "Child matches are" child-matches)

        (println "potential-qp-matches are" potential-qp-matches)

      (if ancestors-found?
        (println "Found all ancestors!! for" field-name "and" value)
        (println "Did not find ancestors" ancestors-to-match ancestors-found))
      ; (when has-siblings? (println "Value exists is " value-exists?))
      ;; If all ancestors are found
      (if ancestors-found?
        (create-remove-link-for-hierarchical-field
         base-url query-params field-name ancestors-map parent-indexes value has-siblings?
         applied-children-tuples (concat child-matches potential-qp-matches))
        (create-apply-link-for-hierarchical-field
         base-url query-params field-name ancestors-map parent-indexes value has-siblings?
         applied-children-tuples)))
    (let [potential-query-params (get-potential-matching-query-params query-params field-name)
          value-exists? (or (seq (get-keys-to-remove potential-query-params value))
                            (seq (get-keys-to-update potential-query-params value)))]
      (if value-exists?
        (create-remove-link-for-hierarchical-field
         base-url query-params field-name ancestors-map parent-indexes value has-siblings?
         applied-children-tuples)
        (create-apply-link-for-hierarchical-field
         base-url query-params field-name ancestors-map parent-indexes value has-siblings?
         applied-children-tuples)))))

(comment
 (count {:a 1 "b" 2 :c nil :d 45})
 (get-keys-to-remove {"science_keywords_h[0][term]" "Term1"})
 (apply max (map count [{"science_keywords_h[0][topic]" "Topic1" "science_keywords_h[0][term]" "Term1"}])))
