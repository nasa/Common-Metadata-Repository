import nock from 'nock'

import { mockClient } from 'aws-sdk-client-mock'
import { SQSClient, SendMessageBatchCommand } from '@aws-sdk/client-sqs'

import * as chunkArray from '../../chunkArray'

import { fetchPageFromCMR } from '../fetchPageFromCMR'

const OLD_ENV = process.env

beforeEach(() => {
  jest.clearAllMocks()

  // Manage resetting ENV variables
  jest.resetModules()
  process.env = { ...OLD_ENV }
  delete process.env.NODE_ENV
})

afterEach(() => {
  // Restore any ENV variables overwritten in tests
  process.env = OLD_ENV
})

const sqsClientMock = mockClient(SQSClient)

describe('fetchPageFromCMR', () => {
  beforeEach(() => {
    process.env.IS_LOCAL = 'false'

    sqsClientMock.reset()
  })

  test('Empty page', async () => {
    const mockedBody = {
      feed: {
        title: 'ECHO dataset metadata',
        entry: []
      }
    }

    nock(/local-cmr/).get(/search/).reply(200, mockedBody)

    await fetchPageFromCMR({
      searchAfter: null,
      token: null,
      gremlinConnection: global.testGremlinConnection,
      providerId: 'PROV1'
    })

    expect(sqsClientMock.calls()).toHaveLength(0)
  })

  test('Single result', async () => {
    const mockedBody = {
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
    }

    nock(/local-cmr/).get(/search/).reply(200, mockedBody, { 'cmr-search-after': '["aaa", 123, 456]' })
    nock(/local-cmr/).get(/search/).reply(200, {
      feed: {
        title: 'ECHO dataset metadata',
        entry: []
      }
    }, {})

    await fetchPageFromCMR({
      searchAfter: null,
      token: null,
      gremlinConnection: global.testGremlinConnection,
      providerId: null
    })

    const sendMessageBatchMock = sqsClientMock.commandCalls(SendMessageBatchCommand)

    expect(sqsClientMock.calls()).toHaveLength(1)

    expect(sendMessageBatchMock[0].args[0].input).toEqual({
      QueueUrl: 'http://example.com/collectionIndexQueue',
      Entries: [{
        Id: 'C1216257563-LPDAAC_TS1',
        MessageBody: JSON.stringify({
          action: 'concept-update',
          'concept-id': 'C1216257563-LPDAAC_TS1'
        })
      }]
    })
  })

  test('Single result with mocked ECHO token', async () => {
    const mockedBody = {
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
    }

    nock(/local-cmr/).get(/search/).reply(200, mockedBody, { 'cmr-search-after': '["aaa", 123, 456]' })

    nock(/local-cmr/).get(/search/).reply(200, {
      feed: {
        title: 'ECHO dataset metadata',
        entry: []
      }
    }, { 'cmr-search-after': '["bbb", 567, 890]' })

    await fetchPageFromCMR({
      searchAfter: 'fake-search-after',
      token: 'SUPER-SECRET-TOKEN',
      gremlinConnection: global.testGremlinConnection,
      providerId: null
    })

    const sendMessageBatchMock = sqsClientMock.commandCalls(SendMessageBatchCommand)

    expect(sqsClientMock.calls()).toHaveLength(1)

    expect(sendMessageBatchMock[0].args[0].input).toEqual({
      QueueUrl: 'http://example.com/collectionIndexQueue',
      Entries: [{
        Id: 'C1216257563-LPDAAC_TS1',
        MessageBody: JSON.stringify({
          action: 'concept-update',
          'concept-id': 'C1216257563-LPDAAC_TS1'
        })
      }]
    })
  })

  test('Invalid concept-id', async () => {
    const mockedBody = {
      errors: ["Invalid concept_id [C1234-PROV1]! I can't believe you've done this"]
    }

    nock(/local-cmr/).get(/search/).reply(400, mockedBody)

    await fetchPageFromCMR({
      searchAfter: null,
      token: null,
      gremlinConnection: global.testGremlinConnection,
      providerId: null
    })

    expect(sqsClientMock.calls()).toHaveLength(0)
  })

  test('Function catches and handles errors', async () => {
    const mockedBody = {
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
    }

    nock(/local-cmr/).get(/search/).reply(200, mockedBody, { 'cmr-search-after': '["aaa", 123, 456]' })
    nock(/local-cmr/).get(/search/).reply(200, {
      feed: {
        title: 'ECHO dataset metadata',
        entry: []
      }
    }, { 'cmr-search-after': '["bbb", 567, 890]' })

    const consoleMock = jest.spyOn(console, 'log')
    const errorResponse = jest.spyOn(chunkArray, 'chunkArray').mockImplementationOnce(() => {
      throw new Error('Oh no! I sure hope this exception is handled')
    })

    await fetchPageFromCMR({
      searchAfter: null,
      token: null,
      gremlinConnection: global.testGremlinConnection,
      providerId: null
    })

    expect(consoleMock).toBeCalledWith('Could not complete request due to error: Error: Oh no! I sure hope this exception is handled')
    expect(errorResponse).toBeCalledTimes(1)
  })
})
