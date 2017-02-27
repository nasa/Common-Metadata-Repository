(ns cmr.umm-spec.test.xml-to-umm-mappings.dif10.related-url
  (:require [clj-time.core :as t]
            [clojure.test :refer :all]
            [cmr.umm-spec.xml-to-umm-mappings.dif10.related-url :as ru]))

(deftest dif10-metadata-related-url-test
  (testing "parse related urls from Multimedia_Sample"
    (is (= [{:URL "http://disc.sci.gsfc.nasa.gov/OCO-2/images/ACOS.xCO2.2013.v3.5.png"
             :Description " Global amounts of column CO2 in 2013, ..."
             :Relation ["GET RELATED VISUALIZATION"]}]
           (ru/parse-related-urls
             "<DIF>
                <Multimedia_Sample>
                  <URL>http://disc.sci.gsfc.nasa.gov/OCO-2/images/ACOS.xCO2.2013.v3.5.png</URL>
                  <Format>PNG</Format>
                  <Caption>ACOS xCO2 v2.5, yearly mean for 2013, in part per million in volume</Caption>
                  <Description> Global amounts of column CO2 in 2013, ...</Description>
                </Multimedia_Sample>
              </DIF>"
             true))))

  (testing "parse realted urls from Related_URL and Multimedia_Sample together"
    (is (= [{:URL "http://disc.sci.gsfc.nasa.gov/OCO-2/images/ACOS.xCO2.2013.v3.5.png"
             :Description " Global amounts of column CO2 in 2013, ..."
             :Relation ["GET RELATED VISUALIZATION"]}
            {:URL "http://reverb.echo.nasa.gov/reverb/"
             :Description "Interface to search, discover, and access EOS data products, and invoke available data services."
             :Relation ["GET DATA" "REVERB"]
             :MimeType nil}]
           (ru/parse-related-urls
             "<DIF>
                <Multimedia_Sample>
                  <URL>http://disc.sci.gsfc.nasa.gov/OCO-2/images/ACOS.xCO2.2013.v3.5.png</URL>
                  <Format>PNG</Format>
                  <Caption>ACOS xCO2 v2.5, yearly mean for 2013, in part per million in volume</Caption>
                  <Description> Global amounts of column CO2 in 2013, ...</Description>
                </Multimedia_Sample>
                <Related_URL>
                  <URL_Content_Type>
                    <Type>GET DATA</Type>
                    <Subtype>REVERB</Subtype>
                  </URL_Content_Type>
                  <URL>http://reverb.echo.nasa.gov/reverb/</URL>
                  <Description>Interface to search, discover, and access EOS data products, and invoke available data services.</Description>
                </Related_URL>
              </DIF>"
              true)))))
