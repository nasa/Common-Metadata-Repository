(ns cmr.umm-spec.test.xml-to-umm-mappings.dif9.spatial-extent
  (:require
   [clojure.test :refer :all]
   [cmr.umm-spec.xml-to-umm-mappings.dif9.spatial-extent :as spatial]))

(def full-double-spatial-coverage
  "<DIF>
     <Spatial_Coverage>
       <Southernmost_Latitude>90</Southernmost_Latitude>
       <Northernmost_Latitude>-90</Northernmost_Latitude>
       <Westernmost_Longitude>-180</Westernmost_Longitude>
       <Easternmost_Longitude>180</Easternmost_Longitude>
       <Minimum_Altitude>0</Minimum_Altitude>
       <Maximum_Altitude>100</Maximum_Altitude>
       <Minimum_Depth>-1</Minimum_Depth>
       <Maximum_Depth>-100</Maximum_Depth>
     </Spatial_Coverage>
     <Spatial_Coverage>
       <Southernmost_Latitude>90</Southernmost_Latitude>
       <Northernmost_Latitude>-90</Northernmost_Latitude>
       <Westernmost_Longitude>-180</Westernmost_Longitude>
       <Easternmost_Longitude>180</Easternmost_Longitude>
       <Minimum_Altitude>0</Minimum_Altitude>
       <Maximum_Altitude>100</Maximum_Altitude>
       <Minimum_Depth>-1</Minimum_Depth>
       <Maximum_Depth>-100</Maximum_Depth>
     </Spatial_Coverage>
     <Extended_Metadata>
       <Metadata>
         <Name>GranuleSpatialRepresentation</Name>
         <Value>CARTESIAN</Value>
       </Metadata>
     </Extended_Metadata>
   </DIF>")

(def expected-full-double-spatial-coverage
  {:GranuleSpatialRepresentation "CARTESIAN"
   :SpatialCoverageType "HORIZONTAL_VERTICAL"
   :HorizontalSpatialDomain
   {:Geometry {:CoordinateSystem "CARTESIAN"
               :BoundingRectangles [{:NorthBoundingCoordinate "-90"
                                     :SouthBoundingCoordinate "90"
                                     :WestBoundingCoordinate "-180"
                                     :EastBoundingCoordinate "180"}
                                    {:NorthBoundingCoordinate "-90"
                                     :SouthBoundingCoordinate "90"
                                     :WestBoundingCoordinate "-180"
                                     :EastBoundingCoordinate "180"}]}}

   :VerticalSpatialDomains [{:Type "Minimum Altitude"
                             :Value "0"}
                            {:Type "Maximum Altitude"
                             :Value "100"}
                            {:Type "Minimum Depth"
                             :Value "-1"}
                            {:Type "Maximum Depth"
                             :Value "-100"}
                            {:Type "Minimum Altitude"
                             :Value "0"}
                            {:Type "Maximum Altitude"
                             :Value "100"}
                            {:Type "Minimum Depth"
                             :Value "-1"}
                            {:Type "Maximum Depth"
                             :Value "-100"}]})

(def full-single-spatial-coverage
  "<DIF>
     <Spatial_Coverage>
       <Southernmost_Latitude>90</Southernmost_Latitude>
       <Northernmost_Latitude>-90</Northernmost_Latitude>
       <Westernmost_Longitude>-180</Westernmost_Longitude>
       <Easternmost_Longitude>180</Easternmost_Longitude>
       <Minimum_Altitude>0</Minimum_Altitude>
       <Maximum_Altitude>100</Maximum_Altitude>
       <Minimum_Depth>-1</Minimum_Depth>
       <Maximum_Depth>-100</Maximum_Depth>
     </Spatial_Coverage>
     <Extended_Metadata>
       <Metadata>
         <Name>GranuleSpatialRepresentation</Name>
         <Value>CARTESIAN</Value>
       </Metadata>
     </Extended_Metadata>
   </DIF>")

(def expected-full-single-spatial-coverage
  {:GranuleSpatialRepresentation "CARTESIAN"
   :SpatialCoverageType "HORIZONTAL_VERTICAL"
   :HorizontalSpatialDomain
   {:Geometry {:CoordinateSystem "CARTESIAN"
               :BoundingRectangles [{:NorthBoundingCoordinate "-90"
                                     :SouthBoundingCoordinate "90"
                                     :WestBoundingCoordinate "-180"
                                     :EastBoundingCoordinate "180"}]}}
   :VerticalSpatialDomains [{:Type "Minimum Altitude"
                             :Value "0"}
                            {:Type "Maximum Altitude"
                             :Value "100"}
                            {:Type "Minimum Depth"
                             :Value "-1"}
                            {:Type "Maximum Depth"
                             :Value "-100"}]})

(def just-bounding-spatial-coverage
  "<DIF>
     <Spatial_Coverage>
       <Southernmost_Latitude>90</Southernmost_Latitude>
       <Northernmost_Latitude>-90</Northernmost_Latitude>
       <Westernmost_Longitude>-180</Westernmost_Longitude>
       <Easternmost_Longitude>180</Easternmost_Longitude>
     </Spatial_Coverage>
     <Extended_Metadata>
       <Metadata>
         <Name>GranuleSpatialRepresentation</Name>
         <Value>CARTESIAN</Value>
       </Metadata>
     </Extended_Metadata>
   </DIF>")

(def expected-just-bounding-spatial-coverage
  {:GranuleSpatialRepresentation "CARTESIAN"
   :SpatialCoverageType "HORIZONTAL"
   :HorizontalSpatialDomain
   {:Geometry {:CoordinateSystem "CARTESIAN"
               :BoundingRectangles [{:NorthBoundingCoordinate "-90"
                                     :SouthBoundingCoordinate "90"
                                     :WestBoundingCoordinate "-180"
                                     :EastBoundingCoordinate "180"}]}}})

(def just-vertical-spatial-coverage
  "<DIF>
     <Spatial_Coverage>
       <Minimum_Altitude>0</Minimum_Altitude>
       <Maximum_Altitude>100</Maximum_Altitude>
       <Minimum_Depth>-1</Minimum_Depth>
       <Maximum_Depth>-100</Maximum_Depth>
     </Spatial_Coverage>
     <Extended_Metadata>
       <Metadata>
         <Name>GranuleSpatialRepresentation</Name>
         <Value>CARTESIAN</Value>
       </Metadata>
     </Extended_Metadata>
   </DIF>")

(def expected-just-vertical-spatial-coverage
  {:GranuleSpatialRepresentation "CARTESIAN"
   :SpatialCoverageType "VERTICAL"
   :VerticalSpatialDomains [{:Type "Minimum Altitude"
                             :Value "0"}
                            {:Type "Maximum Altitude"
                             :Value "100"}
                            {:Type "Minimum Depth"
                             :Value "-1"}
                            {:Type "Maximum Depth"
                             :Value "-100"}]})

(def partial-vertical-spatial-coverage
  "<DIF>
     <Spatial_Coverage>
       <Minimum_Altitude>0</Minimum_Altitude>
       <Maximum_Altitude>100</Maximum_Altitude>
     </Spatial_Coverage>
     <Extended_Metadata>
       <Metadata>
         <Name>GranuleSpatialRepresentation</Name>
         <Value>CARTESIAN</Value>
       </Metadata>
     </Extended_Metadata>
   </DIF>")

(def expected-partial-vertical-spatial-coverage
  {:GranuleSpatialRepresentation "CARTESIAN"
   :SpatialCoverageType "VERTICAL"
   :VerticalSpatialDomains [{:Type "Minimum Altitude"
                             :Value "0"}
                            {:Type "Maximum Altitude"
                             :Value "100"}]})

(def partial-bounding-spatial-coverage
  "<DIF>
     <Spatial_Coverage>
       <Southernmost_Latitude>90</Southernmost_Latitude>
       <Northernmost_Latitude>-90</Northernmost_Latitude>
     </Spatial_Coverage>
     <Extended_Metadata>
       <Metadata>
         <Name>GranuleSpatialRepresentation</Name>
         <Value>CARTESIAN</Value>
       </Metadata>
     </Extended_Metadata>
   </DIF>")

(def expected-partial-bounding-spatial-coverage
  {:GranuleSpatialRepresentation "CARTESIAN"
   :SpatialCoverageType "HORIZONTAL"
   :HorizontalSpatialDomain
   {:Geometry {:CoordinateSystem "CARTESIAN"
               :BoundingRectangles [{:NorthBoundingCoordinate "-90"
                                     :SouthBoundingCoordinate "90"}]}}})

(def no-spatial-coverage
  "<DIF>
   </DIF>")

(def expected-no-spatial-coverage
  {:GranuleSpatialRepresentation "NO_SPATIAL"})

(def missing-granule-representation
  "<DIF>
     <Spatial_Coverage>
       <Southernmost_Latitude>90</Southernmost_Latitude>
       <Northernmost_Latitude>-90</Northernmost_Latitude>
       <Westernmost_Longitude>-180</Westernmost_Longitude>
       <Easternmost_Longitude>180</Easternmost_Longitude>
     </Spatial_Coverage>
   </DIF>")

(def expected-missing-granule-representation
  {:GranuleSpatialRepresentation "NO_SPATIAL"
   :SpatialCoverageType "HORIZONTAL"
   :HorizontalSpatialDomain
   {:Geometry {:CoordinateSystem "CARTESIAN"
               :BoundingRectangles [{:NorthBoundingCoordinate "-90"
                                     :SouthBoundingCoordinate "90"
                                     :WestBoundingCoordinate "-180"
                                     :EastBoundingCoordinate "180"}]}}})

(deftest test-dif9-spatial-extent

  (testing "Full Spatial_Coverage test"
    (is (= expected-full-single-spatial-coverage
           (spatial/parse-spatial-extent full-single-spatial-coverage true))))

  (testing "Full Double Spatial_Coverage test"
    (is (= expected-full-double-spatial-coverage
           (spatial/parse-spatial-extent full-double-spatial-coverage true))))

  (testing "Just Bounding Rectangle Spatial_Coverage test"
    (is (= expected-just-bounding-spatial-coverage
           (spatial/parse-spatial-extent just-bounding-spatial-coverage true))))

  (testing "Just Vertical Spatial_Coverage test"
    (is (= expected-just-vertical-spatial-coverage
           (spatial/parse-spatial-extent just-vertical-spatial-coverage true))))

  (testing "Partial Vertical Spatial_Coverage test"
    (is (= expected-partial-vertical-spatial-coverage
           (spatial/parse-spatial-extent partial-vertical-spatial-coverage true))))

  (testing "Partial Bounding Spatial_Coverage test"
    (is (= expected-partial-bounding-spatial-coverage
           (spatial/parse-spatial-extent partial-bounding-spatial-coverage true))))

  (testing "No Spatial_Coverage test"
    (is (= expected-no-spatial-coverage
           (spatial/parse-spatial-extent no-spatial-coverage true))))

  (testing "Missing Granule Representation test"
    (is (= expected-missing-granule-representation
           (spatial/parse-spatial-extent missing-granule-representation true)))))
