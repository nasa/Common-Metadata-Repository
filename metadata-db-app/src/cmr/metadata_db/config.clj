(ns cmr.metadata-db.config
  "Contains functions to retrieve metadata db specific configuration"
  (:require
   [cmr.common.concepts :as concepts]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.oracle.config :as oracle-config]
   [cmr.oracle.connection :as conn]
   [cmr.message-queue.config :as rmq-conf]))

(defconfig metadata-db-username
  "The database username"
  {:default "METADATA_DB"})

;; This value is set via profiles.clj in dev-system
(defconfig metadata-db-password
  "The database password"
  {})

(defconfig catalog-rest-db-username
  "The catalog rest db username"
  {:default "DEV_52_CATALOG_REST"})

(defn db-spec
  "Returns a db spec populated with config information that can be used to connect to oracle"
  [connection-pool-name]
  (conn/db-spec
   connection-pool-name
   (oracle-config/db-url)
   (oracle-config/db-fcf-enabled)
   (oracle-config/db-ons-config)
   (metadata-db-username)
   (metadata-db-password)))

(defconfig parallel-chunk-size
  "Gets the number of concepts that should be processed in each thread of get-concepts."
  {:default 200
   :type Long})

(defconfig result-set-fetch-size
  "Gets the setting for query fetch-size (number of rows to fetch at once)"
  {:default 200
   :type Long})

(defconfig metadata-db-nrepl-port
  "Port to listen for nREPL connections"
  {:default nil
   :parser cfg/maybe-long})

(defconfig ingest-exchange-name
  "The ingest exchange to which concept update/save messages are published."
  {:default "cmr_ingest.exchange"})

(defconfig access-control-exchange-name
  "The access control exchange to which update/save messages are published for access control data."
  {:default "cmr_access_control.exchange"})

(def concept-type->exchange-name-fn
  "Maps concept types to a function that returns the name of the exchange to publish the message to."
  (merge
   {:granule ingest-exchange-name 
    :collection ingest-exchange-name
    :tag ingest-exchange-name
    :tag-association ingest-exchange-name
    :service ingest-exchange-name
    :service-association ingest-exchange-name
    :access-group access-control-exchange-name
    :acl access-control-exchange-name
    :humanizer ingest-exchange-name
    :variable ingest-exchange-name
    :variable-association ingest-exchange-name
    :tool ingest-exchange-name
    :tool-association ingest-exchange-name
    :subscription ingest-exchange-name
    :generic-association ingest-exchange-name}
   (zipmap (concepts/get-generic-concept-types-array) (repeat ingest-exchange-name))))

(defconfig deleted-concept-revision-exchange-name
  "An exchange that will have messages passed to it whenever a concept revision is removed from
  metadata db. This was originally only intended for collections and it is messy to change the
  exchange name after it is in use, so we keep the old name even though it is no longer correct."
  {:default "cmr_deleted_collection_revision.exchange"})

(defconfig deleted-granule-exchange-name
  "An exchange that will have messages passed to it whenever a granule revision is removed
  from metadata db."
  {:default "cmr_deleted_granule.exchange"})

(defconfig publish-messages
  "This indicates whether or not messages be published to the exchange"
  {:default true :type Boolean})

(defn queue-config
  "Returns the queue configuration for the metadata db application."
  []
  (assoc (rmq-conf/default-config)
         :exchanges [(deleted-concept-revision-exchange-name)
                     (ingest-exchange-name)
                     (access-control-exchange-name)
                     (deleted-granule-exchange-name)]))
