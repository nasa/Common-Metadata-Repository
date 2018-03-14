(ns cmr.metadata-db.data.oracle.sql-helper
  "Contains helper functions that are shared by providers and concepts."
  (:require
   [clj-time.format :as time-format]
   [clojure.java.jdbc :as j]
   [clojure.string :as str]
   [cmr.common.services.errors :as errors]
   [cmr.metadata-db.data.oracle.concept-tables :as ct]
   [cmr.oracle.sql-utils :as su :refer [insert values select from where with order-by desc delete as]])
  (:import cmr.oracle.connection.OracleStore))

(defn find-params->sql-clause
  "Converts a parameter map for finding concept types into a sql clause for inclusion in a query. The type
  of value determines the nature of the clause. If the value for a parameter is sequential then a clause
  using 'in' is generated. If the value is a map then it must contain two keys, :comparator which specifies
  what comparision operation to use, e.g. `> or `<, and :value which specifies the value for comparision.
  Any other value type results in a simple 'equals' clause.

  Examples:
             {:provider-id \"PROV1\"}               =>   `(= :provider-id \"PROV1\")

             {:provider-id [\"PROV1\", \"PROV2\"]}  =>   `(in :provider-id [\"PROV1\" \"PROV2\"])

             {:revision-id {:comparator `>, :value \"2000-01-01T10:00:00Z\"}} =>
                    `(> :revision-id \"2000-01-01T10:00:00Z\")"
  [params]
  ;; Validate parameter names as a sanity check to prevent sql injection
  (let [valid-param-name #"^[a-zA-Z][a-zA-Z0-9_\-]*$"]
    (when-let [invalid-names (seq (filter #(not (re-matches valid-param-name (name %))) (keys params)))]
      (errors/internal-error! (format "Attempting to search with invalid parameter names [%s]"
                                      (str/join ", " invalid-names)))))
  (let [comparisons (for [[k v] params]
                      (cond
                        (sequential? v) (let [val (seq v)]
                                          `(in ~k ~val))
                        (map? v) (let [{:keys [value comparator]} v]
                                   `(~comparator ~k ~value))
                        :else `(= ~k ~v)))]
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
