(ns cmr.search.services.query-execution.facets.links-helper
  "Functions to create the links that are included within v2 facets. Commonly used parameters in
  the functions include:

  base-url - root URL for the link being created.
  query-params - the query parameters from the current search as a map with a key for each
                 parameter name and the value as either a single value or a collection of values.
  field-name - the query-field that needs to be added to (or removed from) the current search.
  value - the value to apply (or remove) for the given field-name."
  (:require [camel-snake-kebab.core :as csk]
            [ring.util.codec :as codec]
            [clojure.string :as str]))

(defn- generate-query-string
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

(defn create-remove-link
  "Create a link that will modify the current search to no longer filter on the given field-name and
  value. Looks for matches case insensitively."
  [base-url query-params field-name value]
  (let [updated-query-params (remove-value-from-query-params query-params field-name value)]
    {:remove (generate-query-string base-url updated-query-params)}))

(defn- get-values-for-field
  "Returns a list of all of the values for the provided field-name. Includes values for keys of
   both field-name and field-name[]."
  [query-params field-name]
  (let [field-name-snake-case (csk/->snake_case_string field-name)]
    (reduce (fn [values-for-field field]
                (let [values (get query-params field)]
                  (if (coll? values)
                    (conj values values-for-field)
                    (if values
                      (cons values values-for-field)
                      values-for-field))))
            []
            [field-name-snake-case (str field-name-snake-case "[]")])))

(defn create-links
  "Creates either a remove or an apply link based on whether this particular value is already
  being filtered on within the provided query-params. Returns a tuple of the type of link created
  and the link itself. Looks for matches case insensitively."
  [base-url query-params field-name value]
  (let [field-name-snake-case (csk/->snake_case_string field-name)
        values-for-field (get-values-for-field query-params field-name)
        value-exists (some #{(str/lower-case value)}
                          (map #(when % (str/lower-case %)) values-for-field))]
    (if value-exists
      (create-remove-link base-url query-params field-name value)
      (create-apply-link base-url query-params field-name value))))

(defn- get-max-index-for-field-name
  "Returns the max index for the provided field-name within the query parameters.

  For example if the query parameters included fields foo[0][alpha]=bar and foo[6][beta]=zeta the
  max index of field foo would be 6. If the field is not found then -1 is returned."
  [query-params base-field]
  (let [field-regex (re-pattern (format "%s.*" base-field))
        matches (keep #(re-matches field-regex %) (keys query-params))
        indexes (keep #(second (re-matches #".*\[(\d)\].*" %)) matches)
        indexes-int (map #(Integer/parseInt %) indexes)]
    (if (seq indexes-int)
      (apply max indexes-int)
      -1)))

(defn create-hierarchical-apply-link
  "Create a link that will modify the current search to also filter by the given hierarchical
  field-name and value.
  Field-name must be of the form <string>[<int>][<string>]."
  [base-url query-params field-name value]
  (let [[base-field sub-field] (str/split field-name #"\[\d+\]")
        max-index (get-max-index-for-field-name query-params base-field)
        updated-field-name (format "%s[%d]%s" base-field (inc max-index) sub-field)
        updated-query-params (assoc query-params updated-field-name value)]
    {:apply (generate-query-string base-url updated-query-params)}))

(defn- get-keys-to-update
  "Returns a sequence of keys that have multiple values where at least one of values matches the
  passed in value. Looks for matches case insensitively."
  [query-params value]
  (let [value (str/lower-case value)]
    (flatten
      (keep (fn [[k values]]
              (when (coll? values)
                (keep (fn [single-value]
                        (when (= (str/lower-case single-value) value)
                          k))
                      values)))
            query-params))))

(defn- get-keys-to-remove
  "Returns a sequence of keys that have a single value which matches the passed in value. Looks for
  matches case insensitively."
  [query-params value]
  (let [value (str/lower-case value)]
    (keep (fn [[k v]]
            (when-not (coll? v)
              (when (= (str/lower-case v) value)
                k)))
          query-params)))

(defn- get-potential-matching-query-params
  "Returns a subset of query parameters whose keys match the provided field-name and ignoring the
  index.

  For example query parameters of foo[0][alpha]=bar, foo[0][beta]=cat, and foo[1][alpha]=dog
  would return foo[0][alpha]=bar and foo[1][alpha]=dog, but not foo[0][beta]=cat.
  Field-name must be of the form <string>[<int>][<string>]."
  [query-params field-name]
  (let [[base-field sub-field] (str/split field-name #"\[0\]")
        field-regex (re-pattern (format "%s.*%s" base-field (subs sub-field 1 (count sub-field))))
        matching-keys (keep #(re-matches field-regex %) (keys query-params))]
    (when (seq matching-keys)
      (select-keys query-params matching-keys))))

(defn create-hierarchical-remove-link
  "Create a link that will modify the current search to no longer filter on the given hierarchical
  field-name and value. Looks for matches case insensitively.
  Field-name must be of the form <string>[<int>][<string>]."
  [base-url query-params field-name value]
  (let [value (str/lower-case value)
        potential-query-params (get-potential-matching-query-params query-params field-name)
        updated-query-params (reduce (fn [updated-params k]
                                       (update updated-params k
                                         (fn [existing-values]
                                           (remove (fn [existing-value]
                                                     (= value (str/lower-case existing-value)))
                                                   existing-values))))
                                     query-params
                                     (get-keys-to-update potential-query-params value))
        updated-query-params (apply dissoc updated-query-params
                                    (get-keys-to-remove potential-query-params value))]
    {:remove (generate-query-string base-url updated-query-params)}))

(defn create-hierarchical-links
  "Creates either a remove or an apply link based on whether this particular value is already
  selected within a query. Returns a tuple of the type of link created and the link itself.
  Field-name must be of the form <string>[<int>][<string>]."
  [base-url query-params field-name value]
  (let [potential-query-params (get-potential-matching-query-params query-params field-name)
        value-exists (or (seq (get-keys-to-remove potential-query-params value))
                         (seq (get-keys-to-update potential-query-params value)))]
    (if value-exists
      (create-hierarchical-remove-link base-url query-params field-name value)
      (create-hierarchical-apply-link base-url query-params field-name value))))
