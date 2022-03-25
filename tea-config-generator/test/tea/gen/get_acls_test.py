"""
ACL tests
"""

from unittest import TestCase, mock
from tea.gen.get_acls import get_acl, get_acls

#python -m unittest discover -s ./ -p '*_test.py'
class GetAclsTest(TestCase):
    "Do all the ACL test"

    @mock.patch('tea.gen.get_acls.requests.get')
    def test_get_acls(self, mock_get):
        "Get ACL check"
        my_mock_response = mock.Mock(status_code=200)
        my_mock_response.json.return_value = {
          'hits' : 2,
          'took' : 81,
          'items' : [ {
            'concept_id' : 'ACL1218667606555-CMR',
            'revision_id' : 9,
            'identity_type' : 'Catalog Item',
            'name' : 'All Collections',
            'location' : 'https://cmr.uat.earthdata.nasa.gov:443/' +
                'access-control/acls/ACL1218667606555-CMR'
          }, {
            'concept_id' : 'ACL1218667506777-CMR',
            'revision_id' : 9,
            'identity_type' : 'Catalog Item',
            'name' : 'All Granules',
            'location' : 'https://cmr.uat.earthdata.nasa.gov:443/' +
                'access-control/acls/ACL1218667506777-CMR'
          } ]}
        mock_get.return_value = my_mock_response

        provider = 'XXX'
        env = {'cmr-url': 'XXX'}
        token = 'EDL-XXX'
        response = get_acls(env,provider,token)

        self.assertEqual(response[0]['location'],
            'https://cmr.uat.earthdata.nasa.gov:443/access-control/acls/ACL1218667606555-CMR')
        self.assertEqual(response[1]['location'],
            'https://cmr.uat.earthdata.nasa.gov:443/access-control/acls/ACL1218667506777-CMR')

    @mock.patch('tea.gen.get_acls.requests.get')
    def test_get_acl(self, mock_get):
        "test getting one acl"
        my_mock_response = mock.Mock(status_code=200)
        my_mock_response.json.return_value = {
          'group_permissions' : [ {
            'permissions' : [ 'read' ],
            'user_type' : 'guest'
          }, {
            'permissions' : [ 'read' ],
            'user_type' : 'registered'
          }, {
            'permissions' : [ 'read' ],
            'group_id' : 'AG1222486916-CMR'
          }, {
            'permissions' : [ 'read' ],
            'group_id' : 'AG1236456866-CMR'
          }, {
            'permissions' : [ 'read', 'order' ],
            'group_id' : 'AG1216375421-SCIOPS'
          }, {
            'permissions' : [ 'read', 'order' ],
            'group_id' : 'AG1216375422-SCIOPS'
          }, {
            'permissions' : [ 'read', 'order' ],
            'group_id' : 'AG1215550981-CMR'
          } ],
          'catalog_item_identity' : {
            'name' : 'All Collections',
            'provider_id' : 'SCIOPS',
            'granule_applicable' : 'false',
            'collection_applicable' : 'true',
            'collection_identifier' : {
              'concept_ids' : [ 'C1240032460-SCIOPS'],
              'entry_titles' : [ '2000 Pilot Environmental Sustainability Index (ESI)' ]
            }
          },
          'legacy_guid' : 'F4E6573E-B97E-8BBC-A553-37DCE8F28D9D'
        }
        mock_get.return_value = my_mock_response

        acl_url = 'XXX'
        token = 'EDL-XXX'
        response = get_acl({}, acl_url, token)

        self.assertEqual(response['group_permissions'][3]['group_id'], 'AG1236456866-CMR')
        self.assertEqual(response['group_permissions'][5]['group_id'], 'AG1216375422-SCIOPS')
