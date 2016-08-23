(ns cmr.umm.test.validation.collection-validation-tests
  "This has tests for UMM validations."
  (:require [clojure.test :refer :all]
            [cmr.umm.validation.validation-core :as v]
            [cmr.umm.umm-collection :as c]
            [cmr.umm.test.validation.validation-test-helpers :as helpers]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.point :as p]
            [cmr.common.services.errors :as e]
            [cmr.umm.collection.product-specific-attribute :as psa]))

(defn assert-valid
  "Asserts that the given collection is valid."
  [collection]
  (is (empty? (v/validate-collection collection))))

(defn assert-invalid
  "Asserts that the given umm model is invalid and has the expected error messages.
  field-path is the path within the metadata to the error. expected-errors is a list of string error
  messages."
  [collection field-path expected-errors]
  (is (= [(e/map->PathErrors {:path field-path
                              :errors (vec expected-errors)})]
         (v/validate-collection collection))))

(defn assert-multiple-invalid
  "Asserts there are multiple errors at different paths invalid with the UMM. Expected errors
  should be a list of maps with path and errors."
  [collection expected-errors]
  (is (= (set (map e/map->PathErrors expected-errors))
         (set (v/validate-collection collection)))))

(defn coll-with-psas
  [psas]
  (c/map->UmmCollection {:product-specific-attributes (map c/map->ProductSpecificAttribute psas)}))

(defn coll-with-geometries
  ([geometries]
   ;; Insert a default coordinate reference system in order to pass validation (See CMR-2928)
   (coll-with-geometries "GEODETIC" geometries))
  ([coordinate-reference geometries]
   (c/map->UmmCollection {:spatial-coverage (c/map->SpatialCoverage
                                              {:spatial-representation coordinate-reference
                                               :geometries geometries})})))

;; This is built on top of the existing spatial validation. It just ensures that the spatial
;; validation is being called
(deftest collection-spatial-coverage
  (let [valid-point p/north-pole
        valid-mbr (m/mbr 0 0 0 0)
        invalid-point (p/point -181 0)
        invalid-mbr (m/mbr -180 45 180 46)]
    (testing "Valid spatial areas"
      (assert-valid (coll-with-geometries [valid-point]))
      (assert-valid (coll-with-geometries [valid-point valid-mbr])))
    (testing "Invalid single geometry"
      (assert-invalid
        (coll-with-geometries [invalid-point])
        [:spatial-coverage :geometries 0]
        ["Spatial validation error: Point longitude [-181] must be within -180.0 and 180.0"]))
    (testing "Invalid multiple geometry"
      (let [expected-errors [{:path [:spatial-coverage :geometries 1]
                              :errors ["Spatial validation error: Point longitude [-181] must be within -180.0 and 180.0"]}
                             {:path [:spatial-coverage :geometries 2]
                              :errors ["Spatial validation error: The bounding rectangle north value [45] was less than the south value [46]"]}]]
        (assert-multiple-invalid (coll-with-geometries [valid-point invalid-point invalid-mbr])
                                 expected-errors)))
    (testing "Orbit parameters missing for an orbit collection"
      (assert-invalid (c/map->UmmCollection
                        {:spatial-coverage
                         (c/map->SpatialCoverage {:granule-spatial-representation :orbit})})
                      [:spatial-coverage]
                      ["Orbit Parameters must be defined for a collection whose granule spatial representation is ORBIT."]))))

