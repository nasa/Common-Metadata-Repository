(ns cmr.metadata-db.data.oracle.sql-helper
  "Contains helper functions that are shared by providers and concepts."
  (:require
   [clj-time.format :as time-format]
   [clojure.java.jdbc :as j]
   [clojure.string :as str]
   [cmr.common.services.errors :as errors]
   [cmr.common.log :refer [debug error info trace warn]]
   [cmr.metadata-db.data.oracle.concept-tables :as ct]
   [cmr.oracle.sql-utils :as su :refer [insert values select from where with order-by desc delete as]]
   [cmr.efs.config :as efs-config]
   [cmr.efs.connection :as efs]
   [cmr.common.util :as util])
  (:import cmr.oracle.connection.OracleStore))

;;----------- HELPER FOR EFS DELETE --------------
(defn efs-concept-delete-helper
  [revision-concept-tuple]
  (let
   [{:keys [concept_id
            revision_id]} revision-concept-tuple]
    [concept_id revision_id]))

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
  ([params]
   (find-params->sql-clause params false))
  ([params or?]
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
       (if or?
         (cons `or comparisons)
         (cons `and comparisons))
       (first comparisons)))))

(defn force-delete-concept-by-params
  "Delete the concepts based on params. concept-type and provider-id must be one of the params.
  This function is moved from the concepts namespace to avoid cyclic inclusion issue."
  [db provider params]
  (let [{:keys [concept-type]} params
        params (if (:small provider)
                 (dissoc params :concept-type)
                 (dissoc params :concept-type :provider-id))
        table (ct/get-table-name provider concept-type)
        get-stmt (su/build (select [:concept-id :revision-id]
                                   (from table)
                                   (where (find-params->sql-clause params))))
        get-values (when (not (= "efs-off" (efs-config/efs-toggle)))
                     (j/query db get-stmt))
        _ (info "vales gotten from db for delete by params doall: " (doall get-values))
        stmt (su/build (delete table
                               (where (find-params->sql-clause params))))]
    (when (not (= "efs-off" (efs-config/efs-toggle)))
      (info "Time taken to delete from EFS by params: " (first (util/time-execution
                                                                (efs/delete-concepts provider concept-type (map efs-concept-delete-helper get-values))))))
    (j/execute! db stmt)))
