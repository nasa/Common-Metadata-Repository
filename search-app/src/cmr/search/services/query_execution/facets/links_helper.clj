(ns cmr.search.services.query-execution.facets.links-helper
  "Functions to create the links that are included within v2 facets."
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
  "Create a link to apply a term."
  [base-url query-params field-name term]
  (let [param-name (str (csk/->snake_case_string field-name) "[]")
        existing-values (flatten [(get query-params param-name)])
        updated-query-params (assoc query-params param-name (remove nil? (conj existing-values term)))]
    {:apply (generate-query-string base-url updated-query-params)}))

(defn create-remove-link
  "Create a link to remove a term from a query."
  [base-url query-params field-name term]
  (let [term (str/lower-case term)
        field-name-snake-case (csk/->snake_case_string field-name)
        updated-query-params
         (reduce
          (fn [query-params param-name]
             (let [existing-values (get query-params param-name)]
               (if (coll? existing-values)
                  (update query-params param-name
                          (fn [_]
                            (remove (fn [value]
                                      (= (str/lower-case value) term))
                                    existing-values)))
                  (dissoc query-params param-name))))
          query-params
          [field-name-snake-case (str field-name-snake-case "[]")])]
    {:remove (generate-query-string base-url updated-query-params)}))

(defn create-links
  "Creates either a remove or an apply link based on whether this particular term is already
  selected within a query. Returns a tuple of the type of link created and the link itself."
  [base-url query-params field-name term]
  (let [field-name-snake-case (csk/->snake_case_string field-name)
        terms-for-field (reduce (fn [terms-for-field field]
                                  (let [terms (get query-params field)]
                                    (if (coll? terms)
                                      (conj terms terms-for-field)
                                      (if terms
                                        (cons terms terms-for-field)
                                        terms-for-field))))
                                []
                                [field-name-snake-case (str field-name-snake-case "[]")])
        term-exists (some #{(str/lower-case term)}
                          (map #(when % (str/lower-case %)) terms-for-field))]
    (if term-exists
      [:remove (create-remove-link base-url query-params field-name term)]
      [:apply (create-apply-link base-url query-params field-name term)])))

(defn create-hierarchical-apply-link
  "Create a hierarchical link to apply a term."
  [base-url query-params param-name term]
  (let [[base-field sub-field] (str/split param-name #"\[0\]")
        field-regex (re-pattern (format "%s.*" base-field))
        matches (keep #(re-matches field-regex %) (keys query-params))
        indexes (keep #(second (re-matches #".*\[(\d)\].*" %)) matches)
        indexes-int (map #(Integer/parseInt %) indexes)
        max-index (if (seq indexes-int)
                    (apply max indexes-int)
                    -1)
        updated-param-name (format "%s[%d]%s" base-field (inc max-index) sub-field)
        updated-query-params (assoc query-params updated-param-name term)]
    {:apply (generate-query-string base-url updated-query-params)}))

(defn- get-keys-to-update
  "Returns a sequence of keys that have multiple values at least one of which needs to be removed"
  [query-params matching-term]
  (let [matching-term (str/lower-case matching-term)]
    (flatten
      (keep (fn [[k values]]
              (when (coll? values)
                (keep (fn [single-value]
                        (when (= (str/lower-case single-value) matching-term)
                          k))
                      values)))
            query-params))))

(defn- get-keys-to-remove
  "Returns a sequence of keys that have a single value which needs to be removed"
  [query-params matching-term]
  (let [matching-term (str/lower-case matching-term)]
    (keep (fn [[k v]]
            (when-not (coll? v)
              (when (= (str/lower-case v) matching-term)
                k)))
          query-params)))

(defn create-hierarchical-remove-link
  "Create a hierarchical link to remove a term from a query."
  [base-url query-params param-name term]
  (let [term (str/lower-case term)
        [base-field sub-field] (str/split param-name #"\[0\]")
        field-regex (re-pattern (format "%s.*%s" base-field (subs sub-field 1 (count sub-field))))
        matching-keys (keep #(re-matches field-regex %) (keys query-params))
        potential-query-params (when (seq matching-keys)
                                 (select-keys query-params matching-keys))
        updated-query-params (reduce (fn [updated-params k]
                                       (update updated-params k
                                         (fn [existing-values]
                                           (remove (fn [value]
                                                     (= term (str/lower-case value)))
                                                   existing-values))))
                                     query-params
                                     (get-keys-to-update potential-query-params term))
        updated-query-params (apply dissoc updated-query-params
                                    (get-keys-to-remove potential-query-params term))]
    {:remove (generate-query-string base-url updated-query-params)}))

(defn create-hierarchical-links
  "Creates either a remove or an apply link based on whether this particular term is already
  selected within a query. Returns a tuple of the type of link created and the link itself."
  [base-url query-params field-name term]
  (let [[base-field sub-field] (str/split field-name #"\[0\]")
        field-regex (re-pattern (format "%s.*%s" base-field (subs sub-field 1 (count sub-field))))
        matching-keys (keep #(re-matches field-regex %) (keys query-params))
        potential-query-params (when (seq matching-keys)
                                 (select-keys query-params matching-keys))
        term-exists (or (seq (get-keys-to-remove potential-query-params term))
                        (seq (get-keys-to-update potential-query-params term)))]
    (if term-exists
      [:remove (create-hierarchical-remove-link base-url query-params field-name term)]
      [:apply (create-hierarchical-apply-link base-url query-params field-name term)])))
