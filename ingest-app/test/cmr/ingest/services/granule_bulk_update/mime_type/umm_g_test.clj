(ns cmr.ingest.services.granule-bulk-update.mime-type.umm-g-test
  "Unit tests for UMM-G additionalfile update"
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.ingest.services.granule-bulk-update.mime-type.umm-g :as umm-g]))

(def ^:private context "A fake context object" {})

(deftest update-mime-types
  ;;note we are not checking errors here, so these tests serve solely to verify
  ;;replacement logic. Proper validation is done as part of integration testing
  (testing "Add/update mime-types"
    (are3 [input-links source result]
      (is (= (umm-g/update-mime-type context {:RelatedUrls source} input-links)
             {:RelatedUrls result}))

      "Add some mime types to links"
      [{:URL "www.example.com/1" :MimeType "application/gzip"}
       {:URL "www.example.com/2" :MimeType "application/tar"}]
      [{:URL "www.example.com/1"
        :Type "GET DATA VIA DIRECT ACCESS"
        :Subtype "MAP"}
       {:URL "www.example.com/2"
        :Type "GET DATA VIA DIRECT ACCESS"
        :Subtype "DIRECT DOWNLOAD"}]
      [{:URL "www.example.com/1"
        :Type "GET DATA VIA DIRECT ACCESS"
        :Subtype "MAP"
        :MimeType "application/gzip"}
       {:URL "www.example.com/2"
        :Type "GET DATA VIA DIRECT ACCESS"
        :Subtype "DIRECT DOWNLOAD"
        :MimeType "application/tar"}]

      "Update mime types in some links"
      [{:URL "www.example.com/1" :MimeType "application/pdf"}
       {:URL "www.example.com/2" :MimeType "application/json"}]
      [{:URL "www.example.com/1"
        :Type "GET DATA VIA DIRECT ACCESS"
        :Subtype "MAP"
        :MimeType "application/gzip"}
       {:URL "www.example.com/2"
        :Type "GET DATA VIA DIRECT ACCESS"
        :Subtype "DIRECT DOWNLOAD"
        :MimeType "application/tar"}]
      [{:URL "www.example.com/1"
        :Type "GET DATA VIA DIRECT ACCESS"
        :Subtype "MAP"
        :MimeType "application/pdf"}
       {:URL "www.example.com/2"
        :Type "GET DATA VIA DIRECT ACCESS"
        :Subtype "DIRECT DOWNLOAD"
        :MimeType "application/json"}]

      "Update one link, add to another"
      [{:URL "www.example.com/1" :MimeType "application/pdf"}
       {:URL "www.example.com/2" :MimeType "application/json"}]
      [{:URL "www.example.com/1"
        :Type "GET DATA VIA DIRECT ACCESS"
        :Subtype "MAP"}
       {:URL "www.example.com/2"
        :Type "GET DATA VIA DIRECT ACCESS"
        :Subtype "DIRECT DOWNLOAD"
        :MimeType "application/tar"}]
      [{:URL "www.example.com/1"
        :Type "GET DATA VIA DIRECT ACCESS"
        :Subtype "MAP"
        :MimeType "application/pdf"}
       {:URL "www.example.com/2"
        :Type "GET DATA VIA DIRECT ACCESS"
        :Subtype "DIRECT DOWNLOAD"
        :MimeType "application/json"}])))
