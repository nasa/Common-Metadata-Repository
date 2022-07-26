(ns cmr.umm-spec.test.xml-to-umm-mappings.iso-shared.shared-iso-parsing-util
  (:require
   [clojure.test :refer :all]
   [cmr.common.test.test-util :as tu]
   [cmr.common.util :refer [are3]]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.shared-iso-parsing-util :as util]))

(deftest description-string-parsing
  (testing "Parsing given string and converting it to a string map"
    (are3 [string regex expected]
      (is (= expected (util/convert-iso-description-string-to-string-map string regex)))

      "ISO MENDS Collection Description string"
      "URLContentType: DistributionURL Description: NASA's newest search and order tool for subsetting, reprojecting, and reformatting data. Type: GET DATA Subtype: Earthdata Search"
      (re-pattern "URLContentType:|Description:|Type:|Subtype:|Checksum:")
      {"Type" "GET DATA",
       "URLContentType" "DistributionURL",
       "Description" "NASA's newest search and order tool for subsetting, reprojecting, and reformatting data.",
       "Subtype" "Earthdata Search"}

      "String with odd and nil values"
      ":: URLContentType:nil Checksum: \"nil\" Description: NASA's newest lawnmower ascii art: __\\.-.,,,, Type: SELF PROPELLED Subtype: Earthdata Search"
      (re-pattern "URLContentType:|Description:|Type:|Subtype:|Checksum:")
      {"Checksum" "\"nil\"",
       "Description" "NASA's newest lawnmower ascii art: __\\.-.,,,,",
       "Type" "SELF PROPELLED",
       "Subtype" "Earthdata Search"}

      "String with No pattern match"
      "This is just a plain description."
      (re-pattern "URLContentType:|Description:|Type:|Subtype:|Checksum:")
      {}))

  (testing "Parsing given string and converting it to a map where the key is a key and not a string"
    (are3 [string regex expected]
      (is (= expected (util/convert-iso-description-string-to-map string regex)))

      "ISO MENDS Collection Description string"
      "URLContentType: DistributionURL Description: NASA's newest search and order tool for subsetting, reprojecting, and reformatting data. Type: GET DATA Subtype: Earthdata Search"
      (re-pattern "URLContentType:|Description:|Type:|Subtype:|Checksum:")
      {:Type "GET DATA",
       :URLContentType "DistributionURL",
       :Description "NASA's newest search and order tool for subsetting, reprojecting, and reformatting data.",
       :Subtype "Earthdata Search"}

      "String with odd and nil values"
      ":: URLContentType:nil Checksum: \"nil\" Description: NASA's newest lawnmower ascii art: __\\.-.,,,, Type: SELF PROPELLED Subtype: Earthdata Search"
      (re-pattern "URLContentType:|Description:|Type:|Subtype:|Checksum:")
      {:Checksum "\"nil\"",
       :Description "NASA's newest lawnmower ascii art: __\\.-.,,,,",
       :Type "SELF PROPELLED",
       :Subtype "Earthdata Search"}))

  (testing "Parsing given string and converting it to a string map"
    (are3 [map number-key-list expected]
      (is (= expected (util/convert-select-values-from-string-to-number map number-key-list)))

      "Testing the success case where some values need to be converted."
      {:Unit "Meters", :YDimension "10", :XDimension "10" :somethingesle "ok"}
      '(:XDimension :MinimumXDimension :MaximumXDimension :YDimension :MinimumYDimension :MaximumYDimension)
      {:Unit "Meters", :YDimension 10, :XDimension 10 :somethingesle "ok"}

      "Testing the success case where some values need to be converted."
      {:Unit "Meters", :YDimension "0.2", :XDimension "0.5" :somethingesle "ok"}
      '(:XDimension :MinimumXDimension :MaximumXDimension :YDimension :MinimumYDimension :MaximumYDimension)
      {:Unit "Meters", :YDimension 0.2, :XDimension 0.5 :somethingesle "ok"}

      "Testing the case where some values need to be converted and some are nil."
      {:Unit "Meters", :YDimension nil, :XDimension "0.5" :somethingesle "ok"}
      '(:XDimension :MinimumXDimension :MaximumXDimension :YDimension :MinimumYDimension :MaximumYDimension)
      {:Unit "Meters", :YDimension nil, :XDimension 0.5 :somethingesle "ok"}

      "Test when the number-key-list is nil"
      {:Unit "Meters", :YDimension "10", :XDimension "10" :somethingesle "ok"}
      nil
      {:Unit "Meters", :YDimension "10", :XDimension "10" :somethingesle "ok"}

      "Test when the map is empty"
      {}
      nil
      nil

      "Test when the map is nil"
      nil
      nil
      nil))

  (testing "Testing the ability to handle parsing an invalid number"
    (let [map {:Unit "Meters", :YDimension "10", :XDimension "NAN"}
          number-key-list '(:XDimension :MinimumXDimension :MaximumXDimension :YDimension :MinimumYDimension :MaximumYDimension)]
      (tu/assert-exception-thrown-with-errors
       :invalid-data
       ["Error parsing the field :XDimension with value NAN"]
       (util/convert-select-values-from-string-to-number map number-key-list))))

  (testing "Parsing given string and converting it to a map where the key is a key and not a string.
            also converting any expected numbers to a number."
    (are3 [string regex number-key-list expected]
      (is (= expected (util/convert-iso-description-string-to-map string regex number-key-list)))

      "ISO MENDS Collection Description string"
      "URLContentType: DistributionURL Description: NASA's newest search and order tool for subsetting, reprojecting, and reformatting data. Type: GET DATA Subtype:
       Earthdata Search Number: 10"
      (re-pattern "URLContentType:|Description:|Type:|Subtype:|Checksum:|Number:")
      '(:Number)
      {:Type "GET DATA",
       :URLContentType "DistributionURL",
       :Description "NASA's newest search and order tool for subsetting, reprojecting, and reformatting data.",
       :Subtype "Earthdata Search"
       :Number 10}

      "String with odd and nil values"
      ":: URLContentType:nil Checksum: \"nil\" Description: NASA's newest lawnmower ascii art: __\\.-.,,,, Type: SELF PROPELLED Subtype: Earthdata Search Number: 10"
      (re-pattern "URLContentType:|Description:|Type:|Subtype:|Checksum:|Number:")
      nil
      {:Checksum "\"nil\"",
       :Description "NASA's newest lawnmower ascii art: __\\.-.,,,,",
       :Type "SELF PROPELLED",
       :Subtype "Earthdata Search"
       :Number "10"})))
