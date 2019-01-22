(ns cmr.umm-spec.test.xml-to-umm-mappings.iso19115-2.data-contact
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.data-contact :as data-contact]))


(def contact-related-urls-example-result
  '({:NonDataCenterAffiliation nil,
     :Roles ["User Services"],
     :GroupName "National Snow and Ice Data Center",
     :ContactInformation {:ContactMechanisms (),
                          :ServiceHours nil,
                          :Addresses [{:City "Boulder",
                                       :StateProvince "Colorado",
                                       :Country "USA",
                                       :StreetAddresses (),
                                       :PostalCode nil}],
                          :RelatedUrls ({:URLContentType "DataContactURL",
                                         :Description nil,
                                         :Type "HOME PAGE",
                                         :URL "http://nsidc.org"}),
                          :ContactInstruction nil}}
    {:NonDataCenterAffiliation nil,
     :Roles ["User Services"],
     :GroupName "National Snow and Ice Data Center2",
     :ContactInformation {:ContactMechanisms (),
                          :ServiceHours nil,
                          :Addresses [{:City "Boulder",
                                       :StateProvince "Colorado",
                                       :Country "USA",
                                       :StreetAddresses (),
                                       :PostalCode nil}],
                          :RelatedUrls (),
                          :ContactInstruction nil}}))

(def data-center-contact-related-urls-example-result
  '({:ShortName "NASA National Snow and Ice Data Center Distributed Active Archive Center",
     :ContactPersons nil,
     :Roles ["ARCHIVER"],
     :LongName nil,
     :ContactInformation {:ContactMechanisms ({:Value "1 303 492 6199 x",
                                               :Type "Telephone"}
                                              {:Value "1 303 492 2468 x",
                                               :Type "Fax"}
                                              {:Value "nsidc@nsidc.org",
                                               :Type "Email"}),
                          :ServiceHours nil,
                          :Addresses [{:City "Boulder",
                                       :StateProvince "CO",
                                       :Country "USA",
                                       :StreetAddresses (),
                                       :PostalCode "80309-0449"}],
                          :RelatedUrls ({:URLContentType "DataCenterURL",
                                         :Description nil,
                                         :Type "HOME PAGE",
                                         :URL "http://nsidc.org/daac/index.html"}),
                          :ContactInstruction nil}}
    {:ShortName "NASA National Snow and Ice Data Center Distributed Active Archive Center2",
     :ContactPersons nil,
     :Roles ["ARCHIVER"],
     :LongName nil,
     :ContactInformation {:ContactMechanisms ({:Value "1 303 492 6199 x",
                                               :Type "Telephone"}
                                              {:Value "1 303 492 2468 x",
                                               :Type "Fax"}
                                              {:Value "nsidc@nsidc.org",
                                               :Type "Email"}),
                          :ServiceHours nil,
                          :Addresses [{:City "Boulder",
                                       :StateProvince "CO",
                                       :Country "USA",
                                       :StreetAddresses (),
                                       :PostalCode "80309-0449"}],
                          :RelatedUrls (),
                          :ContactInstruction nil}}))

(deftest parse-data-contact-related-urls-test
  (testing "Test if the URL doesn't exist for parsing contact data, then don't include it. If it
           does exist then include it."
    (let [metadata (slurp (io/resource "example-data/iso19115/ISOExample-contactRelatedURLs.xml"))
          sanitize? true]
      (is (= (:ContactGroups (data-contact/parse-contacts metadata sanitize?))
             contact-related-urls-example-result))
      (is (= (:DataCenters (data-contact/parse-contacts metadata sanitize?))
             data-center-contact-related-urls-example-result)))))
