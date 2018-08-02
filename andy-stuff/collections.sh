#!/bin/bash

COLLECTIONS=\
"<Collection>
    <ShortName>SEASAT_SAR_L1_TIFF</ShortName>
    <VersionId>1</VersionId>
    <InsertTime>2013-06-27T20:15:18Z</InsertTime>
    <LastUpdate>2013-09-12T14:55:21Z</LastUpdate>
    <LongName>SEASAT Image GeoTIFF</LongName>
    <DataSetId>SEASAT_SAR_LEVEL1_GEOTIFF</DataSetId>
    <Description>SEASAT Image GeoTIFF</Description>
    <Orderable>false</Orderable>
    <Visible>true</Visible>
    <ProcessingCenter>Alaska Satellite Facility</ProcessingCenter>
    <ProcessingLevelId>1</ProcessingLevelId>
    <ProcessingLevelDescription>SEASAT image data processed as georeferenced products.</ProcessingLevelDescription>
    <ArchiveCenter>Alaska Satellite Facility</ArchiveCenter>
    <CollectionState>ACTIVE</CollectionState>
    <Temporal>
        <TimeType>UTC</TimeType>
        <DateType>Gregorian</DateType>
        <TemporalRangeType>Continuous Range</TemporalRangeType>
        <PrecisionOfSeconds>1</PrecisionOfSeconds>
        <EndsAtPresentFlag>false</EndsAtPresentFlag>
        <RangeDateTime>
            <BeginningDateTime>1978-07-04T12:05:47Z</BeginningDateTime>
            <EndingDateTime>1978-10-11T01:29:10Z</EndingDateTime>
        </RangeDateTime>
    </Temporal>
    <Contacts>
        <Contact>
            <Role>User Services</Role>
            <OrganizationPhones>
                <Phone>
                    <Number>907-474-5041</Number>
                    <Type>Primary</Type>
                </Phone>
            </OrganizationPhones>
            <OrganizationEmails>
                <Email>uso@asf.alaska.edu</Email>
            </OrganizationEmails>
        </Contact>
    </Contacts>
    <ScienceKeywords>
        <ScienceKeyword>
            <CategoryKeyword>EARTH SCIENCE</CategoryKeyword>
            <TopicKeyword>CRYOSPHERE</TopicKeyword>
            <TermKeyword>SEA ICE</TermKeyword>
            <VariableLevel1Keyword>
                <Value>ICE EXTENT</Value>
            </VariableLevel1Keyword>
        </ScienceKeyword>
        <ScienceKeyword>
            <CategoryKeyword>EARTH SCIENCE</CategoryKeyword>
            <TopicKeyword>CRYOSPHERE</TopicKeyword>
            <TermKeyword>SEA ICE</TermKeyword>
            <VariableLevel1Keyword>
                <Value>LEADS</Value>
            </VariableLevel1Keyword>
        </ScienceKeyword>
        <ScienceKeyword>
            <CategoryKeyword>EARTH SCIENCE</CategoryKeyword>
            <TopicKeyword>LAND SURFACE</TopicKeyword>
            <TermKeyword>GEOMORPHIC LANDFORMS/PROCESSES</TermKeyword>
            <VariableLevel1Keyword>
                <Value>COASTAL LANDFORMS</Value>
            </VariableLevel1Keyword>
        </ScienceKeyword>
        <ScienceKeyword>
            <CategoryKeyword>EARTH SCIENCE</CategoryKeyword>
            <TopicKeyword>LAND SURFACE</TopicKeyword>
            <TermKeyword>GEOMORPHIC LANDFORMS/PROCESSES</TermKeyword>
            <VariableLevel1Keyword>
                <Value>FLUVIAL LANDFORMS</Value>
            </VariableLevel1Keyword>
        </ScienceKeyword>
        <ScienceKeyword>
            <CategoryKeyword>EARTH SCIENCE</CategoryKeyword>
            <TopicKeyword>LAND SURFACE</TopicKeyword>
            <TermKeyword>GEOMORPHIC LANDFORMS/PROCESSES</TermKeyword>
            <VariableLevel1Keyword>
                <Value>GLACIAL LANDFORMS</Value>
            </VariableLevel1Keyword>
        </ScienceKeyword>
        <ScienceKeyword>
            <CategoryKeyword>EARTH SCIENCE</CategoryKeyword>
            <TopicKeyword>OCEANS</TopicKeyword>
            <TermKeyword>COASTAL PROCESSES</TermKeyword>
            <VariableLevel1Keyword>
                <Value>ESTUARIES</Value>
            </VariableLevel1Keyword>
        </ScienceKeyword>
        <ScienceKeyword>
            <CategoryKeyword>EARTH SCIENCE</CategoryKeyword>
            <TopicKeyword>OCEANS</TopicKeyword>
            <TermKeyword>COASTAL PROCESSES</TermKeyword>
            <VariableLevel1Keyword>
                <Value>FJORDS</Value>
            </VariableLevel1Keyword>
        </ScienceKeyword>
        <ScienceKeyword>
            <CategoryKeyword>EARTH SCIENCE</CategoryKeyword>
            <TopicKeyword>OCEANS</TopicKeyword>
            <TermKeyword>COASTAL PROCESSES</TermKeyword>
            <VariableLevel1Keyword>
                <Value>INLETS</Value>
            </VariableLevel1Keyword>
        </ScienceKeyword>
        <ScienceKeyword>
            <CategoryKeyword>EARTH SCIENCE</CategoryKeyword>
            <TopicKeyword>OCEANS</TopicKeyword>
            <TermKeyword>COASTAL PROCESSES</TermKeyword>
            <VariableLevel1Keyword>
                <Value>LAGOONS</Value>
            </VariableLevel1Keyword>
        </ScienceKeyword>
        <ScienceKeyword>
            <CategoryKeyword>EARTH SCIENCE</CategoryKeyword>
            <TopicKeyword>OCEANS</TopicKeyword>
            <TermKeyword>COASTAL PROCESSES</TermKeyword>
            <VariableLevel1Keyword>
                <Value>MANGROVES</Value>
            </VariableLevel1Keyword>
        </ScienceKeyword>
        <ScienceKeyword>
            <CategoryKeyword>EARTH SCIENCE</CategoryKeyword>
            <TopicKeyword>OCEANS</TopicKeyword>
            <TermKeyword>COASTAL PROCESSES</TermKeyword>
            <VariableLevel1Keyword>
                <Value>MARSHES</Value>
            </VariableLevel1Keyword>
        </ScienceKeyword>
        <ScienceKeyword>
            <CategoryKeyword>EARTH SCIENCE</CategoryKeyword>
            <TopicKeyword>OCEANS</TopicKeyword>
            <TermKeyword>COASTAL PROCESSES</TermKeyword>
            <VariableLevel1Keyword>
                <Value>ROCKY COASTS</Value>
            </VariableLevel1Keyword>
        </ScienceKeyword>
        <ScienceKeyword>
            <CategoryKeyword>EARTH SCIENCE</CategoryKeyword>
            <TopicKeyword>OCEANS</TopicKeyword>
            <TermKeyword>COASTAL PROCESSES</TermKeyword>
            <VariableLevel1Keyword>
                <Value>SHORELINES</Value>
            </VariableLevel1Keyword>
        </ScienceKeyword>
        <ScienceKeyword>
            <CategoryKeyword>EARTH SCIENCE</CategoryKeyword>
            <TopicKeyword>OCEANS</TopicKeyword>
            <TermKeyword>OCEAN WAVES</TermKeyword>
            <VariableLevel1Keyword>
                <Value>WIND WAVES</Value>
            </VariableLevel1Keyword>
        </ScienceKeyword>
        <ScienceKeyword>
            <CategoryKeyword>EARTH SCIENCE</CategoryKeyword>
            <TopicKeyword>OCEANS</TopicKeyword>
            <TermKeyword>OCEAN WINDS</TermKeyword>
            <VariableLevel1Keyword>
                <Value>SURFACE WINDS</Value>
            </VariableLevel1Keyword>
        </ScienceKeyword>
        <ScienceKeyword>
            <CategoryKeyword>EARTH SCIENCE</CategoryKeyword>
            <TopicKeyword>OCEANS</TopicKeyword>
            <TermKeyword>SEA ICE</TermKeyword>
            <VariableLevel1Keyword>
                <Value>ICE DEFORMATION</Value>
            </VariableLevel1Keyword>
        </ScienceKeyword>
        <ScienceKeyword>
            <CategoryKeyword>EARTH SCIENCE</CategoryKeyword>
            <TopicKeyword>OCEANS</TopicKeyword>
            <TermKeyword>SEA ICE</TermKeyword>
            <VariableLevel1Keyword>
                <Value>ICE DEPTH/THICKNESS</Value>
            </VariableLevel1Keyword>
        </ScienceKeyword>
        <ScienceKeyword>
            <CategoryKeyword>EARTH SCIENCE</CategoryKeyword>
            <TopicKeyword>SOLID EARTH</TopicKeyword>
            <TermKeyword>GEOMORPHIC LANDFORMS/PROCESSES</TermKeyword>
            <VariableLevel1Keyword>
                <Value>COASTAL LANDFORMS</Value>
            </VariableLevel1Keyword>
        </ScienceKeyword>
        <ScienceKeyword>
            <CategoryKeyword>EARTH SCIENCE</CategoryKeyword>
            <TopicKeyword>SOLID EARTH</TopicKeyword>
            <TermKeyword>GEOMORPHIC LANDFORMS/PROCESSES</TermKeyword>
            <VariableLevel1Keyword>
                <Value>FLUVIAL LANDFORMS</Value>
            </VariableLevel1Keyword>
        </ScienceKeyword>
        <ScienceKeyword>
            <CategoryKeyword>EARTH SCIENCE</CategoryKeyword>
            <TopicKeyword>SOLID EARTH</TopicKeyword>
            <TermKeyword>GEOMORPHIC LANDFORMS/PROCESSES</TermKeyword>
            <VariableLevel1Keyword>
                <Value>GLACIAL LANDFORMS</Value>
            </VariableLevel1Keyword>
        </ScienceKeyword>
        <ScienceKeyword>
            <CategoryKeyword>EARTH SCIENCE</CategoryKeyword>
            <TopicKeyword>SOLID EARTH</TopicKeyword>
            <TermKeyword>GEOMORPHIC LANDFORMS/PROCESSES</TermKeyword>
            <VariableLevel1Keyword>
                <Value>TECTONIC LANDFORMS</Value>
            </VariableLevel1Keyword>
        </ScienceKeyword>
        <ScienceKeyword>
            <CategoryKeyword>EARTH SCIENCE</CategoryKeyword>
            <TopicKeyword>SOLID EARTH</TopicKeyword>
            <TermKeyword>TECTONICS</TermKeyword>
            <VariableLevel1Keyword>
                <Value>PLATE TECTONICS</Value>
            </VariableLevel1Keyword>
        </ScienceKeyword>
        <ScienceKeyword>
            <CategoryKeyword>EARTH SCIENCE</CategoryKeyword>
            <TopicKeyword>SOLID EARTH</TopicKeyword>
            <TermKeyword>TECTONICS</TermKeyword>
            <VariableLevel1Keyword>
                <Value>VOLCANIC ACTIVITY</Value>
            </VariableLevel1Keyword>
        </ScienceKeyword>
    </ScienceKeywords>
    <Platforms>
        <Platform>
            <ShortName>SEASAT 1</ShortName>
            <LongName>Ocean Dynamics Satellite</LongName>
            <Type>Spacecraft</Type>
            <Instruments>
                <Instrument>
                    <ShortName>SAR</ShortName>
                    <LongName>Synthetic Aperture Radar</LongName>
                    <Sensors>
                        <Sensor>
                            <ShortName>STD</ShortName>
                        </Sensor>
                    </Sensors>
                    <OperationModes>
                        <OperationMode>Arctic</OperationMode>
                        <OperationMode>Antarctic</OperationMode>
                    </OperationModes>
                </Instrument>
            </Instruments>
        </Platform>
    </Platforms>
    <AdditionalAttributes>
        <AdditionalAttribute>
            <Name>FLIGHT_LINE</Name>
            <DataType>STRING</DataType>
            <Description>Flight line identifies where the aircraft/instument has flown to acquire data.</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>GROUP_ID</Name>
            <DataType>STRING</DataType>
            <Description>ID that helps ASF group products</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>OFF_NADIR_ANGLE</Name>
            <DataType>FLOAT</DataType>
            <Description>The angle the sensor points away from the satellite's nadir (off to the side. Larger angles correspond to imaging farther away from the satellite.</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>MD5SUM</Name>
            <DataType>STRING</DataType>
            <Description>Checksum</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>GRANULE_TYPE</Name>
            <DataType>STRING</DataType>
            <Description>Identifies the type of data by combining platform, beam mode type, and coverage (frame/swath</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>ASCENDING_DESCENDING</Name>
            <DataType>STRING</DataType>
            <Description>Describes whether the satellite travel direction was ascending towards the north pole, or descending towards the south pole.</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>FAR_END_LAT</Name>
            <DataType>FLOAT</DataType>
            <Description>Latitude of the granule where imaging ended farthest from the satellite. For an ascending satellite, it locates the upper right corner of a north-oriented image.</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>INSAR_STACK_SIZE</Name>
            <DataType>INT</DataType>
            <Description>The number of SAR images over the same location in the entire ASF archive. Used for data that may be used for interferometry.</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>BEAM_MODE_TYPE</Name>
            <DataType>STRING</DataType>
            <Description>In most cases the same as beam mode. For Radarsat indicates both resolution and off-nadir pointing angle. Standard beam mode becomes ST1 though ST7 beammodetype, with ST7 pointing farthest from the satellite.</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>INSAR_BASELINE</Name>
            <DataType>FLOAT</DataType>
            <Description>The perpendicular distance between two satellites when they took the related two images. Useful for identifying image pairs to use for interferometry.</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>CENTER_FRAME_ID</Name>
            <DataType>STRING</DataType>
            <Description>ID of the center frame</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>CENTER_ESA_FRAME</Name>
            <DataType>INT</DataType>
            <Description>The Europenan Space Agency equivalent to the value in the FRAME_NUMBER field</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>ACQUISITION_DATE</Name>
            <DataType>STRING</DataType>
            <Description>Date the data was acquired by the satellite or aircraft + instrument.</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>MISSION_NAME</Name>
            <DataType>STRING</DataType>
            <Description>The sitenames where AIRSAR, AIMOSS, and UAVSAR acqusitions were taken.</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>CENTER_LON</Name>
            <DataType>FLOAT</DataType>
            <Description>Longitude of the center of the scene or frame.</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>NEAR_START_LAT</Name>
            <DataType>FLOAT</DataType>
            <Description>Latitude of the granule where imaging began nearest to the satellite. For an ascending satellite, it locates the lower left corner of a north-oriented image.</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>BEAM_MODE</Name>
            <DataType>STRING</DataType>
            <Description>The general type of active sensor used. Values indicate image resolution: such as standard, fine, wide, scansar wide, SM (strip mode, EW(extra wide. OR indicate resolution + polarization: FBS (fine beam dual polarization, FBD (fine beam dual pol. OR atipically the type of product: TOPSAR (high resolution DEM, RPI (repeat pass interferometry, XTI (across track interferometry. OR resolution and product type IW (interferometric wide.</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>BEAM_MODE_DESC</Name>
            <DataType>STRING</DataType>
            <Description>Text that describes the beam mode</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>PROCESSING_TYPE</Name>
            <DataType>STRING</DataType>
            <Description>Indicates the type of product, such as browse, metadata, L1, complex, amplitude, projected, terrain corrected.</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>PROCESSING_DESCRIPTION</Name>
            <DataType>STRING</DataType>
            <Description>Describes what the data was processed to. For example: low resolution terrain corrected ALOS data.</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>FRAME_NUMBER</Name>
            <DataType>INT</DataType>
            <Description>The designated frame or scene as defined by various space agencies (ESA, JAXA, CSA each of which have their own scheme. The frame describes a box over the Earth at a fixed latitude. Combined with a path number (for longitude it identifies a specific piece of ground.</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>PROCESSING_LEVEL</Name>
            <DataType>STRING</DataType>
            <Description>Indicates how much processing has been done to the data: L1, L1.0 unprocessed; L1.1 single look complex; L1, L1.5 amplitude image</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>PROCESSING_DATE</Name>
            <DataType>STRING</DataType>
            <Description>The date the data was processed</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>NEAR_START_LON</Name>
            <DataType>FLOAT</DataType>
            <Description>Longitude of the granule where imaging began nearest the satellite. For an ascending satellite, it locates the lower left corner of a north-oriented image.</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>DOPPLER</Name>
            <DataType>FLOAT</DataType>
            <Description>The doppler centroid frequency - Useful for sar processing and interferometry. Related to the squint of the satellite.</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>FAR_START_LAT</Name>
            <DataType>FLOAT</DataType>
            <Description>Latitude of the granule where imaging began farthest from the satellite. For an ascending satellite, it locates the lower right corner of a north-oriented image.</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>NEAR_END_LON</Name>
            <DataType>FLOAT</DataType>
            <Description>Longitude of the granule where imaging ended nearest to the satellite. For an ascending satellite, it locates the upper left corner of a north-oriented image.</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>PROCESSING_TYPE_DISPLAY</Name>
            <DataType>STRING</DataType>
            <Description>Label that will be displayed in ASF's Vertex interface</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>POLARIZATION</Name>
            <DataType>STRING</DataType>
            <Description>Radar transmissions are polarized, with components normally termed vertical (V and horizontal (H. Vertical means that the electric vector is in the plane of incidence; horizontal means the electric vector is perpendicular to the plane of incidence.</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>FAR_START_LON</Name>
            <DataType>FLOAT</DataType>
            <Description>Latitude of the granule where imaging began farthest from the satellite. For an ascending satellite, it locates the lower right corner of a north-oriented image.</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>THUMBNAIL_URL</Name>
            <DataType>STRING</DataType>
            <Description>URL that points to this granule's thumbnail image</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>ASF_PLATFORM</Name>
            <DataType>STRING</DataType>
            <Description>Internal ASF platform name</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>INSAR_STACK_ID</Name>
            <DataType>INT</DataType>
            <Description>An ASF-assigned unique number used internally to unifiy SAR images over the same location. Used for data that might be used for interferometry.</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>LOOK_DIRECTION</Name>
            <DataType>STRING</DataType>
            <Description>SAR imagery is taken with the sensor pointing either left or right of the satellite. Most data is right-looking. A small amount of Radarsat is left-looking.</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>PATH_NUMBER</Name>
            <DataType>INT</DataType>
            <Description>The path describes a path the satellite will follow over the earth similar to longitude; each path has an assigned number. Combined with a frame number is identifies a specific piece of ground.</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>NEAR_END_LAT</Name>
            <DataType>FLOAT</DataType>
            <Description>Latitude of the granule where imaging ended nearest to the satellite. For an ascending satellite, it locates the upper left corner of a north-oriented image.</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>FARADAY_ROTATION</Name>
            <DataType>STRING</DataType>
            <Description>An effect of the ionisphere that rotates polarizations from HH or VV or HV or VH. Significant for L or P band. Rotation over 5 degrees reduces usefulness for applications such as forest biomass. Effect is reduced at solar minimum.</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>FAR_END_LON</Name>
            <DataType>FLOAT</DataType>
            <Description>Longitude of the granule where imaging ended farthest from the satellite. For an ascending satellite, it locates the upper right corner of a north-oriented image.</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>BYTES</Name>
            <DataType>FLOAT</DataType>
            <Description>Product size in bytes.</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>CENTER_LAT</Name>
            <DataType>FLOAT</DataType>
            <Description>Latitude of the center of the scene or frame.</Description>
        </AdditionalAttribute>
        <AdditionalAttribute>
            <Name>DEM_REGION</Name>
            <DataType>STRING</DataType>
            <Description>The DEM_REGION for this collection</Description>
        </AdditionalAttribute>
    </AdditionalAttributes>
    <OnlineAccessURLs>
        <OnlineAccessURL>
            <URL>https://vertex.daac.asf.alaska.edu/</URL>
            <URLDescription>The ASF Search and Ordering Interface</URLDescription>
        </OnlineAccessURL>
    </OnlineAccessURLs>
    <AssociatedDIFs>
        <DIF>
            <EntryId>ASF00SS</EntryId>
        </DIF>
    </AssociatedDIFs>
    <Spatial>
        <HorizontalSpatialDomain>
            <Geometry>
                <CoordinateSystem>GEODETIC</CoordinateSystem>
                <GPolygon>
                    <Boundary>
                        <Point>
                            <PointLongitude>21.445312</PointLongitude>
                            <PointLatitude>38.272689</PointLatitude>
                        </Point>
                        <Point>
                            <PointLongitude>14.765625</PointLongitude>
                            <PointLatitude>33.724340</PointLatitude>
                        </Point>
                        <Point>
                            <PointLongitude>-15.117188</PointLongitude>
                            <PointLatitude>24.206890</PointLatitude>
                        </Point>
                        <Point>
                            <PointLongitude>-72.070312</PointLongitude>
                            <PointLatitude>3.162456</PointLatitude>
                        </Point>
                        <Point>
                            <PointLongitude>-88.242188</PointLongitude>
                            <PointLatitude>2.811371</PointLatitude>
                        </Point>
                        <Point>
                            <PointLongitude>-143.085938</PointLongitude>
                            <PointLatitude>21.943046</PointLatitude>
                        </Point>
                        <Point>
                            <PointLongitude>163.125000</PointLongitude>
                            <PointLatitude>53.540307</PointLatitude>
                        </Point>
                        <Point>
                            <PointLongitude>156.796875</PointLongitude>
                            <PointLatitude>64.472794</PointLatitude>
                        </Point>
                        <Point>
                            <PointLongitude>164.882812</PointLongitude>
                            <PointLatitude>72.395706</PointLatitude>
                        </Point>
                        <Point>
                            <PointLongitude>-168.398438</PointLongitude>
                            <PointLatitude>75.497157</PointLatitude>
                        </Point>
                        <Point>
                            <PointLongitude>-94.570312</PointLongitude>
                            <PointLatitude>77.235074</PointLatitude>
                        </Point>
                        <Point>
                            <PointLongitude>-15.468750</PointLongitude>
                            <PointLatitude>74.959392</PointLatitude>
                        </Point>
                        <Point>
                            <PointLongitude>30.937500</PointLongitude>
                            <PointLatitude>64.472794</PointLatitude>
                        </Point>
                    </Boundary>
                </GPolygon>
            </Geometry>
        </HorizontalSpatialDomain>
        <GranuleSpatialRepresentation>GEODETIC</GranuleSpatialRepresentation>
    </Spatial>
</Collection>"
