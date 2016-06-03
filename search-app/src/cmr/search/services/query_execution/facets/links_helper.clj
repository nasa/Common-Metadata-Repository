(ns cmr.search.services.query-execution.facets.links-helper
  "Functions to create the links that are included within v2 facets."
  (:require [camel-snake-kebab.core :as csk]
            [ring.util.codec :as codec]
            [clojure.string :as str]))

(defn- generate-query-string
  "Creates a query string from a root URL and a map of query params"
  [base-url query-params]
  (format "%s?%s" base-url (codec/form-encode query-params)))

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
  (let [param-name (str (csk/->snake_case_string field-name) "[]")
        existing-values (get query-params param-name)
        updated-query-params (if (coll? existing-values)
                                 (update query-params param-name
                                         (fn [_]
                                           (remove (fn [value]
                                                     (= (str/lower-case term) value))
                                                   (map str/lower-case existing-values))))
                                 (dissoc query-params param-name))]
    {:remove (generate-query-string base-url updated-query-params)}))


(defn create-links
  "Creates either a remove or an apply link based on whether this particular term is already
  selected within a query. Returns a tuple of the type of link created and the link itself."
  [base-url query-params field-name term]
  (let [terms-for-field (get query-params (str (csk/->snake_case_string field-name) "[]"))
        term-exists (when terms-for-field
                      (some #{(str/lower-case term)}
                            (if (coll? terms-for-field)
                                (map str/lower-case terms-for-field)
                                [(str/lower-case terms-for-field)])))]
    (if term-exists
      [:remove (create-remove-link base-url query-params field-name term)]
      [:apply (create-apply-link base-url query-params field-name term)])))

(defn create-hierarchical-apply-link
  "Create a hierarchical link to apply a term."
  [base-url query-params param-name term]
  (let [[base-field sub-field] (str/split param-name #"\[0\]")
        field-regex (re-pattern (format "%s.*" base-field))
        matches (keep #(re-matches field-regex %) (keys query-params))
        indexes (keep #(second (re-matches #".*(\d).*" %)) matches)
        indexes-int (map #(Integer/parseInt %) indexes)
        max-index (if (seq indexes-int)
                    (apply max indexes-int)
                    -1)
        updated-param-name (format "%s[%d]%s" base-field (inc max-index) sub-field)
        updated-query-params (assoc query-params updated-param-name term)]
    {:apply (generate-query-string base-url updated-query-params)}))

(defn- get-keys-to-update
  [])

(defn- get-keys-to-remove
  [])

(defn create-hierarchical-remove-link
  "Create a hierarchical link to remove a term from a query."
  [base-url query-params param-name term]
  (let [[base-field sub-field] (str/split param-name #"\[0\]")
        field-regex (re-pattern (format "%s.*" base-field))
        matching-keys (keep #(re-matches field-regex %) (keys query-params))
        potential-matching-query-params (when (seq matching-keys)
                                          (select-keys query-params matching-keys))
        values-to-remove (keep (fn [[k v]]
                                 (when (= (str/lower-case term) (str/lower-case v))
                                   k))
                               potential-matching-query-params)
        updated-query-params (if (seq values-to-remove)
                               (apply dissoc query-params values-to-remove)
                               query-params)]
        ; keys-to-update (get-keys-to-update)
        ; keys-to-remove (get-keys-to-remove)
        ; updated-query-params (update (apply dissoc query-params keys-to-remove))
        ;
        ;
        ;
        ;
        ;
        ;
        ; (for [param (keys potential-matching-query-params)
        ;        :let [existing-values (get query-params param-name)
        ;              updated-query-params (if (coll? existing-values)
        ;                                       (update query-params param-name
        ;                                               (fn [_]
        ;                                                 (remove (fn [value]
        ;                                                           (= (str/lower-case term) value))
        ;                                                         (map str/lower-case existing-values))))
        ;                                       (dissoc query-params param-name))]])]
    {:remove (generate-query-string base-url updated-query-params)}))

(defn create-hierarchical-links
  "Creates either a remove or an apply link based on whether this particular term is already
  selected within a query. Returns a tuple of the type of link created and the link itself."
  [base-url query-params field-name term]
  (let [terms-for-field (get query-params field-name)
        term-exists (when terms-for-field
                      (some #{(str/lower-case term)}
                            (if (coll? terms-for-field)
                                (map str/lower-case terms-for-field)
                                [(str/lower-case terms-for-field)])))]
    (if term-exists
      [:remove (create-hierarchical-remove-link base-url query-params field-name term)]
      [:apply (create-hierarchical-apply-link base-url query-params field-name term)])))
