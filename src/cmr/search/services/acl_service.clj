(ns cmr.search.services.acl-service
  "Performs ACL related tasks for the search application"
  (:require [cmr.transmit.echo.tokens :as echo-tokens]
            [cmr.search.models.query :as qm]
            [cmr.acl.acl-cache :as ac]
            [clojure.string :as str]
            [cmr.acl.collection-matchers :as coll-matchers]))

(defn- context->sids
  "Returns the security identifiers (group guids and :guest or :registered) of the user identified
  by the token in the context."
  [context]
  (let [{:keys [token]} context]
    (if token
      (echo-tokens/get-current-sids context token)
      [:guest])))

(defmulti add-acl-conditions-to-query
  "Adds conditions to the query to enforce ACLs"
  (fn [context query]
    (:concept-type query)))

(defmethod add-acl-conditions-to-query :granule
  [context query]
  ;; implement this in a future sprint
  query)

(defmethod add-acl-conditions-to-query :collection
  [context query]
  (let [group-ids (map #(if (keyword? %) (name %) %) (context->sids context))
        acl-cond (qm/string-conditions :permitted-group-ids group-ids true)]
    (update-in query [:condition] #(qm/and-conds [acl-cond %]))))

(defn- ace-matches-sid?
  "Returns true if the ACE is applicable to the SID."
  [sid ace]
  (or
    (= sid (:user-type ace))
    (= sid (:group-guid ace))))

(defn- acl-matches-sids?
  "Returns true if the acl is applicable to any of the sids."
  [sids acl]
  (some (fn [sid]
          (some (partial ace-matches-sid? sid) (:aces acl)))
        sids))

(defn- get-acls-applicable-to-token
  "Retrieves the ACLs that are applicable to the current user."
  [context]
  (let [acls (ac/get-acls context)
        sids (context->sids context)]
    (filter (partial acl-matches-sids? sids) acls)))

(defmulti extract-access-value
  "Extracts access value (aka. restriction flag) from the concept."
  (fn [concept]
    (:format concept)))

(defmethod extract-access-value "application/echo10+xml"
  [concept]
  (when-let [[_ restriction-flag-str] (re-matches #"(?s).*<RestrictionFlag>(.+)</RestrictionFlag>.*"
                                                  (:metadata concept))]
    (Double. ^String restriction-flag-str)))

(defmethod extract-access-value "application/dif+xml"
  [concept]
  ;; DIF doesn't support restriction flag yet.
  nil)

(defmulti acls-match-concept?
  "Returns true if any of the acls match the concept."
  (fn [acls concept]
    (:concept-type concept)))

(defmethod acls-match-concept? :granule
  [acls concept]
  ;; Granule support will be added in a later sprint
  true)

(defmethod acls-match-concept? :collection
  [acls concept]
  (let [;; Create a equivalent umm collection that will work with collection matchers.
        coll {:entry-title (get-in concept [:extra-fields :entry-title])
              :access-value (extract-access-value concept)}]
    (some (partial coll-matchers/coll-applicable-acl? (:provider-id concept) coll) acls)))

(defn filter-concepts
  "Filters out the concepts that the current user does not have access to. Concepts are the maps
  of concept metadata as returned by the metadata db."
  [context concepts]
  (let [acls (get-acls-applicable-to-token context)
        coll-acls (filter (comp :collection-applicable :catalog-item-identity) acls)]
    ;; This assumes collection concepts for now.
    (filter (partial acls-match-concept? coll-acls) concepts)))


(comment
  ;; example concept

  (def concept {:revision-id 2,
     :deleted false,
     :format "application/echo10+xml",
     :provider-id "PROV1",
     :native-id "coll2",
     :concept-id "C1200000001-PROV1",
     :metadata
     "<Collection>
        <ShortName>short-name58</ShortName>
        <VersionId>V60</VersionId>
        <InsertTime>2012-01-11T10:00:00.000Z</InsertTime>
        <LastUpdate>2012-01-19T18:00:00.000Z</LastUpdate>
        <LongName>long-name59</LongName>
        <DataSetId>coll2</DataSetId>
        <Description/>
        <Orderable>true</Orderable>
        <Visible>true</Visible>
        <RestrictionFlag>5.0</RestrictionFlag>
      </Collection>",
     :revision-date "2014-08-19T18:53:13.499Z",
     :extra-fields {:entry-title "coll2",
                    :short-name "short-name58",
                    :version-id "V60",
                    :delete-time nil},
     :concept-type :collection})

  )

