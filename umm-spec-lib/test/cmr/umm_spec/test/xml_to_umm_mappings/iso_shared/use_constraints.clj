(ns cmr.umm-spec.test.xml-to-umm-mappings.iso-shared.use-constraints
  "Tests to verify that ISO and smap constraints are parsed correctly."
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.umm-spec.models.umm-collection-models :as umm-coll-models]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.use-constraints :as uc]
   [cmr.umm-spec.xml-to-umm-mappings.iso-smap :as smap]
   [cmr.umm-spec.xml-to-umm-mappings.iso19115-2 :as iso]))

; These four fields will be used in all of the test in this namespace.
(def base-iso (slurp (io/resource "example-data/iso19115/artificial_test_data_2.xml")))
(def base-smap (slurp (io/resource "example-data/iso-smap/artificial_test_data.xml")))
(def iso-xpath iso/constraints-xpath)
(def smap-xpath smap/constraints-xpath)

(deftest parse-ac-test
  (testing "Parsing access constraints by regex"

    (are3 [test-iso xpath add-xpath value? expected]
      (is (= expected (uc/parse-ac test-iso xpath add-xpath value?)))

      "Test no constraints for access constraints value MENDS using old key"
      base-iso iso-xpath uc/other-constraints-xpath uc/old-access-value nil

      "Test no constraints for access constraints value MENDS using new key"
      base-iso iso-xpath uc/other-constraints-xpath uc/new-access-value nil

      "Test no constraints for access constraints value SMAP using old key"
      base-smap smap-xpath uc/other-constraints-xpath uc/old-access-value nil

      "Test no constraints for access constraints value SMAP using new key"
      base-smap smap-xpath uc/other-constraints-xpath uc/new-access-value nil

      "Test wrong key for access constraints value"
      (string/replace base-iso
                      #"<\/gmd:descriptiveKeywords>[ *|\n|\r]*<gmd:aggregationInfo>"
                      "</gmd:descriptiveKeywords>
                       <gmd:resourceConstraints>
                         <gmd:MD_LegalConstraints>
                           <gmd:otherConstraints>
                             <gco:CharacterString>Restriction Flags: 15</gco:CharacterString>
                           </gmd:otherConstraints>
                         </gmd:MD_LegalConstraints>
                       </gmd:resourceConstraints>
                       <gmd:aggregationInfo>")
      iso-xpath
      uc/other-constraints-xpath
      uc/old-access-value
      nil

      "Test Restriction Flag constraints with space after colon for access constraints value"
      (string/replace base-iso
                      #"<\/gmd:descriptiveKeywords>[ *|\n|\r]*<gmd:aggregationInfo>"
                      "</gmd:descriptiveKeywords>
                       <gmd:resourceConstraints>
                         <gmd:MD_LegalConstraints>
                           <gmd:otherConstraints>
                             <gco:CharacterString>Restriction Flag: 15</gco:CharacterString>
                           </gmd:otherConstraints>
                         </gmd:MD_LegalConstraints>
                       </gmd:resourceConstraints>
                       <gmd:aggregationInfo>")
      iso-xpath
      uc/other-constraints-xpath
      uc/old-access-value
      " 15"

      "Test Access Constraints Value constraints without space for access constraints value"
      (string/replace base-iso
                      #"<\/gmd:descriptiveKeywords>[ *|\n|\r]*<gmd:aggregationInfo>"
                      "</gmd:descriptiveKeywords>
                       <gmd:resourceConstraints>
                         <gmd:MD_LegalConstraints>
                           <gmd:otherConstraints>
                             <gco:CharacterString>Access Constraints Value:15</gco:CharacterString>
                           </gmd:otherConstraints>
                         </gmd:MD_LegalConstraints>
                       </gmd:resourceConstraints>
                       <gmd:aggregationInfo>")
      iso-xpath
      uc/other-constraints-xpath
      uc/new-access-value
      "15"

      "Test Access Constraints Description Restriction Comment using the use limitation xpath."
      (string/replace base-iso
                      #"<\/gmd:descriptiveKeywords>[ *|\n|\r]*<gmd:aggregationInfo>"
                      "</gmd:descriptiveKeywords>
                       <gmd:resourceConstraints>
                         <gmd:MD_LegalConstraints>
                           <gmd:useLimitation>
                             <gco:CharacterString>Restriction Comment: description</gco:CharacterString>
                           </gmd:useLimitation>
                         </gmd:MD_LegalConstraints>
                       </gmd:resourceConstraints>
                       <gmd:aggregationInfo>")
      iso-xpath
      uc/use-limitation-xpath
      uc/old-access-desc
      " description"

      "Test Access Constraints Description using the use limitation xpath."
      (string/replace base-iso
                      #"<\/gmd:descriptiveKeywords>[ *|\n|\r]*<gmd:aggregationInfo>"
                      "</gmd:descriptiveKeywords>
                       <gmd:resourceConstraints>
                         <gmd:MD_LegalConstraints>
                           <gmd:useLimitation>
                             <gco:CharacterString>Access Constraints Description:description</gco:CharacterString>
                           </gmd:useLimitation>
                         </gmd:MD_LegalConstraints>
                       </gmd:resourceConstraints>
                       <gmd:aggregationInfo>")
      iso-xpath
      uc/use-limitation-xpath
      uc/new-access-desc
      "description"

      "Test Access Constraints Description using the use other constraints xpath."
      (string/replace base-smap
                      #"<\/gmd:descriptiveKeywords>[ *|\n|\r]*<gmd:aggregationInfo>"
                      "</gmd:descriptiveKeywords>
                       <gmd:resourceConstraints>
                         <gmd:MD_LegalConstraints>
                           <gmd:otherConstraints>
                             <gco:CharacterString>Access Constraints Description:  description</gco:CharacterString>
                           </gmd:otherConstraints>
                         </gmd:MD_LegalConstraints>
                       </gmd:resourceConstraints>
                       <gmd:aggregationInfo>")
      smap-xpath
      uc/other-constraints-xpath
      uc/new-access-desc
      "  description")))

(deftest parse-access-constraints-test
  (testing "Parsing access constraints"

    (are3 [test-iso xpath sanitize? expected]
      (is (= expected (uc/parse-access-constraints test-iso xpath sanitize?)))

      "Test no constraints for access constraints value MENDS using old key"
      base-iso iso-xpath true nil

      "Test Access Constraints Value constraints without space for access constraints value.
       Description will be Not provided since sanitize? is true."
      (string/replace base-iso
                      #"<\/gmd:descriptiveKeywords>[ *|\n|\r]*<gmd:aggregationInfo>"
                      "</gmd:descriptiveKeywords>
                       <gmd:resourceConstraints>
                         <gmd:MD_LegalConstraints>
                           <gmd:otherConstraints>
                             <gco:CharacterString>Access Constraints Value:15</gco:CharacterString>
                           </gmd:otherConstraints>
                         </gmd:MD_LegalConstraints>
                       </gmd:resourceConstraints>
                       <gmd:aggregationInfo>")
      iso-xpath
      true
      {:Description "Not provided" :Value 15.0}

      "Test Access Constraints Value constraints using smap for Restriction Flag.
       Restriction Flag will be used and the other will be ignored.
       Description will be nil since sanitize? is false."
      (string/replace base-smap
                      #"<\/gmd:descriptiveKeywords>[ *|\n|\r]*<gmd:aggregationInfo>"
                      "</gmd:descriptiveKeywords>
                       <gmd:resourceConstraints>
                         <gmd:MD_LegalConstraints>
                           <gmd:otherConstraints>
                             <gco:CharacterString>Restriction Flag: 15</gco:CharacterString>
                           </gmd:otherConstraints>
                           <gmd:otherConstraints>
                             <gco:CharacterString>Access Constraints Value: 20</gco:CharacterString>
                           </gmd:otherConstraints>
                         </gmd:MD_LegalConstraints>
                       </gmd:resourceConstraints>
                       <gmd:aggregationInfo>")
      smap-xpath
      false
      {:Value 15.0 :Description nil}

      "Test Access Constraints Value constraints using smap for Restriction Flag.
       Restriction Flag will be used with a non number Because neither Description nor Value exist
       nil is returned."
      (string/replace base-smap
                      #"<\/gmd:descriptiveKeywords>[ *|\n|\r]*<gmd:aggregationInfo>"
                      "</gmd:descriptiveKeywords>
                       <gmd:resourceConstraints>
                         <gmd:MD_LegalConstraints>
                           <gmd:otherConstraints>
                             <gco:CharacterString>Restriction Flag: abc</gco:CharacterString>
                           </gmd:otherConstraints>
                         </gmd:MD_LegalConstraints>
                       </gmd:resourceConstraints>
                       <gmd:aggregationInfo>")
      smap-xpath
      true
      nil

      "Test Access Constraints Description constraints using smap and both useLimitation and otherConstraints.
       useLimitation Restriction Comment will be the answer."
      (string/replace base-smap
                      #"<\/gmd:descriptiveKeywords>[ *|\n|\r]*<gmd:aggregationInfo>"
                      "</gmd:descriptiveKeywords>
                       <gmd:resourceConstraints>
                         <gmd:MD_LegalConstraints>
                           <gmd:useLimitation>
                             <gco:CharacterString>Access Constraints Description:description1</gco:CharacterString>
                           </gmd:useLimitation>
                           <gmd:useLimitation>
                             <gco:CharacterString>Restriction Comment:description2</gco:CharacterString>
                           </gmd:useLimitation>
                           <gmd:otherConstraints>
                             <gco:CharacterString>Access Constraints Description:  description3</gco:CharacterString>
                           </gmd:otherConstraints>
                         </gmd:MD_LegalConstraints>
                       </gmd:resourceConstraints>
                       <gmd:aggregationInfo>")
      smap-xpath
      true
      {:Description "description2" :Value nil}

      "Test Access Constraints Description constraints using smap and using two different resource
       constraint sections. UseLimitation Restriction Comment will be the answer."
      (string/replace base-smap
                      #"<\/gmd:descriptiveKeywords>[ *|\n|\r]*<gmd:aggregationInfo>"
                      "</gmd:descriptiveKeywords>
                       <gmd:resourceConstraints>
                         <gmd:MD_LegalConstraints>
                           <gmd:useLimitation>
                             <gco:CharacterString>Access Constraints Description: description1</gco:CharacterString>
                           </gmd:useLimitation>
                         </gmd:MD_LegalConstraints>
                       </gmd:resourceConstraints>
                       <gmd:resourceConstraints>
                         <gmd:MD_LegalConstraints>
                           <gmd:useLimitation>
                             <gco:CharacterString>Restriction Comment: description2</gco:CharacterString>
                           </gmd:useLimitation>
                         </gmd:MD_LegalConstraints>
                       </gmd:resourceConstraints>
                       <gmd:aggregationInfo>")
      smap-xpath
      true
      {:Description " description2" :Value nil})))

(deftest parse-use-constraints-test
  (testing "Parsing use constraints"

    (are3 [test-iso xpath sanitize? expected]
      (is (= expected (uc/parse-use-constraints test-iso xpath sanitize?)))

      "Test no constraints for use constraints."
      base-iso iso-xpath true nil

      "Test FreeAndOpenData value of true"
      (string/replace base-smap
                      #"<\/gmd:descriptiveKeywords>[ *|\n|\r]*<gmd:aggregationInfo>"
                      "</gmd:descriptiveKeywords>
                       <gmd:resourceConstraints>
                         <gmd:MD_LegalConstraints>
                           <gmd:otherConstraints>
                             <gco:CharacterString>FreeAndOpenData: true</gco:CharacterString>
                           </gmd:otherConstraints>
                         </gmd:MD_LegalConstraints>
                       </gmd:resourceConstraints>
                       <gmd:aggregationInfo>")
      smap-xpath
      true
      (umm-coll-models/map->UseConstraintsType
        {:FreeAndOpenData true})

      "Test FreeAndOpenData value of false"
      (string/replace base-smap
                      #"<\/gmd:descriptiveKeywords>[ *|\n|\r]*<gmd:aggregationInfo>"
                      "</gmd:descriptiveKeywords>
                       <gmd:resourceConstraints>
                         <gmd:MD_LegalConstraints>
                           <gmd:otherConstraints>
                             <gco:CharacterString>FreeAndOpenData: false</gco:CharacterString>
                           </gmd:otherConstraints>
                         </gmd:MD_LegalConstraints>
                       </gmd:resourceConstraints>
                       <gmd:aggregationInfo>")
      smap-xpath
      true
      (umm-coll-models/map->UseConstraintsType
        {:FreeAndOpenData false})

      "Test FreeAndOpenData value of something other than true or false"
      (string/replace base-smap
                      #"<\/gmd:descriptiveKeywords>[ *|\n|\r]*<gmd:aggregationInfo>"
                      "</gmd:descriptiveKeywords>
                       <gmd:resourceConstraints>
                         <gmd:MD_LegalConstraints>
                           <gmd:otherConstraints>
                             <gco:CharacterString>FreeAndOpenData: hello</gco:CharacterString>
                           </gmd:otherConstraints>
                         </gmd:MD_LegalConstraints>
                       </gmd:resourceConstraints>
                       <gmd:aggregationInfo>")
      smap-xpath
      true
      nil)))
