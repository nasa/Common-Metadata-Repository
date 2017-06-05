(ns cmr.system-int-test.utils.service-util
  "This contains utilities for testing services."
  (:require
   [clojure.string :as string]
   [clojure.test :refer [is]]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest-util]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.transmit.echo.tokens :as tokens]
   [cmr.transmit.service :as transmit-service]))

(defn assert-convert-kebab-case
  [data]
  ; XXX This can be uncommented once service support is added to the metadata db
  ;     See CMR-
  ; (util/assert-convert-kebab-case [:concept-id :revision-id
  ;                                  :service-name :originator-id]
  ;                                 data))
  true)

(defn grant-all-service-fixture
  "A test fixture that grants all users the ability to create and modify
  services."
  [f]
  (echo-util/grant-all-service (s/context))
  (f))

(def sample-service
  {:Name "A name"
   :Description "A UMM-S description"
   :Format ""
   :Type "Web Mapping Service"
   :Version "1.1.1"})

(defn make-service
  "Makes a valid service based on the given input."
  ([]
   (make-service nil))
  ([attrs]
   (merge sample-service attrs))
  ([index attrs]
   (merge
    sample-service
    {:Name (str "Name" index)
     :Version (str "V" index)
     :Description (str "UMM-S description " index)}
    attrs)))

(defn create-service
  "Creates a service."
  ([token service]
   (create-service token service nil))
  ([token service options]
   (let [options (merge {:raw? true :token token} options)]
     (ingest-util/parse-map-response
      (transmit-service/create-service (s/context) service options)
      assert-convert-kebab-case))))

(defn update-service
  "Updates a service."
  ([token service]
   (update-service token (:service-name service) service nil))
  ([token service-name service]
   (update-service token service-name service nil))
  ([token service-name service options]
   (let [options (merge {:raw? true :token token} options)]
     (ingest-util/parse-map-response
      (transmit-service/update-service (s/context) service-name service options)
      assert-convert-kebab-case))))

(defn save-service
  "A helper function for creating or updating services for search tests.

   If the service does not have a :concept-id, it saves it. If the service has
   a :concept-id, it updates the service. Returns the saved service along
   with :concept-id, :revision-id, :errors, and :status."
  [token service]
  (let [service-to-save (select-keys service [:service-name :description :revision-date])
        response (if-let [concept-id (:concept-id service)]
                   (update-service token (:service-name service) service-to-save)
                   (create-service token service-to-save))
        service (-> service
                    (update :service-name string/lower-case)
                    (into (select-keys response [:status :errors :concept-id :revision-id])))]
    (if (= (:revision-id service) 1)
      ;; Get the originator id for the service
      (assoc service :originator-id (tokens/get-user-id (s/context) token))
      service)))

(defn assert-service-saved
  "Checks that a service was persisted correctly in metadata db. The service
  should already have originator id set correctly. The user-id indicates which
  user updated this revision."
  [service user-id concept-id revision-id]
  (let [concept (mdb/get-concept concept-id revision-id)]
    (is (= {:concept-type :service
            :native-id (:service-name service)
            :provider-id "CMR"
            :format mt/edn
            :metadata (pr-str service)
            :user-id user-id
            :deleted false
            :concept-id concept-id
            :revision-id revision-id}
           (dissoc concept :revision-date :transaction-id)))))

(defn sort-expected-services
  "Sorts the services using the expected default sort key."
  [services]
  (sort-by identity
           (fn [t1 t2]
             (compare (:service-name t1) (:service-name t2)))
           services))

(def service-names-in-expected-response
  [:concept-id :revision-id :service-name :description :originator-id])
