import unittest
import json
from unittest.mock import patch, MagicMock
from search import Search

class TestSearch(unittest.TestCase):
    def setUp(self):
        self.search = Search()
        self.test_input = {
            "CollectionReference": {"EntryTitle": "Sentinel-1 Interferograms (BETA)"},
            "DataGranule": {
                "DayNightFlag": "Unspecified",
                "Identifiers": [
                    {
                        "Identifier": "S1-GUNW-A-R-014-tops-20141030_20141018-152945-24166N_21930N-PP-f419-v2_0_3",
                        "IdentifierType": "ProducerGranuleId"
                    }
                ],
                "ProductionDateTime": "2020-06-25T00:58:15.561Z"
            },
            "GranuleUR": "S1-GUNW-A-R-014-tops-20141030_20141018-152945-24166N_21930N-PP-f419-v2_0_3",
            "MetadataSpecification": {
                "URL": "https://cdn.earthdata.nasa.gov/umm/granule/v1.6.6",
                "Name": "UMM-G",
                "Version": "1.6.6"
            }
        }
        self.search.get_public_search_url = lambda: "https://cmr.earthdata.nasa.gov/search/"

    @patch('search.os.getenv')
    def test_get_url_from_parameter_store(self, mock_getenv):
        mock_getenv.return_value = 'http://localhost:3003/'
        self.search.get_url_from_parameter_store()
        self.assertEqual(self.search.url, 'http://localhost:3003/')

    @patch('search.requests.get')
    def test_get_concept(self, mock_get):
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.text = json.dumps(self.test_input)
        mock_get.return_value = mock_response

        self.search.url = 'http://test-url.com'
        self.search.token = 'test-token'

        result = self.search.get_concept('test-concept-id')
        self.assertEqual(result, json.dumps(self.test_input))

    def test_get_producer_granule_id(self):
        result = self.search.get_producer_granule_id(self.test_input)
        expected = "S1-GUNW-A-R-014-tops-20141030_20141018-152945-24166N_21930N-PP-f419-v2_0_3"
        self.assertEqual(result, expected)

    def test_create_notification_message_body(self):
        # Prepare test input
        result_dict = {
            "concept_id": "G1200484356-PROV",
            "revision_id": "39",
            "metadata": {
                "GranuleUR": "SWOT_L2_HR_PIXC_578_020_221L_20230710T223456_20230710T223506_PIA1_01",
                "DataGranule": {
                    "Identifiers": [
                        {
                            "IdentifierType": "ProducerGranuleId",
                            "Identifier": "SWOT_L2_HR_PIXC_578_020_221L_20230710T223456_20230710T223506_PIA1_01.nc"
                        }
                    ]
                }
            }
        }

        # Call the method
        result = self.search.create_notification_message_body(result_dict)

        # Parse the result
        result_json = result

        # Assert the results
        self.assertEqual(result_json["concept-id"], "G1200484356-PROV")
        self.assertEqual(result_json["granule-ur"], "SWOT_L2_HR_PIXC_578_020_221L_20230710T223456_20230710T223506_PIA1_01")
        self.assertEqual(result_json["producer-granule-id"], "SWOT_L2_HR_PIXC_578_020_221L_20230710T223456_20230710T223506_PIA1_01.nc")
        self.assertEqual(result_json["location"], "https://cmr.earthdata.nasa.gov/search/concepts/G1200484356-PROV/39")

    def test_create_notification_message_body_no_producer_granule_id(self):
        # Prepare test input without producer granule id
        result_dict = {
            "concept_id": "G1200484356-PROV",
            "revision_id": "39",
            "metadata": {
                "GranuleUR": "SWOT_L2_HR_PIXC_578_020_221L_20230710T223456_20230710T223506_PIA1_01",
                "DataGranule": {
                    "Identifiers": []
                }
            }
        }

        # Call the method
        result = self.search.create_notification_message_body(result_dict)

        # Parse the result
        result_json = result

        # Assert the results
        self.assertEqual(result_json["concept-id"], "G1200484356-PROV")
        self.assertEqual(result_json["granule-ur"], "SWOT_L2_HR_PIXC_578_020_221L_20230710T223456_20230710T223506_PIA1_01")
        self.assertEqual(result_json["location"], "https://cmr.earthdata.nasa.gov/search/concepts/G1200484356-PROV/39")
        self.assertNotIn("producer-granule-id", result_json)

    @patch('search.os.getenv')
    def test_get_public_search_url_from_parameter_store(self, mock_getenv):
        mock_getenv.return_value = 'http://public-search.com/'
        self.search.get_public_search_url_from_parameter_store()
        self.assertEqual(self.search.public_search_url, 'http://public-search.com/')

    @patch('search.os.getenv')
    def test_get_token_from_parameter_store(self, mock_getenv):
        mock_getenv.return_value = 'test-token'
        self.search.get_token_from_parameter_store()
        self.assertEqual(self.search.token, 'test-token')

    def test_get_url(self):
        self.search.url = 'http://test-url.com'
        result = self.search.get_url()
        self.assertEqual(result, 'http://test-url.com')

    def test_get_public_search_url(self):
        result = self.search.get_public_search_url()
        self.assertEqual(result, 'https://cmr.earthdata.nasa.gov/search/')

    def test_get_token(self):
        self.search.token = 'test-token'
        result = self.search.get_token()
        self.assertEqual(result, 'test-token')

    @patch('search.Search.get_concept')
    @patch('search.Search.get_public_search_url')
    def test_process_message(self, mock_get_public_search_url, mock_get_concept):
        # Setup
        search = Search()
        
        # Mock the get_public_search_url method
        mock_get_public_search_url.return_value = "https://cmr.earthdata.nasa.gov/search/"

        # Mock the get_concept method
        mock_get_concept.return_value = """
        <result>
            <concept-id>G1200484356-ERICH_PROV</concept-id>
            <revision-id>1</revision-id>
        </result>
        {
            "GranuleUR": "SWOT_L2_HR_PIXC_578_020_221L_20230710T223456_20230710T223506_PIA1_01",
            "DataGranule": {
                "Identifiers": [
                    {
                        "IdentifierType": "ProducerGranuleId",
                        "Identifier": "SWOT_L2_HR_PIXC_578_020_221L_20230710T223456_20230710T223506_PIA1_01.nc"
                    }
                ]
            }
        }
        """

        # Input message
        input_message = '{"concept-id": "G1200484356-ERICH_PROV"}'

        # Expected output
        expected_output = {"concept-id": "G1200484356-ERICH_PROV", "granule-ur": "SWOT_L2_HR_PIXC_578_020_221L_20230710T223456_20230710T223506_PIA1_01", "location": "https://cmr.earthdata.nasa.gov/search/concepts/G1200484356-ERICH_PROV/1", "producer-granule-id": "SWOT_L2_HR_PIXC_578_020_221L_20230710T223456_20230710T223506_PIA1_01.nc"}

        # Call the method
        result = search.process_message(input_message)

        # Assert
        self.assertEqual(result, expected_output)
        mock_get_concept.assert_called_once_with("G1200484356-ERICH_PROV")
        mock_get_public_search_url.assert_called_once()

if __name__ == '__main__':
    unittest.main()