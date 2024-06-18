(ns cmr.access-control.test.provider-util
  "Functions related to creating a provider. This code is very very similar to
   other test utility functions, so keeping this code in a file to make it easy
   to diff the changes. Different modules load in different order, so a common
   locations seams to hard to find.
   This code is similar to the following files:
   * cmr.metadata-db.int-test.utility
   * cmr.access-control.test.provider-util
   * cmr.system-int-test.utils.provider-util"
  (:require
   [clojure.string :as string]
   [cmr.common.util :as util]))

;; The most basic provider metadata needed to create a provider
(def basic-provider {:ProviderId "REAL_ID"
                     :DescriptionOfHolding "sample provider, no data"
                     :Organizations [{:Roles ["ORIGINATOR"]
                                      :ShortName "REAL_ID"
                                      :URLValue "https://example.gov"}]
                     :Administrators ["admin1"]
                     :MetadataSpecification {:Name "Provider"
                                             :URL "https://cdn.earthdata.nasa.gov/schemas/provider/v1.0.0"
                                             "Version" "1.0.0"}
                     ;;:Consortiums ["one" "two"]
                     })

(defn minimum-provider->metadata
  "Construct the new provider post document. Allow for the minimal set of inputs
   by the callers of reset-database-fixture and any of the CRUD functions here.
   The output is meant for metadata-db"
  [minimum-provider]
  (let [provider-id (get minimum-provider :provider-id)
        short-name provider-id
        cmr-only (:cmr-only minimum-provider)
        small (:small minimum-provider)
        consortiums (:consortiums minimum-provider)
        consortium-list (when (some? consortiums) (string/split (string/trim consortiums) #" "))
        extra-fields (dissoc minimum-provider
                             :provider-id
                             :provider-guid
                             :short-name
                             :cmr-only
                             :small
                             :consortiums)
        metadata (-> basic-provider
                     (merge extra-fields)
                     (assoc :ProviderId provider-id :Consortiums consortium-list)
                     (assoc-in [:Organizations 0 :ShortName] (if (some? short-name) short-name "blank"))
                     (util/remove-nil-keys))]
    (util/remove-nil-keys {:provider-id provider-id
                           :short-name short-name
                           :cmr-only (if (some? cmr-only) cmr-only false)
                           :small (if (some? small) small false)
                           :consortiums (when (seq consortiums) consortiums)
                           :metadata metadata})))

(defn minimum-provider->metadata-only
  "This is for ingest
   This code is an exact copy in:
   * cmr.metadata-db.int-test.utility/minimim-provider->metadata"
  [minimum-provider]
  (let [data (minimum-provider->metadata minimum-provider)
        cmr-only (:cmr-only data)
        small (:small data)
        metadata (-> (:metadata data)
                     (assoc :small small
                            :cmr-only cmr-only)
                     (util/remove-nil-keys))
        consort_count (count (:Consortiums metadata))
        metadata (if (zero? consort_count) (dissoc metadata :Consortiums) metadata)]
    metadata))
