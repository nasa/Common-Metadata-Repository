(ns cmr.system-int-test.utils.service-util
  "This contains utilities for testing services."
  (:require
   [cmr.common.mime-types :as mt]
   [clojure.test :refer [is]]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.umm-spec-service :as data-umm-s]
   [cmr.system-int-test.utils.ingest-util :as ingest-util]
   [cmr.system-int-test.system :as s]
   [cmr.umm-spec.versioning :as versioning]))

(def schema-version versioning/current-service-version)
(def content-type "application/vnd.nasa.cmr.umm+json")
(def default-opts {:accept-format :json
                   :content-type content-type})

(defn grant-all-service-fixture
  "A test fixture that grants all users the ability to create and modify services."
  [f]
  (echo-util/grant-all-service (s/context))
  (f))

(defn make-service-concept
  ([]
    (make-service-concept {}))
  ([metadata-attrs]
    (make-service-concept metadata-attrs {}))
  ([metadata-attrs attrs]
    (-> (merge {:provider-id "PROV1"} metadata-attrs)
        (data-umm-s/service-concept)
        (assoc :format (mt/with-version content-type schema-version))
        (merge attrs)))
  ([metadata-attrs attrs idx]
    (-> (merge {:provider-id "PROV1"} metadata-attrs)
        (data-umm-s/service-concept idx)
        (assoc :format (mt/with-version content-type schema-version))
        (merge attrs))))

(defn ingest-service
  "A convenience function for ingesting a service during tests."
  ([]
    (ingest-service (make-service-concept)))
  ([service-concept]
    (ingest-service service-concept default-opts))
  ([service-concept opts]
    (let [result (ingest-util/ingest-concept service-concept opts)
          attrs (select-keys service-concept
                             [:provider-id :native-id :metadata])]
      (merge result attrs))))

(defn ingest-service-with-attrs
  "Helper function to ingest a service with the given service attributes"
  ([metadata-attrs]
   (ingest-service (make-service-concept metadata-attrs)))
  ([metadata-attrs attrs]
   (ingest-service (make-service-concept metadata-attrs attrs)))
  ([metadata-attrs attrs idx]
   (ingest-service (make-service-concept metadata-attrs attrs idx))))


(defn- coll-service-association->expected-service-association
  "Returns the expected service association for the given collection concept id to
  service association mapping, which is in the format of, e.g.
  {[C1200000000-CMR 1] {:concept-id \"SA1200000005-CMR\" :revision-id 1}}."
  [coll-service-association error?]
  (let [[[coll-concept-id coll-revision-id] service-association] coll-service-association
        {:keys [concept-id revision-id]} service-association
        associated-item (if coll-revision-id
                      {:concept-id coll-concept-id :revision-id coll-revision-id}
                      {:concept-id coll-concept-id})
        errors (select-keys service-association [:errors :warnings])]
    (if (seq errors)
      (merge {:associated-item associated-item} errors)
      {:service-association {:concept-id concept-id :revision-id revision-id}
       :associated-item associated-item})))

(defn- comparable-service-associations
  "Returns the service associations with the concept_id removed from the service_association field.
  We do this to make comparision of created service associations possible, as we can't assure
  the order of which the service associations are created."
  [service-associations]
  (let [fix-sa-fn (fn [sa]
                    (if (:service-association sa)
                      (update sa :service-association dissoc :concept-id)
                      sa))]
    (map fix-sa-fn service-associations)))

(defn assert-service-association-response-ok?
  "Assert the service association response when status code is 200 is correct."
  ([coll-service-associations response]
   (assert-service-association-response-ok? coll-service-associations response true))
  ([coll-service-associations response error?]
   (let [{:keys [status body errors]} response
         expected-sas (map #(coll-service-association->expected-service-association % error?)
                           coll-service-associations)]
     (is (= [200
             (set (comparable-service-associations expected-sas))]
            [status (set (comparable-service-associations body))])))))
