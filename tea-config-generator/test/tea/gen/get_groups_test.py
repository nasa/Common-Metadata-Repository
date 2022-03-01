""" Test module """
from unittest import TestCase, mock
from tea.gen.get_groups import get_group, get_groups

#python -m unittest discover -s ./ -p '*_test.py'
class GetGroupsTest(TestCase):
    """ Test class to test GetGroups """
    @mock.patch('tea.gen.get_groups.requests.get')
    def test_get_groups(self, mock_get):
        """ Tests get_groups """
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
            'granule_applicable' : False,
            'collection_applicable' : True,
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

        response = get_groups({}, acl_url, token)
        self.assertEqual(response[2]['group_id'], 'AG1222486916-CMR')
        self.assertEqual(response[4]['group_id'], 'AG1216375421-SCIOPS')

    @mock.patch('tea.gen.get_groups.requests.get')
    def test_get_group(self, mock_get):
        """ Tests get_group """
        my_mock_response = mock.Mock(status_code=200)
        my_mock_response.json.return_value = {
              'name' : 'Science Coordinators',
              'description' : 'List of science coordinators for metadata curation \
                in docBUILDER and CMR API',
              'legacy_guid' : 'E825D79F-A110-A251-7110-97208B5C2987',
              'provider_id' : 'SCIOPS',
              'num_members' : 4
            }
        mock_get.return_value = my_mock_response

        env = {'cmr-url': 'XXX'}
        group_id = 'XXX'
        token = 'EDL-XXX'

        response = get_group(env, group_id, token)
        self.assertEqual(response['name'], 'Science Coordinators')
        self.assertEqual(response['legacy_guid'], 'E825D79F-A110-A251-7110-97208B5C2987')