(deftest collection-product-specific-attributes-validation
  (testing "product specific attributes names"
    (testing "valid product specific attributes names"
      (assert-valid (coll-with-psas [{:name "foo" :data-type :string :description "test"}
                                     {:name "bar" :data-type :string :description "test"}])))
    (testing "invalid product specific attributes names"
      (testing "duplicate names"
        (let [coll (coll-with-psas [{:name "foo" :data-type :string :description "test"}
                                    {:name "foo" :data-type :string :description "test"}
                                    {:name "bar" :data-type :string :description "test"}
                                    {:name "bar" :data-type :string :description "test"}
                                    {:name "charlie" :data-type :string :description "test"}])]
          (assert-invalid
            coll
            [:product-specific-attributes]
            ["Product Specific Attributes must be unique. This contains duplicates named [foo, bar]."])))))

  (testing "product specific attributes data type"
    (testing "valid data types"
      (are [data-type]
           (assert-valid (coll-with-psas [{:name "foo" :data-type data-type :description "test"}]))
           :string
           :float
           :int
           :boolean
           :date
           :time
           :datetime
           :date-string
           :time-string
           :datetime-string))

    (testing "invalid data type"
      (are [data-type error]
           (let [coll (coll-with-psas [{:name "foo" :data-type data-type  :description "test"}])]
             (assert-invalid coll [:product-specific-attributes 0 :data-type] [error]))
           nil "Additional Attribute Data Type [] is not a valid data type."
           :intstring "Additional Attribute Data Type [INTSTRING] is not a valid data type."))

    (testing "multiple invalid data types"
      (let [coll (coll-with-psas [{:name "foo" :description "test"}
                                  {:name "bar" :data-type :intstring :description "test"}])]
        (assert-multiple-invalid
          coll
          [{:path [:product-specific-attributes 0 :data-type]
            :errors
            ["Additional Attribute Data Type [] is not a valid data type."]}
           {:path [:product-specific-attributes 1 :data-type]
            :errors
            ["Additional Attribute Data Type [INTSTRING] is not a valid data type."]}]))))

  (testing "product specific attributes values match data type"
    (testing "valid values"
      (are [data-type value]
           (assert-valid (coll-with-psas [{:name "foo" :data-type data-type :value value :description "test"}]))
           :string "string value"
           :float "1.0"
           :int "1"
           :boolean "true"
           :date "1986-10-14"
           :time "04:03:27.123Z"
           :datetime "1986-10-14T04:03:27.0Z"
           :date-string "1986-10-14"
           :time-string "04:03:27.123"
           :datetime-string "1986-10-14T04:03:27.0Z"
           :string nil
           :float nil
           :int nil
           :boolean nil
           :date nil
           :time nil
           :datetime nil
           :date-string nil
           :time-string nil
           :datetime-string nil)
      (are [data-type value]
           (and
             (assert-valid (coll-with-psas [{:name "foo" :data-type data-type :parameter-range-begin value :description "test"}]))
             (assert-valid (coll-with-psas [{:name "foo" :data-type data-type :parameter-range-end value :description "test"}])))
           :float "1.0"
           :int "1"
           :date "1986-10-14"
           :time "04:03:27.123Z"
           :datetime "1986-10-14T04:03:27.0Z"
           :date-string "1986-10-14"
           :time-string "04:03:27.123"
           :datetime-string "1986-10-14T04:03:27.0Z"
           :string nil
           :float nil
           :int nil
           :boolean nil
           :date nil
           :time nil
           :datetime nil
           :date-string nil
           :time-string nil
           :datetime-string nil))

    (testing "invalid values"
      (are [data-type value field errors]
           (assert-invalid
             (coll-with-psas [{:name "foo" :data-type data-type field value :description "test"}])
             [:product-specific-attributes 0] errors)

           :boolean "true" :parameter-range-begin ["Parameter Range Begin is not allowed for type [BOOLEAN]"]
           :float "bar" :parameter-range-begin ["Parameter Range Begin [bar] is not a valid value for type [FLOAT]."]
           :int "bar" :parameter-range-begin ["Parameter Range Begin [bar] is not a valid value for type [INT]."]
           :date "bar" :parameter-range-begin ["Parameter Range Begin [bar] is not a valid value for type [DATE]."]
           :time "bar" :parameter-range-begin ["Parameter Range Begin [bar] is not a valid value for type [TIME]."]
           :datetime "bar" :parameter-range-begin ["Parameter Range Begin [bar] is not a valid value for type [DATETIME]."]

           :boolean "true" :parameter-range-end ["Parameter Range End is not allowed for type [BOOLEAN]"]
           :float "bar" :parameter-range-end ["Parameter Range End [bar] is not a valid value for type [FLOAT]."]
           :int "bar" :parameter-range-end ["Parameter Range End [bar] is not a valid value for type [INT]."]
           :date "bar" :parameter-range-end ["Parameter Range End [bar] is not a valid value for type [DATE]."]
           :time "bar" :parameter-range-end ["Parameter Range End [bar] is not a valid value for type [TIME]."]
           :datetime "bar" :parameter-range-end ["Parameter Range End [bar] is not a valid value for type [DATETIME]."]

           :boolean "bar" :value ["Value [bar] is not a valid value for type [BOOLEAN]."]
           :float "bar" :value ["Value [bar] is not a valid value for type [FLOAT]."]
           :int "bar" :value ["Value [bar] is not a valid value for type [INT]."]
           :date "bar" :value ["Value [bar] is not a valid value for type [DATE]."]
           :time "bar" :value ["Value [bar] is not a valid value for type [TIME]."]
           :datetime "bar" :value ["Value [bar] is not a valid value for type [DATETIME]."]))

    (testing "multiple invalid values"
      (assert-multiple-invalid
        (coll-with-psas [{:name "foo" :data-type :float :value "str" :description "test"}
                         {:name "bar" :data-type :float :value "1.0" :description "test"}
                         {:name "baz" :data-type :int :value "1.0" :description "test"}])
        [{:path [:product-specific-attributes 0]
          :errors
          ["Value [str] is not a valid value for type [FLOAT]."]}
         {:path [:product-specific-attributes 2]
          :errors
          ["Value [1.0] is not a valid value for type [INT]."]}])))

  (testing "product specific attributes range values"
    (testing "valid range values"
      (are [data-type begin end value]
           (assert-valid (coll-with-psas [{:name "foo"
                                           :data-type data-type
                                           :parsed-parameter-range-begin (psa/parse-value data-type begin)
                                           :parsed-parameter-range-end (psa/parse-value data-type end)
                                           :parsed-value (psa/parse-value data-type value)
                                           :description "test"}]))
           :string nil nil "string value"
           :float "1.0" "3.0" "2.0"
           :int "1" "3" "2"
           :int "1" "1" "1"
           :boolean nil nil "true"
           :date "1986-10-14" "1986-10-16" "1986-10-15"
           :time "04:03:27.123Z" "04:03:29Z" "04:03:28Z"
           :datetime "1986-10-14T04:03:27.0Z" "1986-10-14T04:03:29Z" "1986-10-14T04:03:28Z"
           :date-string "1986-10-14" "1986-10-14" "1986-10-14"
           :time-string "04:03:27.123" "04:03:27.123" "04:03:27.123"
           :datetime-string "1986-10-14T04:03:27.0Z" "1986-10-14T04:03:27.0Z" "1986-10-14T04:03:27.0Z"))

    (testing "invalid range values"
      (testing "parameter range begin is greater than parameter range end"
        (are [data-type begin end value errors]
             (assert-invalid
               (coll-with-psas [{:name "foo"
                                 :data-type data-type
                                 :parsed-parameter-range-begin (psa/parse-value data-type begin)
                                 :parsed-parameter-range-end (psa/parse-value data-type end)
                                 :parsed-value (psa/parse-value data-type value)
                                 :description "test"}])
               [:product-specific-attributes 0] errors)

             :float "3.0" "1.0" "2.0"
             ["Parameter Range Begin [3.0] cannot be greater than Parameter Range End [1.0]."]

             :int "3" "1" "2"
             ["Parameter Range Begin [3] cannot be greater than Parameter Range End [1]."]

             :date "1986-10-16" "1986-10-14" "1986-10-15"
             ["Parameter Range Begin [1986-10-16] cannot be greater than Parameter Range End [1986-10-14]."]

             :time "04:03:29Z" "04:03:27Z" "04:03:28Z"
             ["Parameter Range Begin [04:03:29.000] cannot be greater than Parameter Range End [04:03:27.000]."]

             :datetime "1986-10-14T04:03:29.0Z" "1986-10-14T04:03:27.0Z" "1986-10-14T04:03:28Z"
             ["Parameter Range Begin [1986-10-14T04:03:29.000Z] cannot be greater than Parameter Range End [1986-10-14T04:03:27.000Z]."]))
      (testing "value is less than parameter range begin"
        (are [data-type begin end value errors]
             (assert-invalid
               (coll-with-psas [{:name "foo"
                                 :data-type data-type
                                 :parsed-parameter-range-begin (psa/parse-value data-type begin)
                                 :parsed-parameter-range-end (psa/parse-value data-type end)
                                 :parsed-value (psa/parse-value data-type value)
                                 :description "test"}])
               [:product-specific-attributes 0] errors)

             :float "2.0" "3.0" "1.0"
             ["Value [1.0] cannot be less than Parameter Range Begin [2.0]."]

             :int "2" "3" "1"
             ["Value [1] cannot be less than Parameter Range Begin [2]."]

             :date "1986-10-15" "1986-10-16" "1986-10-14"
             ["Value [1986-10-14] cannot be less than Parameter Range Begin [1986-10-15]."]

             :time "04:03:28Z" "04:03:29Z" "04:03:27Z"
             ["Value [04:03:27.000] cannot be less than Parameter Range Begin [04:03:28.000]."]

             :datetime "1986-10-14T04:03:28Z" "1986-10-14T04:03:29Z" "1986-10-14T04:03:27Z"
             ["Value [1986-10-14T04:03:27.000Z] cannot be less than Parameter Range Begin [1986-10-14T04:03:28.000Z]."]))
      (testing "value is greater than parameter range end"
        (are [data-type begin end value errors]
             (assert-invalid
               (coll-with-psas [{:name "foo"
                                 :data-type data-type
                                 :parsed-parameter-range-begin (psa/parse-value data-type begin)
                                 :parsed-parameter-range-end (psa/parse-value data-type end)
                                 :parsed-value (psa/parse-value data-type value)
                                 :description "test"}])
               [:product-specific-attributes 0] errors)

             :float "1.0" "2.0" "3.0"
             ["Value [3.0] cannot be greater than Parameter Range End [2.0]."]

             :int "1" "2" "3"
             ["Value [3] cannot be greater than Parameter Range End [2]."]

             :date "1986-10-14" "1986-10-15" "1986-10-16"
             ["Value [1986-10-16] cannot be greater than Parameter Range End [1986-10-15]."]

             :time "04:03:27Z" "04:03:28Z" "04:03:29Z"
             ["Value [04:03:29.000] cannot be greater than Parameter Range End [04:03:28.000]."]

             :datetime "1986-10-14T04:03:27Z" "1986-10-14T04:03:28Z" "1986-10-14T04:03:29Z"
             ["Value [1986-10-14T04:03:29.000Z] cannot be greater than Parameter Range End [1986-10-14T04:03:28.000Z]."])))))

