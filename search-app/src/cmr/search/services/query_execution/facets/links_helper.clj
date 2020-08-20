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
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.string :as str]
   [ring.util.codec :as codec]))

(defn generate-query-string
  "Creates a query string from a root URL and a map of query params"
  [base-url query-params]
  (if (seq query-params)
    (format "%s?%s" base-url (codec/form-encode query-params))
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

(defn remove-matching-params
  "Return a collection without keys matching a provided pattern."
  [pattern query-params]
  (let [all-keys (keys query-params)
        rx (re-pattern pattern)
        to-remove (mapv (partial re-matches rx)
                        all-keys)]
    (apply (partial dissoc
                    query-params)
           to-remove)))

(defn create-remove-link
  "Create a link that will modify the current search to no longer filter on the given field-name and
  value. Looks for matches case insensitively."
  [base-url query-params field-name value]
  (let [updated-query-params (remove-value-from-query-params query-params field-name value)
        ;; pass may not be queried without a cycle
        params (if (= "cycle" field-name)
                 (remove-matching-params "passes.*" updated-query-params)
                 updated-query-params)]
    {:remove (generate-query-string base-url params)}))

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
  (let [values-for-field (get-values-for-field query-params field-name)
        value-exists (some #{(str/lower-case value)}
                           (keep #(when % (str/lower-case %)) values-for-field))]
    (if value-exists
      (create-remove-link base-url query-params field-name value)
      (create-apply-link base-url query-params field-name value))))
