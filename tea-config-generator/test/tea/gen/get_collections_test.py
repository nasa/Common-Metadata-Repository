""" Test module """
from unittest import TestCase, mock
from tea.gen.get_collections import get_collection

#python -m unittest discover -s ./ -p '*_test.py'
class GetCollectionsTest(TestCase):
    """ Test class to test GetCollections """
    @mock.patch('tea.gen.get_collections.requests.get')
    def test_get_collections(self, mock_get):
        """ Tests get_collection """
        my_mock_response = mock.Mock(status_code=200)
        my_mock_response.json.return_value = {
          'DataLanguage' : 'eng',
          'AncillaryKeywords' : [ 'GHRSST', 'sea surface temperature', 'Level 4', \
            'SST', 'surface temperature', ' MUR', ' foundation SST', ' SST anomaly', ' anomaly' ],
          'CollectionCitations' : [ {
            'Creator' : 'JPL MUR MEaSUREs Project',
            'OnlineResource' : {
              'Linkage' : 'https://podaac.jpl.nasa.gov/MEaSUREs-MUR'
            },
            'Publisher' : 'JPL NASA',
            'Title' : 'GHRSST Level 4 MUR Global Foundation Sea Surface Temperature Analysis',
            'SeriesName' : 'GHRSST Level 4 MUR Global Foundation Sea Surface Temperature Analysis',
            'OtherCitationDetails' : 'JPL MUR MEaSUREs Project, JPL NASA, 2015-03-11, \
                GHRSST Level 4 MUR Global Foundation Sea Surface Temperature Analysis (v4.1)',
            'ReleaseDate' : '2015-03-11T00:00:00.000Z',
            'Version' : '4.1',
            'ReleasePlace' : 'Jet Propulsion Laboratory'
          } ],
          'AdditionalAttributes' : [ {
            'Name' : 'earliest_granule_start_time',
            'Description' : 'Earliest Granule Start Time for dataset.',
            'Value' : '2002-06-01T09:00:00.000Z',
            'DataType' : 'DATETIME'
          }, {
            'Name' : 'latest_granule_end_time',
            'Description' : 'Latest Granule Stop/End Time for dataset.',
            'Value' : '2021-01-25T09:00:00.000Z',
            'DataType' : 'DATETIME'
          }, {
            'Name' : 'Series Name',
            'Description' : 'Dataset citation series name',
            'Value' : 'GHRSST Level 4 MUR Global Foundation Sea Surface Temperature Analysis',
            'DataType' : 'STRING'
          }, {
            'Name' : 'Persistent ID',
            'Description' : 'Dataset Persistent ID',
            'Value' : 'PODAAC-GHGMR-4FJ04',
            'DataType' : 'STRING'
          } ],
          'SpatialExtent' : {
            'SpatialCoverageType' : 'HORIZONTAL',
            'HorizontalSpatialDomain' : {
              'Geometry' : {
                'CoordinateSystem' : 'CARTESIAN',
                'BoundingRectangles' : [ {
                  'NorthBoundingCoordinate' : 90.0,
                  'WestBoundingCoordinate' : -180.0,
                  'EastBoundingCoordinate' : 180.0,
                  'SouthBoundingCoordinate' : -90.0
                } ]
              },
              'ResolutionAndCoordinateSystem' : {
                'Description' : 'Projection Type: Cylindrical Lat-Lon, Projection Detail: \
                    Geolocation information included for each pixel',
                'GeodeticModel' : {
                  'HorizontalDatumName' : 'World Geodetic System 1984',
                  'EllipsoidName' : 'WGS 84',
                  'SemiMajorAxis' : 6378137.0,
                  'DenominatorOfFlatteningRatio' : 298.2572236
                },
                'HorizontalDataResolution' : {
                  'GenericResolutions' : [ {
                    'XDimension' : 0.01,
                    'YDimension' : 0.01,
                    'Unit' : 'Decimal Degrees'
                  } ]
                }
              }
            },
            'GranuleSpatialRepresentation' : 'CARTESIAN'
          },
          'DirectDistributionInformation' : {
            'Region' : 'us-west-2',
            'S3BucketAndObjectPrefixNames' : [ 'podaac-ops-cumulus-public/MUR-JPL-L4-GLOB-v4.1/', \
                'podaac-ops-cumulus-protected/MUR-JPL-L4-GLOB-v4.1/' ],
            'S3CredentialsAPIEndpoint' : 'https://archive.podaac.earthdata.nasa.gov/s3credentials',
            'S3CredentialsAPIDocumentationURL' : \
                'https://archive.podaac.earthdata.nasa.gov/s3credentialsREADME'
          }
        }
        mock_get.return_value = my_mock_response

        env = {'cmr-url': 'XXX'}
        token = 'EDL-XXX'
        concept_id = 'XXX'
        response = get_collection(env, token, concept_id)

        self.assertEqual(response['DataLanguage'], 'eng')
        self.assertEqual(response['DirectDistributionInformation']['Region'], 'us-west-2')
