(ns cmr.umm-spec.test.generate-and-parse
  "Tests roundtrip XML generation from a Clojure record and parsing it. Ensures that the same data
  is returned."
  (:require [clojure.test :refer :all]
            [cmr.umm-spec.umm-to-xml-mappings.xml-generator :as xg]
            [cmr.umm-spec.umm-to-xml-mappings.echo10 :as xm-echo10]
            [cmr.umm-spec.xml-to-umm-mappings.echo10 :as um-echo10]
            [cmr.umm-spec.umm-to-xml-mappings.iso19115-2 :as xm-iso2]
            [cmr.umm-spec.xml-to-umm-mappings.iso19115-2 :as um-iso2]
            [cmr.umm-spec.umm-to-xml-mappings.dif9 :as xm-dif9]
            [cmr.umm-spec.xml-to-umm-mappings.dif9 :as um-dif9]
            [cmr.umm-spec.umm-to-xml-mappings.dif10 :as xm-dif10]
            [cmr.umm-spec.xml-to-umm-mappings.dif10 :as um-dif10]
            [cmr.umm-spec.umm-to-xml-mappings.iso-smap :as xm-smap]
            [cmr.umm-spec.xml-to-umm-mappings.iso-smap :as um-smap]
            [cmr.umm-spec.xml-to-umm-mappings.parser :as xp]
            [cmr.umm-spec.test.expected-conversion :as expected-conversion]
            [cmr.umm-spec.models.collection :as umm-c]
            [cmr.umm-spec.models.common :as umm-cmn]
            [cmr.umm-spec.json-schema :as js]
            [cmr.umm-spec.core :as c]
            [clj-time.core :as t]
            [cmr.common.util :refer [are2]]))

(def example-record
  "This contains an example record with fields supported by all formats"
  (umm-c/map->UMM-C
    {:Platform [(umm-cmn/map->PlatformType
                  {:ShortName "Platform"
                   :Instruments [(umm-cmn/map->InstrumentType {:ShortName "Instrument"})]})]
     :ProcessingLevel (umm-c/map->ProcessingLevelType {})
     :RelatedUrl [(umm-cmn/map->RelatedUrlType {:URL ["http://google.com"]})]
     :ResponsibleOrganization [(umm-cmn/map->ResponsibilityType {:Role "RESOURCEPROVIDER"
                                                                 :Party (umm-cmn/map->PartyType {})})]
     :ScienceKeyword [(umm-cmn/map->ScienceKeywordType {:Category "cat" :Topic "top" :Term "ter"})]
     :SpatialExtent [(umm-cmn/map->SpatialExtentType {:GranuleSpatialRepresentation "NO_SPATIAL"})]

     :EntryId "short"
     :EntryTitle "The entry title V5"
     :Abstract "A very abstract collection"
     :TemporalExtent [(umm-cmn/map->TemporalExtentType {})]}))

(comment

  (println (xg/generate-xml xm-echo10/umm-c-to-echo10-xml example-record))

  (xp/parse-xml )
  )

(deftest roundtrip-gen-parse
  (are2 [metadata-format to-xml to-umm]
        (let [xml (xg/generate-xml to-xml example-record)
              validation-errors (c/validate-xml :collection metadata-format xml)
              parsed (xp/parse-xml to-umm xml)
              expected-manip-fn (expected-conversion/metadata-format->expected-conversion metadata-format)
              expected (expected-manip-fn example-record)]
          (and (is (empty? validation-errors))
               (is (= expected parsed))))
        "echo10"
        :echo10 xm-echo10/umm-c-to-echo10-xml um-echo10/echo10-xml-to-umm-c

        "dif9"
        :dif xm-dif9/umm-c-to-dif9-xml um-dif9/dif9-xml-to-umm-c

        "dif10"
        :dif10 xm-dif10/umm-c-to-dif10-xml um-dif10/dif10-xml-to-umm-c

        "iso-smap"
        :iso-smap xm-smap/umm-c-to-iso-smap-xml um-smap/iso-smap-xml-to-umm-c

        "ISO19115-2"
        :iso19115 xm-iso2/umm-c-to-iso19115-2-xml um-iso2/iso19115-2-xml-to-umm-c))

