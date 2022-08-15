import nock from 'nock'

import { mockClient } from 'aws-sdk-client-mock'
import { SQSClient } from '@aws-sdk/client-sqs'

import bootstrapGremlinServer from '../handler'

import * as getEchoToken from '../../utils/cmr/getEchoToken'

const event = { Records: [{ body: '{}' }] }

beforeEach(() => {
  jest.clearAllMocks()
})

const sqsClientMock = mockClient(SQSClient)

describe('bootstrapGremlinServer handler', () => {
  beforeEach(() => {
    sqsClientMock.reset()
  })

  describe('When the response from CMR is an error', () => {
    test('throws an exception', async () => {
      nock(/local-cmr/)
        .get(/collections/)
        .reply(400, {
          errors: [
            'Parameter [asdf] was not recognized.'
          ]
        })

      jest.spyOn(getEchoToken, 'getEchoToken').mockImplementation(() => undefined)

      const response = await bootstrapGremlinServer(event)

      const { body, statusCode } = response

      expect(body).toBe('Indexing completed')

      expect(statusCode).toBe(200)
    })
  })

  describe('When the response from CMR is a single page', () => {
    test('processes a single page', async () => {
      nock(/local-cmr/)
        .get(/collections/)
        .reply(200, {
          feed: {
            updated: '2021-06-24T17:06:22.292Z',
            id: 'https://cmr.uat.earthdata.nasa.gov:443/search/collections.json?provider=LPDAAC_TS1&page_size=1&pretty=true',
            title: 'ECHO dataset metadata',
            entry: [{
              time_start: '1999-12-18T00:00:00.000Z',
              boxes: ['-83 -180 83 180'],
              online_access_flag: true,
              has_transforms: true,
              id: 'C1216257563-LPDAAC_TS1',
              associations: {
                services: ['S1224343297-LPDAAC_TS1']
              },
              browse_flag: false,
              has_temporal_subsetting: true,
              summary: "The ASTER Digital Elevation Model (DEM) product is generated using bands 3N (nadir-viewing) and 3B (backward-viewing) of an ASTER Level-1A image acquired by the Visible Near Infrared (VNIR) sensor. The VNIR subsystem includes two independent telescope assemblies that facilitate the generation of stereoscopic data. The Band-3 stereo pair is acquired in the spectral range of 0.78 and 0.86 microns with a base-to-height ratio of 0.6 and an intersection angle of about 27.7. There is a time lag of approximately one minute between the acquisition of the nadir and backward images. \r\n\r\nStarting in early summer of 2006, LP DAAC has implemented a new production software for efficiently creating quality DEMs. Based on an automated stereo-correlation method, the new software generates a relative DEM without any ground control points (GCPs). It utilizes the ephemeris and attitude data derived from both the ASTER instrument and the Terra spacecraft platform. The new ASTER DEM is a single-band product with 30-meters horizontal postings that is geodetically referenced to the UTM coordinate system, and referenced to the Earth's geoid using the EGM96 geopotential model. Compared to ASTER DEMs previously available from the LP DAAC, users likely will note some differences in ASTER DEMs produced by the new system, because DEMs now are produced automatically, with no manual editing. Larger water bodies are detected and typically have a single value, but they no longer are manually edited. Any failed areas, while infrequent, remain as they occur. Cloudy areas typically appear as bright regions, rather than as manually edited dark areas.\r\n\r\nThe accuracy of the new LP DAAC-produced DEMs will meet or exceed accuracy specifications set for the ASTER relative DEMs by the applicable Algorithm Theoretical Basis Document (ATBD). Users likely will find that the DEMs produced by the new DAAC system have accuracies approaching those specified in the ATBD for absolute DEMs. Validation testing has shown that DEMs produced by the new system frequently are more accurate than 25 meters RMSExyz.\r\n\r\nV003 data set release date: 2002-05-03\r\n\r\nData Set Characteristics: \r\nArea: ~60 km x 60 km \r\nImage Dimensions: 2500 rows x 2500 columns \r\nFile Size: ~25 MB \r\nUnits: \r\nProjection: Universal Transverse Mercator (UTM) \r\nData Format: GeoTIFF\r\nVgroup Data Fields: 1",
              coordinate_system: 'CARTESIAN',
              original_format: 'UMM_JSON',
              processing_level_id: '3',
              data_center: 'LPDAAC_TS1',
              has_spatial_subsetting: true,
              archive_center: 'LP DAAC',
              links: [{
                rel: 'http://esipfed.org/ns/fedsearch/1.1/data#',
                hreflang: 'en-US',
                href: 'http://reverb.echo.nasa.gov/reverb/'
              }]
            }]
          }
        }, { 'cmr-search-after': '["aaa", 123, 456]' })

      nock(/local-cmr/)
        .get(/collections/)
        .reply(200, {
          feed: {
            title: 'ECHO dataset metadata',
            entry: []
          }
        }, { 'cmr-search-after': '["bbb", 567, 890]' })

      jest.spyOn(getEchoToken, 'getEchoToken').mockImplementation(() => null)

      const response = await bootstrapGremlinServer(event)

      const { body, statusCode } = response

      expect(body).toBe('Indexing completed')
      expect(statusCode).toBe(200)
    })
  })

  describe('When the response from CMR is multiple pages', () => {
    test('processes multiple pages', async () => {
      nock(/local-cmr/)
        .get(/collections/)
        .reply(200, {
          feed: {
            updated: '2021-06-24T17:06:22.292Z',
            id: 'https://cmr.uat.earthdata.nasa.gov:443/search/collections.json?provider=LPDAAC_TS1&page_size=1&pretty=true',
            title: 'ECHO dataset metadata',
            entry: [{
              time_start: '1999-12-18T00:00:00.000Z',
              boxes: ['-83 -180 83 180'],
              online_access_flag: true,
              has_transforms: true,
              id: 'C1216257563-LPDAAC_TS1',
              associations: {
                services: ['S1224343297-LPDAAC_TS1']
              },
              browse_flag: false,
              has_temporal_subsetting: true,
              summary: "The ASTER Digital Elevation Model (DEM) product is generated using bands 3N (nadir-viewing) and 3B (backward-viewing) of an ASTER Level-1A image acquired by the Visible Near Infrared (VNIR) sensor. The VNIR subsystem includes two independent telescope assemblies that facilitate the generation of stereoscopic data. The Band-3 stereo pair is acquired in the spectral range of 0.78 and 0.86 microns with a base-to-height ratio of 0.6 and an intersection angle of about 27.7. There is a time lag of approximately one minute between the acquisition of the nadir and backward images. \r\n\r\nStarting in early summer of 2006, LP DAAC has implemented a new production software for efficiently creating quality DEMs. Based on an automated stereo-correlation method, the new software generates a relative DEM without any ground control points (GCPs). It utilizes the ephemeris and attitude data derived from both the ASTER instrument and the Terra spacecraft platform. The new ASTER DEM is a single-band product with 30-meters horizontal postings that is geodetically referenced to the UTM coordinate system, and referenced to the Earth's geoid using the EGM96 geopotential model. Compared to ASTER DEMs previously available from the LP DAAC, users likely will note some differences in ASTER DEMs produced by the new system, because DEMs now are produced automatically, with no manual editing. Larger water bodies are detected and typically have a single value, but they no longer are manually edited. Any failed areas, while infrequent, remain as they occur. Cloudy areas typically appear as bright regions, rather than as manually edited dark areas.\r\n\r\nThe accuracy of the new LP DAAC-produced DEMs will meet or exceed accuracy specifications set for the ASTER relative DEMs by the applicable Algorithm Theoretical Basis Document (ATBD). Users likely will find that the DEMs produced by the new DAAC system have accuracies approaching those specified in the ATBD for absolute DEMs. Validation testing has shown that DEMs produced by the new system frequently are more accurate than 25 meters RMSExyz.\r\n\r\nV003 data set release date: 2002-05-03\r\n\r\nData Set Characteristics: \r\nArea: ~60 km x 60 km \r\nImage Dimensions: 2500 rows x 2500 columns \r\nFile Size: ~25 MB \r\nUnits: \r\nProjection: Universal Transverse Mercator (UTM) \r\nData Format: GeoTIFF\r\nVgroup Data Fields: 1",
              coordinate_system: 'CARTESIAN',
              original_format: 'UMM_JSON',
              processing_level_id: '3',
              data_center: 'LPDAAC_TS1',
              has_spatial_subsetting: true,
              archive_center: 'LP DAAC',
              links: [{
                rel: 'http://esipfed.org/ns/fedsearch/1.1/data#',
                hreflang: 'en-US',
                href: 'http://reverb.echo.nasa.gov/reverb/'
              }]
            }]
          }
        }, { 'cmr-search-after': '["aaa", 123, 456]' })

      nock(/local-cmr/)
        .get(/collections/)
        .reply(200, {
          feed: {
            updated: '2021-06-24T17:06:22.292Z',
            id: 'https://cmr.uat.earthdata.nasa.gov:443/search/collections.json?provider=LPDAAC_TS1&page_size=1&pretty=true',
            title: 'ECHO dataset metadata',
            entry: [{
              time_start: '1999-12-18T00:00:00.000Z',
              boxes: [
                '-90 -180 90 180'
              ],
              online_access_flag: false,
              has_transforms: true,
              id: 'C100000-CMR',
              associations: {
                services: [
                  'S1224343297-LPDAAC_TS1'
                ]
              },
              browse_flag: false,
              has_temporal_subsetting: true,
              summary: 'The ASTER Expedited L1A Reconstructed Unprocessed Instrument Data is produced with the express purpose of providing the ASTER Science Team members and others, data of their particular interest in quick turn-around time from the moment the data are acquired. This is usually done to support on-going field calibration and validation efforts or to support emergency response to natural disasters when processed Level-1 data with minimum turn-around time would prove beneficial in initial damage or impact assessments. This data set is expected to be publicly available for a period of 30 days after which time it will be removed from the archive. This is done because the routinely processed (Production Data Set or PDS) version of this data set will be available from Japan in due course and available for search and order from the LP DAAC archives. ASTER Expedited Data Sets (EDS) serve the short-term requirements of a small group of scientists and fulfill immediate imagery needs during times of natural disasters.\n\nThe general product description details as described for the ASTER Level 1A Data Set - Reconstructed, unprocessed Instrument Data apply to the expedited data set with a few notable exceptions. These include:\n\n•       The Expedited Level-1A data set does not contain the VNIR 3B  (aft-viewing) band\n•       This data set does not have short-term calibration for the Thermal Infrared (TIR) sensor\n•       The registration quality of this data set is likely to be lower, and also vary from scene to scene \n\nThe Level-0 data downlink from the Tracking Data Relay Satellite System (TDRSS) at White Sands, NM is received at the EOS Data Operations System (EDOS) facility at the Goddard Space Flight Center (GSFC). Following some pre-processing, EDOS directly transmits that data (via the GSFC DAAC) to LP DAAC where the data are processed and the level-1 EDS is produced. The ASTER Level-1AE product contains reconstructed, unprocessed instrument digital data derived from the telemetry streams of the 3 telescopes at their respective ground resolutions: Visible Near Infrared (VNIR), 15 m, Shortwave Infrared (SWIR), 30 m, and Thermal Infrared (TIR), 90 m. There is no browse product provided for the expedited L1A data set.\n\nUsers are advised that ASTER SWIR data acquired from late April 2008 to the present exhibit anomalous saturation of values and anomalous striping. This effect is also present for some prior acquisition periods. Please refer to the ASTER SWIR User Advisory Document at https://lpdaac.usgs.gov/sites/default/files/public/aster/docs/ASTER_SWIR_User_Advisory_July%2018_08.pdf for more details.\n\nV003 data set release date: 2002-05-03\n\nData Set Characteristics: \nArea: ~60km x 60km \nImage Dimensions: \n  VNIR: 4200 rows x 4100 columns \n  SWIR: 2100 rows x 2048 columns \n  TIR: 700 rows x 700 columns \nFile Size: \n  VNIR (1, 2, 3N): ~49.3 MB \n  SWIR (4 through 9): ~25 MB \n  TIR (10 through 14): ~4.7 MB \n  Total: 85 Megabytes \nSpatial Resolution:\n  VNIR: 15m\n  SWIR: 30m\n  TIR: 90m\nUnits: Digital count\nProjection: Universal Transverse Mercator (UTM)\nData Format: HDF-EOS or GeoTIFF \nVgroup Data Fields: 14\n',
              coordinate_system: 'CARTESIAN',
              original_format: 'ECHO10',
              processing_level_id: '1A',
              data_center: 'LPDAAC_TS1',
              has_spatial_subsetting: true,
              archive_center: 'LPDAAC',
              links: [
                {
                  rel: 'http://esipfed.org/ns/fedsearch/1.1/documentation#',
                  hreflang: 'en-US',
                  href: 'http://dx.doi.org/10.5067/ASTER/AST_L1AE.003'
                },
                {
                  rel: 'http://esipfed.org/ns/fedsearch/1.1/documentation#',
                  hreflang: 'en-US',
                  href: 'http://reverb.echo.nasa.gov/reverb/#utf8=%E2%9C%93&spatial_map=satellite&spatial_type=rectangle&keywords=AST_L1AE3'
                },
                {
                  rel: 'http://esipfed.org/ns/fedsearch/1.1/documentation#',
                  hreflang: 'en-US',
                  href: 'https://lpdaac.usgs.gov/data_access/data_pool/'
                },
                {
                  rel: 'http://esipfed.org/ns/fedsearch/1.1/documentation#',
                  hreflang: 'en-US',
                  href: 'https://lpdaac.usgs.gov/'
                }
              ],
              dataset_id: 'ASTER Expedited L1A Reconstructed Unprocessed Instrument Data V003',
              title: 'ASTER Expedited L1A Reconstructed Unprocessed Instrument Data V003',
              platforms: [
                'AM-1'
              ],
              has_variables: true,
              organizations: [
                'LPDAAC',
                'LP DAAC User Services'
              ],
              short_name: 'AST_L1AE',
              updated: '2015-09-14T09:50:23.026Z',
              orbit_parameters: {},
              has_formats: false,
              version_id: '003'
            }]
          }
        }, { 'cmr-search-after': '["bbb", 567, 890]' })

      nock(/local-cmr/)
        .get(/collections/)
        .reply(200, {
          feed: {
            title: 'ECHO dataset metadata',
            entry: []
          }
        }, {})

      jest.spyOn(getEchoToken, 'getEchoToken').mockImplementation(() => null)

      const response = await bootstrapGremlinServer(event)

      const { body, statusCode } = response

      expect(body).toBe('Indexing completed')
      expect(statusCode).toBe(200)
    })
  })
})
