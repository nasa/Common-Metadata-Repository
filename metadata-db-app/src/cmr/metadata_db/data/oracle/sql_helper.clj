(ns cmr.metadata-db.data.oracle.sql-helper
  "Contains helper functions that are shared by providers and concepts."
  (:require [cmr.common.services.errors :as errors]
            [clojure.string :as str]
            [clojure.java.jdbc :as j]
            [cmr.metadata-db.data.oracle.concept-tables :as ct]
            [cmr.metadata-db.data.oracle.sql-utils :as su :refer [insert values select from where with order-by desc delete as]])
  (:import cmr.oracle.connection.OracleStore))

(defn find-params->sql-clause
  "Converts a parameter map for finding concept types into a sql clause for inclusion in a query."
  [params]
  ;; Validate parameter names as a sanity check to prevent sql injection
  (let [valid-param-name #"^[a-zA-Z][a-zA-Z0-9_\-]*$"]
    (when-let [invalid-names (seq (filter #(not (re-matches valid-param-name (name %))) (keys params)))]
      (errors/internal-error! (format "Attempting to search with invalid parameter names [%s]"
                                      (str/join ", " invalid-names)))))
  (let [comparisons (for [[k v] params]
                      `(= ~k ~v))]
    (if (> (count comparisons) 1)
      (cons `and comparisons)
      (first comparisons))))

(defn force-delete-concept-by-params
  "Delete the concepts based on params. concept-type and provider-id must be one of the params.
  This function is moved from the concepts namespace to avoid cyclic inclusion issue."
  [db provider params]
  (let [{:keys [concept-type]} params
        params (if (:small provider)
                 (dissoc params :concept-type)
                 (dissoc params :concept-type :provider-id))
        table (ct/get-table-name provider concept-type)
        stmt (su/build (delete table
                               (where (find-params->sql-clause params))))]
    (j/execute! db stmt)))