(deftest collection-projects-validation
  (let [c1 (c/map->Project {:short-name "C1"})
        c2 (c/map->Project {:short-name "C2"})
        c3 (c/map->Project {:short-name "C3"})]
    (testing "valid projects"
      (assert-valid (c/map->UmmCollection {:projects [c1 c2]})))

    (testing "invalid projects"
      (testing "duplicate names"
        (let [coll (c/map->UmmCollection {:projects [c1 c1 c2 c2 c3]})]
          (assert-invalid
            coll
            [:projects]
            ["Projects must be unique. This contains duplicates named [C1, C2]."]))))))


(deftest collection-platforms-validation
  (let [s1 (c/map->Sensor {:short-name "S1"})
        s2 (c/map->Sensor {:short-name "S2"})
        i1 (c/map->Instrument {:short-name "I1"
                               :sensors [s1 s2]})
        i2 (c/map->Instrument {:short-name "I2"
                               :sensors [s1 s2]})
        c1 (c/map->Characteristic {:name "C1"})
        c2 (c/map->Characteristic {:name "C2"})]
    (testing "valid platforms"
      (assert-valid (c/map->UmmCollection
                      {:platforms [(c/map->Platform {:short-name "P1"
                                                     :instruments [i1 i2]
                                                     :characteristics [c1 c2]})
                                   (c/map->Platform {:short-name "P2"
                                                     :instruments [i1 i2]
                                                     :characteristics [c1 c2]})]})))

    (testing "invalid platforms"
      (testing "duplicate platform short names"
        (let [coll (c/map->UmmCollection
                     {:platforms [(c/map->Platform {:short-name "P1"})
                                  (c/map->Platform {:short-name "P1"})]})]
          (assert-invalid
            coll
            [:platforms]
            ["Platforms must be unique. This contains duplicates named [P1]."])))
      (testing "duplicate platform characteristics names"
        (let [coll (c/map->UmmCollection
                     {:platforms [(c/map->Platform {:short-name "P1"
                                                    :characteristics [c1 c1]})]})]
          (assert-invalid
            coll
            [:platforms 0 :characteristics]
            ["Characteristics must be unique. This contains duplicates named [C1]."])))
      (testing "duplicate instrument short names"
        (let [coll (c/map->UmmCollection
                     {:platforms [(c/map->Platform {:short-name "P1"
                                                    :instruments [i1 i1]})]})]
          (assert-invalid
            coll
            [:platforms 0 :instruments]
            ["Instruments must be unique. This contains duplicates named [I1]."])))
      (testing "duplicate instrument characteristics names"
        (let [coll (c/map->UmmCollection
                     {:platforms [(c/map->Platform
                                    {:short-name "P1"
                                     :instruments [(c/map->Instrument
                                                     {:short-name "I1"
                                                      :characteristics [c1 c1]})]})]})]
          (assert-invalid
            coll
            [:platforms 0 :instruments 0 :characteristics]
            ["Characteristics must be unique. This contains duplicates named [C1]."])))
      (testing "duplicate sensor short names"
        (let [coll (c/map->UmmCollection
                     {:platforms [(c/map->Platform
                                    {:short-name "P1"
                                     :instruments [(c/map->Instrument {:short-name "I1"
                                                                       :sensors [s1 s1]})]})]})]
          (assert-invalid
            coll
            [:platforms 0 :instruments 0 :sensors]
            ["Sensors must be unique. This contains duplicates named [S1]."])))
      (testing "duplicate sensor characteristics names"
        (let [coll (c/map->UmmCollection
                     {:platforms [(c/map->Platform
                                    {:short-name "P1"
                                     :instruments [(c/map->Instrument
                                                     {:short-name "I1"
                                                      :sensors [(c/map->Sensor
                                                                  {:short-name "S1"
                                                                   :characteristics [c2 c2]})]})]})]})]
          (assert-invalid
            coll
            [:platforms 0 :instruments 0 :sensors 0 :characteristics]
            ["Characteristics must be unique. This contains duplicates named [C2]."])))
      (testing "multiple errors"
        (let [coll (c/map->UmmCollection
                     {:platforms [(c/map->Platform
                                    {:short-name "P1"})
                                  (c/map->Platform
                                    {:short-name "P1"
                                     :instruments [(c/map->Instrument {:short-name "I1"
                                                                       :sensors [s1 s1]})
                                                   (c/map->Instrument {:short-name "I1"
                                                                       :sensors [s1 s2 s2]})]})]})
              expected-errors [{:path [:platforms 1 :instruments 0 :sensors]
                                :errors ["Sensors must be unique. This contains duplicates named [S1]."]}
                               {:path [:platforms 1 :instruments 1 :sensors]
                                :errors ["Sensors must be unique. This contains duplicates named [S2]."]}
                               {:path [:platforms 1 :instruments]
                                :errors ["Instruments must be unique. This contains duplicates named [I1]."]}
                               {:path [:platforms]
                                :errors ["Platforms must be unique. This contains duplicates named [P1]."]}]]
          (assert-multiple-invalid coll expected-errors))))))

