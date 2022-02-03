(ns cmr.ingest.services.granule-bulk-update.checksum-size-format.echo10-test
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.ingest.services.granule-bulk-update.checksum.echo10 :as checksum]
   [cmr.ingest.services.granule-bulk-update.format.echo10 :as format]
   [cmr.ingest.services.granule-bulk-update.size.echo10 :as size]))

(def ^:private update-value-and-algorithm
  "ECHO10 granule for testing updating granule checksum value and algorithm."
  "<Granule>
    <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
    <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
    <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
    <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
    </Collection>
    <DataGranule>
      <Checksum>
        <Value>1ff38cc592c4c5d0c8e3ca38be8f1eb1</Value>
        <Algorithm>MD5</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
    </DataGranule>
  </Granule>")

(def ^:private update-value-and-algorithm-result
  "Result ECHO10 granule after updating granule checksum value and algorithm.
   Do not format the following as whitespace matters in the string comparison in the test."
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <DataGranule>
      <Checksum>
         <Value>foo</Value>
         <Algorithm>bar-32</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
   </DataGranule>
</Granule>\n")

(def ^:private update-value-only
  "ECHO10 granule for testing updating granule checksum value only."
  "<Granule>
    <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
    <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
    <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
    <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
    </Collection>
    <DataGranule>
      <Checksum>
        <Value>1ff38cc592c4c5d0c8e3ca38be8f1eb1</Value>
        <Algorithm>MD5</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
    </DataGranule>
  </Granule>")

(def ^:private update-value-only-result
  "Result ECHO10 granule after updating granule checksum value only.
   Do not format the following as whitespace matters in the string comparison in the test."
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <DataGranule>
      <Checksum>
         <Value>foo</Value>
         <Algorithm>MD5</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
   </DataGranule>
</Granule>\n")

(def ^:private add-checksum-element
  "ECHO10 granule for testing adding a fresh checksum element to the DataGranule element."
  "<Granule>
    <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
    <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
    <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
    <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
    </Collection>
    <DataGranule>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
    </DataGranule>
  </Granule>")

(def ^:private add-checksum-element-result
  "Result ECHO10 granule after adding a fresh checksum element to the DataGranule element.
   Do not format the following as whitespace matters in the string comparison in the test."
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <DataGranule>
      <Checksum>
         <Value>foo</Value>
         <Algorithm>SHA-256</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
   </DataGranule>
</Granule>\n")

(def ^:private add-checksum-element-middle
  "ECHO10 granule for testing adding a fresh checksum element to the DataGranule element
   between two existing values, to make sure schema order for DataGranule children is respected."
  "<Granule>
    <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
    <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
    <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
    <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
    </Collection>
    <DataGranule>
      <SizeMBDataGranule>24.7237091064453</SizeMBDataGranule>
      <Checksum>
         <Value>foo</Value>
         <Algorithm>SHA-256</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
    </DataGranule>
  </Granule>")

(def ^:private add-checksum-element-middle-result
  "Result ECHO10 granule after adding a fresh checksum element to the DataGranule element
   between two existing values, to make sure schema order for DataGranule children is respected.
   Do not format the following as whitespace matters in the string comparison in the test."
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <DataGranule>
      <SizeMBDataGranule>24.7237091064453</SizeMBDataGranule>
      <Checksum>
         <Value>fooValue</Value>
         <Algorithm>SHA-256</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
   </DataGranule>
</Granule>\n")

(def ^:private update-checksum-element-middle
  "ECHO10 granule for testing updating checksum, when it isn't the first element in the DataGranule."
  "<Granule>
    <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
    <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
    <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
    <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
    </Collection>
    <DataGranule>
      <SizeMBDataGranule>24.7237091064453</SizeMBDataGranule>
      <Checksum>
         <Value>old-foo</Value>
         <Algorithm>SHA-256</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
    </DataGranule>
  </Granule>")

(def ^:private update-checksum-element-middle-result
  "Result ECHO10 granule after updating checksum, when it isn't the first element in the DataGranule.
   Do not format the following as whitespace matters in the string comparison in the test."
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <DataGranule>
      <SizeMBDataGranule>24.7237091064453</SizeMBDataGranule>
      <Checksum>
         <Value>new-foo</Value>
         <Algorithm>Adler-32</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
   </DataGranule>
</Granule>\n")

(deftest update-checksum
  (testing "various cases of updating checksum"
    (are3 [checksum-value source result]
      (is (= result (#'checksum/update-checksum-metadata source checksum-value)))

      "Add both value and algorithm"
      "foo,bar-32"
      update-value-and-algorithm
      update-value-and-algorithm-result

      "Update value only, not algorithm"
      "foo"
      update-value-only
      update-value-only-result

      "Add values when there is no existing Checksum element, as new first data-granule element"
      "foo,SHA-256"
      add-checksum-element
      add-checksum-element-result

      "Add a checksum element between two existing data-granule elements"
      "fooValue,SHA-256"
      add-checksum-element-middle
      add-checksum-element-middle-result

      "Update a checksum element between two existing data-granule elements"
      "new-foo,Adler-32"
      update-checksum-element-middle
      update-checksum-element-middle-result)))

(def ^:private gran-1
  "ECHO10 granule for testing updating granule sizes in MB and bytes"
  "<Granule>
    <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
    <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
    <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
    <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
    </Collection>
    <DataGranule>
      <DataGranuleSizeInBytes>25</DataGranuleSizeInBytes>
      <SizeMBDataGranule>25.0</SizeMBDataGranule>
      <Checksum>
        <Value>1ff38cc592c4c5d0c8e3ca38be8f1eb1</Value>
        <Algorithm>MD5</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
    </DataGranule>
  </Granule>")

(def ^:private gran-1-update-both
  "Result ECHO10 granule after updating granule sizes in MB and bytes.
   Do not format the following as whitespace matters in the string comparison in the test."
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <DataGranule>
      <DataGranuleSizeInBytes>50</DataGranuleSizeInBytes>
      <SizeMBDataGranule>50.0</SizeMBDataGranule>
      <Checksum>
         <Value>1ff38cc592c4c5d0c8e3ca38be8f1eb1</Value>
         <Algorithm>MD5</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
   </DataGranule>
</Granule>\n")

(def ^:private gran-1-update-bytes
  "Result ECHO10 granule after updating granule sizes in bytes.
   Do not format the following as whitespace matters in the string comparison in the test."
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <DataGranule>
      <DataGranuleSizeInBytes>75</DataGranuleSizeInBytes>
      <SizeMBDataGranule>25.0</SizeMBDataGranule>
      <Checksum>
         <Value>1ff38cc592c4c5d0c8e3ca38be8f1eb1</Value>
         <Algorithm>MD5</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
   </DataGranule>
</Granule>\n")

(def ^:private gran-1-update-mb
  "Result ECHO10 granule after updating granule sizes in MB.
   Do not format the following as whitespace matters in the string comparison in the test."
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <DataGranule>
      <DataGranuleSizeInBytes>25</DataGranuleSizeInBytes>
      <SizeMBDataGranule>123.456</SizeMBDataGranule>
      <Checksum>
         <Value>1ff38cc592c4c5d0c8e3ca38be8f1eb1</Value>
         <Algorithm>MD5</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
   </DataGranule>
</Granule>\n")

(def ^:private gran-2
  "ECHO10 granule for testing updating granule sizes in MB and bytes"
  "<Granule>
    <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
    <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
    <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
    <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
    </Collection>
    <DataGranule>
      <DataGranuleSizeInBytes>25</DataGranuleSizeInBytes>
      <Checksum>
        <Value>1ff38cc592c4c5d0c8e3ca38be8f1eb1</Value>
        <Algorithm>MD5</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
    </DataGranule>
  </Granule>")

(def ^:private gran-2-update-bytes
  "ECHO10 granule for testing updating granule sizes in bytes"
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <DataGranule>
      <DataGranuleSizeInBytes>100</DataGranuleSizeInBytes>
      <Checksum>
         <Value>1ff38cc592c4c5d0c8e3ca38be8f1eb1</Value>
         <Algorithm>MD5</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
   </DataGranule>
</Granule>\n")

(def ^:private gran-2-update-and-add
  "ECHO10 granule for testing updating granule sizes in MB and bytes"
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <DataGranule>
      <DataGranuleSizeInBytes>123</DataGranuleSizeInBytes>
      <SizeMBDataGranule>8675.309</SizeMBDataGranule>
      <Checksum>
         <Value>1ff38cc592c4c5d0c8e3ca38be8f1eb1</Value>
         <Algorithm>MD5</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
   </DataGranule>
</Granule>\n")

(def ^:private gran-3
  "ECHO10 granule for testing updating granule sizes in MB and bytes"
  "<Granule>
    <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
    <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
    <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
    <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
    </Collection>
    <DataGranule>
      <SizeMBDataGranule>123.456</SizeMBDataGranule>
      <Checksum>
        <Value>1ff38cc592c4c5d0c8e3ca38be8f1eb1</Value>
        <Algorithm>MD5</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
    </DataGranule>
  </Granule>")

(def ^:private gran-3-update-mb
  "ECHO10 granule for testing updating granule sizes in MB"
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <DataGranule>
      <SizeMBDataGranule>45.6</SizeMBDataGranule>
      <Checksum>
         <Value>1ff38cc592c4c5d0c8e3ca38be8f1eb1</Value>
         <Algorithm>MD5</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
   </DataGranule>
</Granule>\n")

(def ^:private gran-3-update-both
  "ECHO10 granule for testing updating granule sizes in bytes"
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <DataGranule>
      <DataGranuleSizeInBytes>6000</DataGranuleSizeInBytes>
      <SizeMBDataGranule>45.6</SizeMBDataGranule>
      <Checksum>
         <Value>1ff38cc592c4c5d0c8e3ca38be8f1eb1</Value>
         <Algorithm>MD5</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
   </DataGranule>
</Granule>\n")

(def ^:private gran-4
  "ECHO10 granule for testing updating granule sizes in MB and byte."
  "<Granule>
    <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
    <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
    <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
    <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
    </Collection>
    <DataGranule>
      <Checksum>
        <Value>1ff38cc592c4c5d0c8e3ca38be8f1eb1</Value>
        <Algorithm>MD5</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
    </DataGranule>
  </Granule>")

(def ^:private gran-4-add-mb
  "ECHO10 granule for testing updating granule sizes in MB"
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <DataGranule>
      <SizeMBDataGranule>22.222</SizeMBDataGranule>
      <Checksum>
         <Value>1ff38cc592c4c5d0c8e3ca38be8f1eb1</Value>
         <Algorithm>MD5</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
   </DataGranule>
</Granule>\n")

(def ^:private gran-4-add-bytes
  "ECHO10 granule for testing updating granule sizes in bytes"
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <DataGranule>
      <DataGranuleSizeInBytes>222</DataGranuleSizeInBytes>
      <Checksum>
         <Value>1ff38cc592c4c5d0c8e3ca38be8f1eb1</Value>
         <Algorithm>MD5</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
   </DataGranule>
</Granule>\n")

(def ^:private gran-4-add-both
  "ECHO10 granule for testing updating granule sizes in MB and bytes"
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <DataGranule>
      <DataGranuleSizeInBytes>222</DataGranuleSizeInBytes>
      <SizeMBDataGranule>22.222</SizeMBDataGranule>
      <Checksum>
         <Value>1ff38cc592c4c5d0c8e3ca38be8f1eb1</Value>
         <Algorithm>MD5</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
   </DataGranule>
</Granule>\n")

(def ^:private gran-5
  "ECHO10 granule for testing updating granule sizes in MB when existing val is integer"
  "<Granule>
    <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
    <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
    <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
    <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
    </Collection>
    <DataGranule>
      <SizeMBDataGranule>400</SizeMBDataGranule>
      <Checksum>
        <Value>1ff38cc592c4c5d0c8e3ca38be8f1eb1</Value>
        <Algorithm>MD5</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
    </DataGranule>
  </Granule>")

(def ^:private gran-5-update-mb
  "ECHO10 granule for testing updating granule sizes in MB"
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <DataGranule>
      <SizeMBDataGranule>500.45</SizeMBDataGranule>
      <Checksum>
         <Value>1ff38cc592c4c5d0c8e3ca38be8f1eb1</Value>
         <Algorithm>MD5</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
   </DataGranule>
</Granule>\n")

(deftest update-size
  (testing "various cases of updating size"
    (are3 [size-value source result]
      (is (= result (#'size/update-size-metadata source size-value)))

      "Update both sizes"
      "50,50.0"
      gran-1
      gran-1-update-both

      "Update both sizes, reverse input"
      "50,50.0"
      gran-1
      gran-1-update-both

      "Update just bytes"
      "75"
      gran-1
      gran-1-update-bytes

      "Update just MB"
      "123.456"
      gran-1
      gran-1-update-mb

      "Update just bytes"
      "100"
      gran-2
      gran-2-update-bytes

      "Update bytes and add MB"
      "123,8675.309"
      gran-2
      gran-2-update-and-add

      "Update just MB"
      "45.6"
      gran-3
      gran-3-update-mb

      "Update MB and add bytes"
      "45.6,6000"
      gran-3
      gran-3-update-both

      "Add MB"
      "22.222"
      gran-4
      gran-4-add-mb

      "Add bytes"
      "222"
      gran-4
      gran-4-add-bytes

      "Add both"
      "222,22.222"
      gran-4
      gran-4-add-both

      "Update a granule whose exist MB value is an integer"
      "500.45"
      gran-5
      gran-5-update-mb)))

(def ^:private gran-without-format
  "ECHO10 granule for testing adding format as final element"
  "<Granule>
    <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
    <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
    <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
    <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
    </Collection>
    <DataGranule>
      <Checksum>
        <Value>1ff38cc592c4c5d0c8e3ca38be8f1eb1</Value>
        <Algorithm>MD5</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
    </DataGranule>
  </Granule>")

(def ^:private updated-gran-without-format
  "ECHO10 granule for testing adding format as final element"
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <DataGranule>
      <Checksum>
         <Value>1ff38cc592c4c5d0c8e3ca38be8f1eb1</Value>
         <Algorithm>MD5</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
   </DataGranule>
   <DataFormat>NET-CDF</DataFormat>
</Granule>\n")

(def ^:private gran-without-format-middle
  "ECHO10 granule for testing adding format"
  "<Granule>
    <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
    <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
    <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
    <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
    </Collection>
    <DataGranule>
      <Checksum>
        <Value>1ff38cc592c4c5d0c8e3ca38be8f1eb1</Value>
        <Algorithm>MD5</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
    </DataGranule>
    <Visible>true</Visible>
  </Granule>")

(def ^:private updated-gran-without-format-middle
  "ECHO10 granule for testing adding format"
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <DataGranule>
      <Checksum>
         <Value>1ff38cc592c4c5d0c8e3ca38be8f1eb1</Value>
         <Algorithm>MD5</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
   </DataGranule>
   <DataFormat>MP3</DataFormat>
   <Visible>true</Visible>
</Granule>\n")

(def ^:private gran-with-format
  "ECHO10 granule for testing updating format"
  "<Granule>
    <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
    <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
    <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
    <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
    </Collection>
    <DataGranule>
      <Checksum>
        <Value>1ff38cc592c4c5d0c8e3ca38be8f1eb1</Value>
        <Algorithm>MD5</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
    </DataGranule>
    <DataFormat>ZIP</DataFormat>
  </Granule>")

(def ^:private updated-gran-with-format
  "ECHO10 granule for testing updating format"
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Granule>
   <GranuleUR>Q2011143115400.L1A_SCI</GranuleUR>
   <InsertTime>2011-08-26T11:10:44.490Z</InsertTime>
   <LastUpdate>2011-08-26T16:17:55.232Z</LastUpdate>
   <Collection>
      <EntryId>AQUARIUS_L1A_SSS</EntryId>
   </Collection>
   <DataGranule>
      <Checksum>
         <Value>1ff38cc592c4c5d0c8e3ca38be8f1eb1</Value>
         <Algorithm>MD5</Algorithm>
      </Checksum>
      <DayNightFlag>UNSPECIFIED</DayNightFlag>
      <ProductionDateTime>2018-02-06T19:13:22Z</ProductionDateTime>
   </DataGranule>
   <DataFormat>XML</DataFormat>
</Granule>\n")

(deftest update-format
  (testing "various cases of updating size"
    (are3 [format source result]
      (is (= result (#'format/update-format-metadata source format)))

      "Add DataFormat to a granule without it (final element)"
      "NET-CDF"
      gran-without-format
      updated-gran-without-format

      "Add DataFormat to a granule without it"
      "MP3"
      gran-without-format-middle
      updated-gran-without-format-middle

      "Update DataFormat in a granule that already has the element"
      "XML"
      gran-with-format
      updated-gran-with-format)))
