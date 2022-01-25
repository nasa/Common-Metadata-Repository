from unittest import TestCase, main, mock
from Utils import *
#python -m unittest discover -s ./ -p '*Test.py'
class UtilsTest(TestCase):
    def test_add_to_dict(self):
        a = {}
        b = set()
        b.update([1,2])
        c = set()
        c.update(['a','b','c'])
        add_to_dict(a,b,c)
        self.assertEqual(len(a[1]), 3)
        self.assertEqual(len(a[2]), 3)

    def test_get_env(self):
        env = 'sit'
        env = get_env(env)
        self.assertEqual(env,'sit.')
        env = 'uat'
        env = get_env(env)
        self.assertEqual(env,'uat.')
        env = 'production'
        env = get_env(env)
        self.assertEqual(env,'')
        env = 'prod'
        env = get_env(env)
        self.assertEqual(env,'')

    def test_get_s3_prefixes(self):
        collection = {
          'DataLanguage' : 'eng',
          'AncillaryKeywords' : [ 'GHRSST', 'sea surface temperature', 'Level 4', 'SST', 'surface temperature', ' MUR', ' foundation SST', ' SST anomaly', ' anomaly' ],
          'CollectionCitations' : [ {
            'Creator' : 'JPL MUR MEaSUREs Project',
            'OnlineResource' : {
              'Linkage' : 'https://podaac.jpl.nasa.gov/MEaSUREs-MUR'
            },
            'Publisher' : 'JPL NASA',
            'Title' : 'GHRSST Level 4 MUR Global Foundation Sea Surface Temperature Analysis',
            'SeriesName' : 'GHRSST Level 4 MUR Global Foundation Sea Surface Temperature Analysis',
            'OtherCitationDetails' : 'JPL MUR MEaSUREs Project, JPL NASA, 2015-03-11, GHRSST Level 4 MUR Global Foundation Sea Surface Temperature Analysis (v4.1)',
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
                'Description' : 'Projection Type: Cylindrical Lat-Lon, Projection Detail: Geolocation information included for each pixel',
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
            'S3BucketAndObjectPrefixNames' : [ 'podaac-ops-cumulus-public/MUR-JPL-L4-GLOB-v4.1/', 'podaac-ops-cumulus-protected/MUR-JPL-L4-GLOB-v4.1/' ],
            'S3CredentialsAPIEndpoint' : 'https://archive.podaac.earthdata.nasa.gov/s3credentials',
            'S3CredentialsAPIDocumentationURL' : 'https://archive.podaac.earthdata.nasa.gov/s3credentialsREADME'
          }
        }

        s3_prefixes = get_s3_prefixes(collection)
        self.assertEqual(s3_prefixes[0], 'podaac-ops-cumulus-public/MUR-JPL-L4-GLOB-v4.1/')
        self.assertEqual(s3_prefixes[1], 'podaac-ops-cumulus-protected/MUR-JPL-L4-GLOB-v4.1/')

if __name__ == '__main__':
    main()