(deftest collection-associated-difs-validation
  (testing "valid associated difs"
    (assert-valid (c/map->UmmCollection {:associated-difs ["d1" "d2" "d3"]})))

  (testing "invalid associated difs"
    (testing "duplicate names"
      (let [coll (c/map->UmmCollection {:associated-difs ["d1" "d2" "d1"]})]
        (assert-invalid
          coll
          [:associated-difs]
          ["Associated Difs must be unique. This contains duplicates named [d1]."])))))

(deftest collection-temporal-validation
  (testing "valid temporal"
    (let [r1 (helpers/range-date-time "1999-12-30T19:00:00Z" "1999-12-30T19:00:01Z")
          r2 (helpers/range-date-time "1999-12-30T19:00:00Z" nil)
          r3 (helpers/range-date-time "1999-12-30T19:00:00Z" "1999-12-30T19:00:00Z")]
      (assert-valid (helpers/coll-with-range-date-times [r1]))
      (assert-valid (helpers/coll-with-range-date-times [r2]))
      (assert-valid (helpers/coll-with-range-date-times [r3]))
      (assert-valid (helpers/coll-with-range-date-times [r1 r2 r3]))))

  (testing "invalid temporal"
    (testing "single error"
      (let [r1 (helpers/range-date-time "1999-12-30T19:00:02Z" "1999-12-30T19:00:01Z")
            coll (helpers/coll-with-range-date-times [r1])]
        (assert-invalid
          coll
          [:temporal :range-date-times 0]
          ["BeginningDateTime [1999-12-30T19:00:02.000Z] must be no later than EndingDateTime [1999-12-30T19:00:01.000Z]"])))

    (testing "multiple errors"
      (let [r1 (helpers/range-date-time "1999-12-30T19:00:02Z" "1999-12-30T19:00:01Z")
            r2 (helpers/range-date-time "2000-12-30T19:00:02Z" "2000-12-30T19:00:01Z")
            coll (helpers/coll-with-range-date-times [r1 r2])]
        (assert-multiple-invalid
          coll
          [{:path [:temporal :range-date-times 0],
            :errors
            ["BeginningDateTime [1999-12-30T19:00:02.000Z] must be no later than EndingDateTime [1999-12-30T19:00:01.000Z]"]}
           {:path [:temporal :range-date-times 1],
            :errors
            ["BeginningDateTime [2000-12-30T19:00:02.000Z] must be no later than EndingDateTime [2000-12-30T19:00:01.000Z]"]}])))))

