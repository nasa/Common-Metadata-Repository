;; gorilla-repl.fileformat = 1

;; **
;;; # Sprint 68 Demos
;; **

;; @@
(ns sprint68
  (:require
   [cmr.demos.helpers :refer [curl+ open login head quiet-logging highlights]]
   [cmr.demos.populate :refer [reset]]
   [cmr.system-int-test.utils.ingest-util :as ingest]))
;; @@

;; **
;;; ## James - CMR-XXXX
;; **

;; @@

;; @@

;; **
;;; ## Lauren - CMR-XXXX
;; **

;; @@

;; @@

;; **
;;; ## Siwei - CMR-XXXX
;; **

;; @@

;; @@

;; **
;;; ## Tim - CMR-XXXX
;; **

;; @@

;; @@

;; **
;;; ## Daniel - CMR-XXXX
;; **

;; @@

;; @@

;; **
;;; ## John - CMR-XXXX
;; **

;; @@

;; @@

;; **
;;; ## Chris - CMR-XXXX
;; **

;; @@

;; @@

;; **
;;; ## Leo - CMR-XXXX
;; **

;; @@
(do
  (reset)
  (ingest/create-provider {:provider-guid "provguid1" :provider-id "PROV1"})

  (curl+ -XPUT -H "Cmr-Pretty:true" -H "Content-Type:application/echo10+xml"
         http://localhost:3002/providers/PROV1/collections/coll1 -d
         "<Collection>
          <ShortName>S1</ShortName>
          <VersionId>001</VersionId>
          <InsertTime>1970-01-01T00:00:00</InsertTime>
          <LastUpdate>1970-01-01T00:00:00</LastUpdate>
          <LongName>dummy-long-name</LongName>
          <DataSetId>coll1</DataSetId>
          <Description>Not provided</Description>
          <Orderable>true</Orderable>
          <Visible>true</Visible>
          <ProcessingLevelId>Level 1</ProcessingLevelId>
          <ArchiveCenter>NASA/NSIDC_DAAC</ArchiveCenter>
          <Platforms>
            <Platform>
              <ShortName>ERS-1</ShortName>
              <LongName>European Remote Sensing Satellite-1</LongName>
              <Type>Spacecraft</Type>
            </Platform>
          </Platforms>
          </Collection>")
  (curl+ -XPUT -H "Cmr-Pretty:true" -H "Content-Type:application/echo10+xml"
         http://localhost:3002/providers/PROV1/collections/coll2 -d
         "<Collection>
          <ShortName>S2</ShortName>
          <VersionId>001</VersionId>
          <InsertTime>1970-01-01T00:00:00</InsertTime>
          <LastUpdate>1970-01-01T00:00:00</LastUpdate>
          <LongName>dummy-long-name</LongName>
          <DataSetId>coll2</DataSetId>
          <Description>Not provided</Description>
          <Orderable>true</Orderable>
          <Visible>true</Visible>
          <ProcessingLevelId>Level 1</ProcessingLevelId>
          <ArchiveCenter>NASA/NSIDC_DAAC</ArchiveCenter>
          <Platforms>
            <Platform>
              <ShortName>ERS-2</ShortName>
              <LongName>European Remote Sensing Satellite-2</LongName>
              <Type>Spacecraft</Type>
            </Platform>
          </Platforms>
          </Collection>")
  (curl+ -XPUT -H "Cmr-Pretty:true" -H "Content-Type:application/echo10+xml"
         http://localhost:3002/providers/PROV1/collections/coll3 -d
         "<Collection>
          <ShortName>S3</ShortName>
          <VersionId>001</VersionId>
          <InsertTime>1970-01-01T00:00:00</InsertTime>
          <LastUpdate>1970-01-01T00:00:00</LastUpdate>
          <LongName>dummy-long-name</LongName>
          <DataSetId>coll3</DataSetId>
          <Description>Not provided</Description>
          <Orderable>true</Orderable>
          <Visible>true</Visible>
          <ProcessingLevelId>Level 1</ProcessingLevelId>
          <ArchiveCenter>NASA/NSIDC_DAAC</ArchiveCenter>
          <Platforms>
            <Platform>
              <ShortName>ERS-1</ShortName>
              <LongName>European Remote Sensing Satellite-1</LongName>
              <Type>Spacecraft</Type>
            </Platform>
            <Platform>
              <ShortName>ERS-2</ShortName>
              <LongName>European Remote Sensing Satellite-2</LongName>
              <Type>Spacecraft</Type>
              <Instruments>
              <Instrument>
              <ShortName>INS-3</ShortName>
              </Instrument>
              </Instruments>
            </Platform>
          </Platforms>
          </Collection>")
  (curl+ -XPUT -H "Cmr-Pretty:true" -H "Content-Type:application/echo10+xml"
         http://localhost:3002/providers/PROV1/collections/coll4 -d
         "<Collection>
          <ShortName>S4</ShortName>
          <VersionId>001</VersionId>
          <InsertTime>1970-01-01T00:00:00</InsertTime>
          <LastUpdate>1970-01-01T00:00:00</LastUpdate>
          <LongName>dummy-long-name</LongName>
          <DataSetId>coll4</DataSetId>
          <Description>Not provided</Description>
          <Orderable>true</Orderable>
          <Visible>true</Visible>
          <ProcessingLevelId>Level 1</ProcessingLevelId>
          <ArchiveCenter>NASA/NSIDC_DAAC</ArchiveCenter>
          <Platforms>
            <Platform>
              <ShortName>ERS-4</ShortName>
              <LongName>European Remote Sensing Satellite-2</LongName>
              <Type>Spacecraft</Type>
              <Instruments>
              <Instrument>
              <ShortName>INS-4</ShortName>
              </Instrument>
              </Instruments>
            </Platform>
          </Platforms>
          </Collection>")
  )
;; @@

;; **
;;; Verify searching by one platform value returns platforms facets for all collections,
;;; i.e. ERS-1 2, ERS-2 2, ERS-4 1
;;; Also notice that only Instrument INS-4 which matches coll4 is listed in the facets
;; **

;; @@
(curl+ -H "Cmr-Pretty:true"
       "http://localhost:3003/collections.json?platform=ERS-4&include_facets=v2")
;; @@

;; **
;;; Verify searching by two platform values returns platforms facets for all collections,
;;; i.e. ERS-1 2, ERS-2 2, ERS-4 1
;;; Also notice that only Instrument INS-3 which matches coll3 is listed in the facets
;; **

;; @@
(curl+ -g -H "Cmr-Pretty:true"
       "http://localhost:3003/collections.json?platform[]=ERS-1&platform[]=ERS-2&include_facets=v2")
;; @@

;; **
;;; Verify searching by regular parameters does filters the platforms facets returned
;;; In this case, only platforms in coll1 (i.e. ERS-1) is present in the platform facets.
;; **

;; @@
(curl+ -g -H "Cmr-Pretty:true"
       "http://localhost:3003/collections.json?platform[]=ERS-1&short_name=S1&include_facets=v2")
;; @@

;; **
;;; Verify searching by instrument value also filters platforms facets
;; **

;; @@
(curl+ -g -H "Cmr-Pretty:true"
       "http://localhost:3003/collections.json?instrument[]=INS-4&include_facets=v2")
;; @@
;; @@

;; @@

;; **
;;; ## Jason - CMR-XXXX
;; **

;; @@

;; @@

;; **
;;; ## Mark - CMR-XXXX
;; **

;; @@

;; @@
