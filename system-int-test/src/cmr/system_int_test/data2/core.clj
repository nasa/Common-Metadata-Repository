(ns cmr.system-int-test.data2.core
  "Contains helper functions for data generation and ingest for example based testing in system
  integration tests."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clj-time.core :as t]
   [clj-time.format :as f]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.test :refer [is]]
   [cmr.common.concepts :as concepts]
   [cmr.common.date-time-parser :as p]
   [cmr.common.mime-types :as mime-types]
   [cmr.common.util :as util]
   [cmr.search.results-handlers.metadata-results-handler :as metadata-results-handler]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.url-helper :as url]
   [cmr.umm-spec.legacy :as umm-legacy]
   [cmr.umm-spec.test.location-keywords-helper :as lkt]
   [cmr.umm-spec.umm-spec-core :as umm-spec]
   [cmr.umm-spec.versioning :as versioning]
   [cmr.umm.umm-core :as umm]))

(defn- item->native-id
  "Returns the native id of a UMM record."
  [item]
  (let [entry-title (some #(get item %) [:entry-title :EntryTitle])]
    ;; If the item contains an entry title it will be trimmed.
    (or (some-> entry-title string/trim)
        (some #(get item %) [:granule-ur :variable-name :native-id :LongName]))))

(def context (lkt/setup-context-for-test))

(defn- format-key->concept-format
  "Returns the format of the concept based on the format key, which could be a map with UMM version
  for UMM JSON format."
  [concept-type format-key]
  (if-let [version (:version format-key)]
    (mime-types/format->mime-type format-key)
    (if (= :umm-json format-key)
      (mime-types/format->mime-type {:format format-key
                                     :version (versioning/current-version concept-type)})
      (mime-types/format->mime-type format-key))))

(defn item->concept
  "Returns a concept map from a UMM item or tombstone. Default provider-id to PROV1 if not present."
  ([item]
   (item->concept item :echo10))
  ([item format-key]
   (let [concept-type (umm-legacy/item->concept-type item)
         format (format-key->concept-format concept-type format-key)]
     (merge {:concept-type concept-type
             :provider-id (or (:provider-id item) "PROV1")
             :native-id (or (:native-id item) (item->native-id item))
             :metadata (when-not (:deleted item)
                         (umm-legacy/generate-metadata
                          context
                          (dissoc item :provider-id) format-key))
             :format format}
            (when (:concept-id item)
              {:concept-id (:concept-id item)})
            (when (:revision-id item)
              {:revision-id (:revision-id item)})))))

(defn ingest
  "Ingests the catalog item. Returns it with concept-id, revision-id, and provider-id set on it.
  Accepts a map of some optional arguments. The options are:

  * :format - The XML Metadata format to use.
  * :token - The token to use.
  * :allow-failure? - Defaults to false. If this is false an exception will be thrown when ingest fails
  for some reason. This is useful when you expect ingest to succeed but don't want to check the results.
  Setting it to true will skip this check. Set it true when testing ingest failure cases.
  * :client-id - The client-id to use
  * :validate-keywords - true or false to indicate if the validate keywords header should be sent
  to enable keyword validation. Defaults to false.
  * :validate-umm-c  - true to enable the return of the UMM-C validation errors. Otherwise, the config values
  of return-umm-json-validation-errors and return-umm-spec-validation-errors will be used"
  ([provider-id item]
   (ingest provider-id item nil))
  ([provider-id item options]
   (let [format-key (get options :format :echo10)
         response (ingest/ingest-concept
                    (item->concept (assoc item :provider-id provider-id) format-key)
                    (select-keys options [:token
                                          :client-id
                                          :user-id
                                          :validate-keywords
                                          :validate-umm-c
                                          :accept-format
                                          :warnings]))
         status (:status response)]

     ;; This allows this to be used from many places where we don't expect a failure but if there is
     ;; one we'll be alerted immediately instead of through a side effect like searches failing.
     (when (and (not (:allow-failure? options)) (not (#{200 201} status)))
       (throw (Exception. (str "Ingest failed when expected to succeed: "
                               (pr-str response)))))

     (if (#{200 201} status)
       (assoc item
              :status status
              :provider-id provider-id
              :user-id (:user-id options)
              :concept-id (:concept-id response)
              :revision-id (:revision-id response)
              :format-key format-key
              :warnings (:warnings response))
       response))))

(defn umm-c-collection->concept
  "Returns a concept map from a UMM collection or tombstone. Default provider-id
  to PROV1 if not present."
  ([collection]
   (umm-c-collection->concept collection :echo10))
  ([collection format-key]
   (let [format (mime-types/format->mime-type format-key)]
     (merge {:concept-type :collection
             :provider-id (or (:provider-id collection) "PROV1")
             :native-id (or (:native-id collection) (item->native-id collection))
             :metadata (when-not (:deleted collection)
                         (umm-spec/generate-metadata
                          context
                          (dissoc collection :provider-id :concept-id)
                          format-key))
             :format format}
            (when (:concept-id collection)
              {:concept-id (:concept-id collection)})
            (when (:revision-id collection)
              {:revision-id (:revision-id collection)})))))

(defn ingest-umm-spec-collection
  "Ingests UMM-C collection. Returns it with concept-id, revision-id, and provider-id set on it.
  Accepts a map of some optional arguments. The options are:

  * :format - The XML Metadata format to use.
  * :token - The token to use.
  * :allow-failure? - Defaults to false. If this is false an exception will be thrown when ingest fails
  for some reason. This is useful when you expect ingest to succeed but don't want to check the results.
  Setting it to true will skip this check. Set it true when testing ingest failure cases.
  * :client-id - The client-id to use
  * :validate-keywords - true or false to indicate if the validate keywords header should be sent
  to enable keyword validation. Defaults to false.
  * :validate-umm-c  - true to enable the return of the UMM-C validation errors. Otherwise, the config values
  of return-umm-json-validation-errors and return-umm-spec-validation-errors will be used"
  ([provider-id item]
   (ingest-umm-spec-collection provider-id item nil))
  ([provider-id item options]
   (let [format-key (get options :format :echo10)
         umm-concept (umm-c-collection->concept (assoc item :provider-id provider-id) format-key)
         response (ingest/ingest-concept
                    (assoc umm-concept :provider-id provider-id)
                    (select-keys options [:token
                                          :client-id
                                          :user-id
                                          :validate-keywords
                                          :validate-umm-c
                                          :test-existing-errors
                                          :accept-format
                                          :warnings]))
         status (:status response)]

     ;; This allows this to be used from many places where we don't expect a failure but if there is
     ;; one we'll be alerted immediately instead of through a side effect like searches failing.
     (when (and (not (:allow-failure? options)) (not (#{200 201} status)))
       (throw (Exception. (str "Ingest failed when expected to succeed: "
                               (pr-str response)))))

     (if (#{200 201} status)
       (assoc item
              :status status
              :provider-id provider-id
              :user-id (:user-id options)
              :concept-id (:concept-id response)
              :revision-id (:revision-id response)
              :format-key format-key
              :warnings (:warnings response)
              :existing-errors (:existing-errors response)
              :body (:body response))
       response))))

(defn create-acl
  "Posts to the ACL CRUD endpoint with the supplied ACL body"
  [acl]
  (let [body (json/generate-string acl)
        params {:method :post
                :url (url/access-control-acls-url)
                :body body
                :headers {:Authorization "mock-echo-system-token"}
                :content-type "application/json"
                :connection-manager (s/conn-mgr)}]
    (client/request params)))

(defn umm-var->concept
  "Returns a concept map from a UMM variable item or tombstone."
  ([item]
   (umm-var->concept item :umm-json))
  ([item format-key]
   (let [format (mime-types/format->mime-type format-key)]
     (merge {:concept-type :variable
             :provider-id (or (:provider-id item) "PROV1")
             :native-id (or (:native-id item) (:Name item))
             :metadata (when-not (:deleted item)
                         (umm-spec/generate-metadata
                          context
                          (dissoc item :provider-id :concept-id :native-id)
                          format-key))
             :format format}
            (when (:concept-id item)
              {:concept-id (:concept-id item)})
            (when (:revision-id item)
              {:revision-id (:revision-id item)})))))

(defn remove-ingest-associated-keys
  "Removes the keys associated into the item from the ingest function."
  [item]
  (dissoc item :user-id :concept-id :revision-id :collection-concept-id :provider-id :status :format-key))

(defn ingest-concept-with-metadata
  "Ingest the given concept with the given metadata."
  [{:keys [provider-id concept-type format format-key metadata native-id]}]
  (let [concept  {:concept-type concept-type
                  :provider-id  provider-id
                  :native-id    (or native-id "native-id")
                  :metadata     metadata
                  :format       (or format (mime-types/format->mime-type format-key))}
        response (ingest/ingest-concept concept)]
    (if (= concept-type :granule)
      (merge (umm-legacy/parse-concept context concept) response)
      (merge (umm-spec/parse-metadata context concept) response))))

(defn ingest-concept-with-metadata-file
  "Ingest the given concept with the metadata file. The metadata file has to be located on the
  classpath. Takes a metadata-filename and an ingest parameter map.
  ingest-params must contain the following keys: provider-id concept-type, and format-key. It can
  optionally contain native-id."
  [metadata-filename ingest-params]
  (let [metadata (slurp (io/resource metadata-filename))]
    (ingest-concept-with-metadata (assoc ingest-params :metadata metadata))))

(defn mimic-ingest-retrieve-metadata-conversion
  "To mimic ingest, convert a collection to metadata in its native format then back to UMM. If
  native format is umm-json, do not do conversion since that will convert to echo10 in the
  parse-concept."
  ([collection]
   (mimic-ingest-retrieve-metadata-conversion collection (:format-key collection)))
  ([collection format-key]
   (if (= format-key :umm-json)
     collection
     (let [original-metadata (umm-legacy/generate-metadata context collection format-key)]
       (umm-legacy/parse-concept context {:metadata original-metadata
                                          :concept-type (umm-legacy/item->concept-type collection)
                                          :format (mime-types/format->mime-type format-key)})))))

(defn- item->ref-name
  "Returns the name of the reference for the given item"
  [item]
  (let [concept-type (concepts/concept-id->type (:concept-id item))]
    (if (some #{concept-type} [:subscription :service :tool :variable])
      (-> item
          :metadata
          (json/decode true)
          :Name)
      (item->native-id item))))

(defn item->ref
  "Converts an item into the expected reference"
  [item]
  (let [{:keys [concept-id revision-id deleted]} item
        ref {:name (item->ref-name item)
             :id concept-id
             :location (format "%s%s/%s" (url/location-root) (:concept-id item) revision-id)
             :revision-id revision-id}]
    (if deleted
      (-> ref
          (assoc :deleted true)
          (dissoc :location))
      ref)))

(defn expected-metadata
  "Returns the expected metadata for the given parsed umm, concept type, original-format and format-key.
   It uses umm-spec-lib to generate the metadata when original format and format key differ."
  [context concept-type original-format format-key umm-record]
  (if (or (= original-format format-key)
          (= :granule concept-type))
    (umm-legacy/generate-metadata context umm-record format-key)
    (let [metadata (umm-legacy/generate-metadata context umm-record original-format)
          umm-spec-parsed (umm-spec/parse-metadata
                           context concept-type
                           original-format metadata)
          expected (umm-spec/generate-metadata context umm-spec-parsed format-key)]
      ;; umm-json metadata is xml escaped in native response,
      ;; so we xml escape the expected metadata for comparison
      (metadata-results-handler/xml-escape-umm-json-metadata
       (mime-types/format->mime-type format-key) expected))))

(defmulti item->metadata-result
  "Converts an item into the expected metadata result"
  (fn [echo-compatible? format-key item]
    echo-compatible?))

(defmethod item->metadata-result false
  [_ format-key item]
  (let [{:keys [concept-id revision-id collection-concept-id]} item
        concept-type (concepts/concept-id->type concept-id)
        original-format (:format-key item)
        ;; Remove test core added fields so they don't end up in the expected UMM JSON
        item (remove-ingest-associated-keys item)
        ;; Translate to native format metadata and back to mimic ingest. Do not translate
        ;; when umm-json since that will translate to echo10
        parsed-item (mimic-ingest-retrieve-metadata-conversion item original-format)]
    (util/remove-nil-keys
     {:revision-id revision-id
      :concept-id concept-id
      :format format-key
      :collection-concept-id collection-concept-id
      :metadata (expected-metadata context concept-type original-format format-key parsed-item)})))

(defmethod item->metadata-result true
  [_ format-key item]
  (let [{:keys [concept-id revision-id collection-concept-id]} item
        original-format (:format-key item)
        ;; Remove test core added fields so they don't end up in the expected UMM JSON
        item (remove-ingest-associated-keys item)
        ;; Translate to native format metadata and back to mimic ingest. Do not translate
        ;; when umm-json since that will translate to echo10
        parsed-item (mimic-ingest-retrieve-metadata-conversion item original-format)]
    (if collection-concept-id
      (util/remove-nil-keys
       {:echo_granule_id concept-id
        :echo_dataset_id collection-concept-id
        :format format-key
        :metadata (umm-legacy/generate-metadata context parsed-item format-key)})
      (util/remove-nil-keys
       {:echo_dataset_id concept-id
        :format format-key
        :metadata (expected-metadata context :collection original-format format-key parsed-item)}))))

(defn- items-match?
  "Returns true if the search result items match the expected items. The argument echo-compatible?
  when set to true converts the expected items to echo compatible format before comparing with the
  result items. format-item is a function which can be used to do additional formatting on the data
  before the comparison. Default is to not do any additional formatting."
  [format-key items result-items options]
  (let [option-defaults {:echo-compatible? false :assert? false :format-item identity}
        {:keys [echo-compatible? format-item assert?]} (merge option-defaults options)
        expected (set (map #(format-item (item->metadata-result echo-compatible? format-key %)) items))
        actual (set (map #(format-item (dissoc % :granule-count)) result-items))]
    (if assert?
      (is (= (:metadata expected) (:metadata actual)))
      (= (:metadata expected) (:metadata actual)))))

(defn- echo10-coverted-iso-mends-metadata-match?
  "Returns true if the concept-ids match and the result metadata indicates it is generated by xslt"
  [items search-items]
  (let [expected-ids (map :concept-id items)
        found-ids (map :concept-id search-items)]
    (and (= (set expected-ids) (set found-ids))
         (every?
           (partial re-find
                    #"Translated from ECHO using ECHOToISO.xsl Version: 1.33")
           (map :metadata search-items)))))

(defn- remove-metadata-ids
  "Remove random uuid strings that are added to iso mends xmls during its creation so that the xmls can be compared as strings"
  [item]
  (update-in item [:metadata]
             (fn [metadata]
               (-> metadata
                   (string/replace (re-pattern "(id=)\"[a-z0-9-]*\"") "$1\"\"")
                   (string/replace (re-pattern "(xlink:href=)\"#[a-z0-9-]*\"") "$1\"\"")))))

(defn- iso-mends-metadata-results-match?
  "Returns true if the iso-mends metadata results match the expected items"
  [format-key items search-result]
  (let [{echo10-items true non-echo10-items false} (group-by #(= :echo10 (:format-key %)) items)
        search-items (:items search-result)
        echo10-concept-ids (map :concept-id echo10-items)
        echo10-search-items (filter #(some #{(:concept-id %)} echo10-concept-ids) search-items)
        non-echo10-search-items (filter #(not (some #{(:concept-id %)} echo10-concept-ids)) search-items)]
    (and (echo10-coverted-iso-mends-metadata-match? echo10-items echo10-search-items)
         (items-match? format-key non-echo10-items non-echo10-search-items
                       {:format-item (partial remove-metadata-ids)}))))

(defn metadata-results-match?
  "Returns true if the metadata results match the expected items"
  ([format-key items search-result]
   (metadata-results-match? format-key items search-result nil))
  ([format-key items search-result options]
   (items-match? format-key items (:items search-result) options)))

(defn assert-metadata-results-match
  "Returns true if the metadata results match the expected items"
  [format-key items search-result]
  (if (and (some? (:status search-result)) (not= 200 (:status search-result)))
    (is (= 200 (:status search-result)) (pr-str search-result))
    (metadata-results-match? format-key items search-result {:assert? true :echo-compatible? false})))

(defn assert-echo-compatible-metadata-results-match
  "Returns true if the metadata results match the expected items"
  [format-key items search-result]
  (metadata-results-match? format-key items search-result {:assert? true :echo-compatible? true}))

(defn echo-compatible-refs-match?
  "Returns true if the echo compatible references match the expected items"
  [items search-result]
  (= (set (map #(dissoc % :revision-id) (map item->ref items)))
     (set (map #(dissoc % :score) (:refs search-result)))))

(defn refs-match?
  "Returns true if the references match the expected items"
  [items search-result]
  (is (= (set (map item->ref items))
         ;; need to remove score etc. because it won't be available in collections
         ;; to which we are comparing
         (set (map #(dissoc % :score :granule-count :warnings) (:refs search-result))))))

(defn assert-refs-match
  "Asserts that the references match the results returned. Use this in place of refs-match? to
  get better output during tests."
  [items search-result]
  (is (= (set (map item->ref items))
         ;; need to remove score etc. because it won't be available in collections
         ;; to which we are comparing
         (set (map #(dissoc % :score :granule-count) (:refs search-result))))))

(defn refs-match-order?
  "Returns true if the references match the expected items in the order specified"
  [items search-result]
  (= (map item->ref items)
     (map #(dissoc % :score :granule-count) (:refs search-result))))

(defn unique-num
  "Creates a unique number and returns it"
  []
  (swap! (:unique-num-atom (s/system)) inc))

(defn unique-str
  "Creates a unique string and returns it"
  ([]
   (unique-str "string"))
  ([prefix]
   (str prefix (unique-num))))

(defn make-datetime
  "Creates a datetime from a number added onto a base datetime"
  ([n]
   (make-datetime n true))
  ([n to-string?]
   (when n
     (let [date (t/plus (t/date-time 2012 1 1 0 0 0)
                        (t/days n)
                        (t/hours n))]
       (if to-string?
         (p/clj-time->date-time-str date)
         date)))))

(defn make-time
  "Creates a time from a number added onto a base datetime"
  ([n]
   (make-time n true))
  ([n to-string?]
   (when n
     (let [date (t/plus (t/date-time 1970 1 1 0 0 0)
                        (t/minutes n)
                        (t/seconds n))]
       (if to-string?
         (f/unparse (f/formatters :hour-minute-second) date)
         date)))))

(defn make-date
  "Creates a date from a number added onto a base datetime"
  ([n]
   (make-date n true))
  ([n to-string?]
   (when n
     (let [date (t/plus (t/date-time 2012 1 1 0 0 0)
                        (t/days n))]
       (if to-string?
         (f/unparse (f/formatters :date) date)
         date)))))