(deftest collection-online-access-urls-validation
  (let [url "http://example.com/url2"
        r1 (c/map->RelatedURL {:type "GET DATA"
                               :url "http://example.com/url1"})
        r2 (c/map->RelatedURL {:type "GET DATA"
                               :url url})
        r3 (c/map->RelatedURL {:type "GET RELATED VISUALIZATION"
                               :url url})]
    (testing "valid online access urls"
      (assert-valid (c/map->UmmCollection {:related-urls [r1 r2 r3]})))

    (testing "invalid online access urls"
      (testing "duplicate names"
        (let [coll (c/map->UmmCollection {:related-urls [r1 r2 r2]})]
          (assert-invalid
            coll
            [:related-urls]
            [(format "Related Urls must be unique. This contains duplicates named [%s]." url)]))))))

(deftest collection-two-d-coordinate-systems-validation
  (let [t1 (c/map->TwoDCoordinateSystem {:name "T1"
                                         :coordinate-1 (c/map->Coordinate {:min-value 0.0
                                                                           :max-value 6.0})
                                         :coordinate-2 (c/map->Coordinate {:min-value 10.0
                                                                           :max-value 10.0})})
        t2 (c/map->TwoDCoordinateSystem {:name "T2"
                                         :coordinate-1 (c/map->Coordinate {:min-value 0.0})
                                         :coordinate-2 (c/map->Coordinate {:max-value 26.0})})
        t3 (c/map->TwoDCoordinateSystem {:name "T3"
                                         :coordinate-1 (c/map->Coordinate {:min-value 10.0
                                                                           :max-value 6.0})})
        t4 (c/map->TwoDCoordinateSystem {:name "T4"
                                         :coordinate-1 (c/map->Coordinate {:min-value 0.0
                                                                           :max-value 6.0})
                                         :coordinate-2 (c/map->Coordinate {:min-value 50.0
                                                                           :max-value 26.0})})]
    (testing "valid two-d-coordinate-systems"
      (assert-valid (c/map->UmmCollection {:two-d-coordinate-systems [t1 t2]})))

    (testing "invalid two-d-coordinate-systems"
      (testing "duplicate names"
        (let [coll (c/map->UmmCollection {:two-d-coordinate-systems [t1 t1]})]
          (assert-invalid
            coll
            [:two-d-coordinate-systems]
            ["Two D Coordinate Systems must be unique. This contains duplicates named [T1]."])))
      (testing "invalid coordinate"
        (let [coll (c/map->UmmCollection {:two-d-coordinate-systems [t3]})]
          (assert-invalid
            coll
            [:two-d-coordinate-systems 0 :coordinate-1]
            ["Coordinate 1 minimum [10.0] must be less than the maximum [6.0]."])))
      (testing "multiple validation errors"
        (let [coll (c/map->UmmCollection {:two-d-coordinate-systems [t1 t1 t3 t4]})]
          (assert-multiple-invalid
            coll
            [{:path [:two-d-coordinate-systems],
              :errors
              ["Two D Coordinate Systems must be unique. This contains duplicates named [T1]."]}
             {:path [:two-d-coordinate-systems 2 :coordinate-1],
              :errors
              ["Coordinate 1 minimum [10.0] must be less than the maximum [6.0]."]}
             {:path [:two-d-coordinate-systems 3 :coordinate-2],
              :errors
              ["Coordinate 2 minimum [50.0] must be less than the maximum [26.0]."]}]))))))

(deftest collection-associations-validation
  (testing "valid collection associations"
    (assert-valid (c/map->UmmCollection
                    {:collection-associations
                     [(c/map->CollectionAssociation {:short-name "S1"
                                                     :version-id "V1"})
                      (c/map->CollectionAssociation {:short-name "S2"
                                                     :version-id "V1"})
                      (c/map->CollectionAssociation {:short-name "S1"
                                                     :version-id "V2"})]})))

  (testing "invalid collection associations"
    (testing "duplicate names"
      (let [coll (c/map->UmmCollection
                   {:collection-associations
                    [(c/map->CollectionAssociation {:short-name "S1"
                                                    :version-id "V1"})
                     (c/map->CollectionAssociation {:short-name "S2"
                                                    :version-id "V1"})
                     (c/map->CollectionAssociation {:short-name "S1"
                                                    :version-id "V1"})]})]
        (assert-invalid
          coll
          [:collection-associations]
          ["Collection Associations must be unique. This contains duplicates named [(ShortName [S1] & VersionId [V1])]."])))))


(deftest collection-science-keywords-validation
  (testing "valid collection science keywords"
    (assert-valid (c/map->UmmCollection
                    {:science-keywords
                     [(c/map->ScienceKeyword {:category "c1"
                                              :topic "t1"
                                              :term "term1"
                                              :variable-level-1 "v1"
                                              :variable-level-2 "v2"
                                              :variable-level-3 "v3"
                                              :detailed-variable "dv"})
                      (c/map->ScienceKeyword {:category "c2"
                                              :topic "t2"
                                              :term "term2"})]})))

  (testing "invalid collection science keywords"
    (testing "missing category"
      (let [coll (c/map->UmmCollection
                   {:science-keywords
                    [(c/map->ScienceKeyword {:topic "t1"
                                             :term "term1"})]})]
        (assert-invalid
          coll
          [:science-keywords 0 :category]
          ["Category is required."])))
    (testing "missing topic"
      (let [coll (c/map->UmmCollection
                   {:science-keywords
                    [(c/map->ScienceKeyword {:category "c1"
                                             :term "term1"})]})]
        (assert-invalid
          coll
          [:science-keywords 0 :topic]
          ["Topic is required."])))
    (testing "missing terms"
      (let [coll (c/map->UmmCollection
                   {:science-keywords
                    [(c/map->ScienceKeyword {:category "c1"
                                             :topic "t1"})]})]
        (assert-invalid
          coll
          [:science-keywords 0 :term]
          ["Term is required."])))
    (testing "multiple errors"
      (let [coll (c/map->UmmCollection
                   {:science-keywords
                    [(c/map->ScienceKeyword {:category "c1"
                                             :topic "t1"})
                     (c/map->ScienceKeyword {:category "c1"
                                             :term "term1"})]})]
        (assert-multiple-invalid
          coll
          [{:path [:science-keywords 0 :term]
            :errors
            ["Term is required."]}
           {:path [:science-keywords 1 :topic]
            :errors
            ["Topic is required."]}])))))
