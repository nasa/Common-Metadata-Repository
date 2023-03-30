import requests
import json 
import os
import xml.etree.ElementTree as ET
import re

url_root = "https://cmr.sit.earthdata.nasa.gov"
access_token = os.environ.get('SIT_SYSTEM_TOKEN')
cmr_ingest_endpoint = "https://cmr.sit.earthdata.nasa.gov/ingest"

def ingest_provider(provider_metadata):
    response = requests.post(f"{url_root}/ingest/providers",
                        data=json.dumps(provider_metadata),
                        headers={"Authorization": access_token, "User-id": "legacy_migration",
                        "Content-Type": "application/json"}
                        )
    print("Ingest response from provider endpoint", response.content)
    return response

# TODO parsing
def retrieve_provider_acls(provider_id):
    # define the payload to be passed to access control
    payload_dict = {'provider':provider_id, 'include-full-acl': 'true', 'page_size':'2000'}

    response = requests.post(f"{url_root}/access-control/acls/search",
    headers={"Authorization": access_token, "User-id": "legacy_migration"},
    data = payload_dict)
    print("provider acls response", response.content)
    return response


def get_all_legacy_service_groups():
    resp = requests.get(f"{url_root}/legacy-services/rest/groups/",
    headers={"Authorization": access_token, "User-id": "legacy_migration"})
    root = ET.fromstring(resp.content)
    return root

def get_provider_group_id(groups_root_node, provider_id):
    passed_provider_name = str(provider_id) + ' Administrator Group'
    # resp = requests.get(f"{url_root}/legacy-services/rest/groups/",
    # headers={"Authorization": access_token, "User-id": "legacy_migration"})
    # # TODO: If the error is NOT 200 then branch out
    # root = ET.fromstring(resp.content)
    provider_group_id = ""
    for reference in groups_root_node.findall('reference'):
        provider_name = reference.find('name').text
        if (provider_name == passed_provider_name):
            provider_group_id = reference.find('id').text
            print('This is the group id which matched our provider_id admin group', provider_group_id)
    return provider_group_id


def get_provider_admin_member_guids(provider_group_id):
    member_guid_arr = []
    # If we didn't get anything back from the groups return empty array
    if (len(provider_group_id) < 1):
        return member_guid_arr

    group_resp = requests.get(f"{url_root}/legacy-services/rest/groups/{provider_group_id}",
    headers={"Authorization": access_token, "User-id": "legacy_migration"})
    # Create ET element
    group_root = ET.fromstring(group_resp.content)

    for member_guids in group_root.findall('member_guids'):
        member_guid_list = member_guids.findall('member_guid')
    
    print(len(member_guid_list))
    for member_guid in member_guid_list:
        print(member_guid.text)
        member_guid_arr.append(member_guid.text)
    return member_guid_arr


def is_admin_group(group):
    # if in the name of the text admin comes up anywhere then it is an admin group
    return bool(re.search('admin', group.lower()))


# TODO complete this and make it work nicely
# this may be mute
# def get_provider_ids_matching_the_use_case(legacy_providers, provider_group_id):
#     member_guid_arr = []
#     # If we didn't get anything back from the groups return empty array
#     if (len(provider_group_id) < 1):
#         return member_guid_arr

#     group_resp = requests.get(f"{url_root}/legacy-services/rest/groups/{provider_group_id}",
#     headers={"Authorization": access_token, "User-id": "legacy_migration"})
#     # Create ET element
#     group_root = ET.fromstring(group_resp.content)

#     # for member_guids in group_root.findall('member_guids'):
#     #     member_guid_list = member_guids.findall('member_guid')
    
#     current_owner_provider_guid = group_root.find('owner_provider_guid')


#     print('The owner provider guid for this provider', current_owner_provider_guid)
    
#     for provider in legacy_providers.findall('provider'):
#         # Id is referring to dataset-id but, in the response it's called id
#         id = provider.find('id').text
#         if (current_owner_provider_guid == id):
#             provider_group_id = provider.find('provider_id').text
#             print('This is the provider-id which matched our dataset-id' +  current_owner_provider_guid + ' ' + provider_group_id)

#     return member_guid_arr
    

def get_admin_usernames(member_guid_arr):
    username_arr = []
    if (len(member_guid_arr) < 1):
        return username_arr
    #TODO: can we do this in one request instead of iterating over each username
    for user_id in member_guid_arr:
        print("User_id being passed in request", user_id)
        user_resp = requests.get(f"{url_root}/legacy-services/rest/users/{user_id}",
        headers={"Authorization": access_token, "User-id": "legacy_migration"})
        user_element = ET.fromstring(user_resp.content)
        edl_username = user_element.find('username')
        print('The edl username', edl_username.text)
        username_arr.append(edl_username.text)
    return username_arr

def retrieve_legacy_providers():
    # Requests all of the providers on legacy-services and their metadata which we will be
    # re-ingesting into the new providers interface
    response = user_resp = requests.get(f"{url_root}/legacy-services/rest/providers.json",
        headers={"Authorization": access_token, "User-id": "legacy_migration"})
    json_response = response.json()
    return json_response

# response = requests.get('https://cmr.sit.earthdata.nasa.gov/legacy-services/rest/providers.json')

# retrieve_provider_acls("CMR_ONLY")


# response = requests.get(f"{url_root}/legacy-services/rest/providers.json",
#     headers={"Authorization": access_token, "User-id": "legacy_migration"})

# json_response = response.json()
# TODO this is using the new api where we pass the owner-id
def get_groups_by_provider(owner_id):
    provider_groups = requests.get(f"{url_root}/legacy-services/rest/groups?owner_id={owner_id}",
    headers={"Authorization": access_token, "User-id": "legacy_migration"})
    print('these are the providers we specified with owner-id', provider_groups.content)
    return provider_groups


def get_all_provider_guids(provider_groups):
    member_guid_arr = []
    # big_member_guid_list = []
    # If we didn't get anything back from the groups return empty array
    # if (len(provider_group_id) < 1):
    #     return member_guid_arr

    # group_resp = requests.get(f"{url_root}/legacy-services/rest/groups/{provider_group_id}",
    # headers={"Authorization": access_token, "User-id": "legacy_migration"})
    # Create ET element
    groups_root = ET.fromstring(provider_groups.content)
    for group in groups_root.findall('group'):
        if(is_admin_group(group.find('name').text)):
            print('🚀 This group was admin: ', group.find('name').text)
            member_guids = group.find('member_guids')
            member_guid_list = member_guids.findall('member_guid')
            for member_guid in member_guid_list:
                print('🚀 ~ file: migrate.py:160 ~ member_guid:', member_guid.text)
                member_guid_arr.append(member_guid.text)
    return member_guid_arr


json_response = retrieve_legacy_providers()

f = open("demofile2.txt", "a")
provider_migration_report = open("provider_migration_report.txt", "a")

full_response = open("fullResponse.txt", "a")

full_response.write(str(json_response))
provider_id_list = []

# The new record
data = {}
data['key'] = 'value'
json_data = json.dumps(data)

# Metadata specification for ingesting providers
# Note that since this script is only meant to be run during the legacy services migration/transition
# It should not be expected the the version or schema directories would change
metadata_specification = {
               "Name": "provider",
               "Version": "1.0.0",
               "URL": "https://cdn.earthdata.nasa.gov/schemas/provider/v1.0.0"}

# TODO: Pull out all of the data that we want from the response

# TODO have a default role per organization
# TODO put this line back
provider_migration_count = 0
# all_groups = get_all_legacy_service_groups()
# TODO: parse the groups for the owner_provider_guid and match that to the id in the provider
print('Migrating' + str(len(json_response)) + ' Providers')

for provider in json_response:
    provider_id = provider['provider']['provider_id']
    description_of_holdings = provider['provider']['description_of_holdings']
    # Remove any "\" characters in the description of holdings string
    # TODO This still needs to work to remove characters correctly
    # description_of_holdings=description_of_holdings.replace("\\","")
    re.sub('[^A-Za-z0-9]+', '', description_of_holdings)
    print('Filtered description of holdings', description_of_holdings)
    search_urls = provider['provider']['discovery_urls']
    org_name = provider['provider']['organization_name']
    contacts = provider['provider']['contacts']
    provider_owner_guid = provider['provider']['id']
    # print('value for provider owner guid', provider_owner_guid)
    
    # The roles in the provider
    # "required": ["Roles", "LastName"]
    # we can do: addresses 
    contact_persons = []
    for contact in contacts:
        contact_person = {}
        contact_person_roles_arr = []
        # New provider schema allows for multiple roles per contact old one did not
        contact_person_roles_arr.append(contact['role'])
        # TODO: For now I am hardcoding this because we need to change the schema
        # contact_person['Roles'] = contact_person_roles_arr
        contact_person["Roles"] = ["PROVIDER MANAGEMENT"]


        contact_person['LastName'] = contact['last_name']
        # Add to contact person to contact_persons arr
        contact_persons.append(contact_person)
        #TODO we need to handle if there are more fields provided


    
    # Handle organizations
    organizations = []
    for url in search_urls:
        organization = {}
        organization["ShortName"] = provider_id
        organization["LongName"] = org_name
        organization["URLValue"] = url
        # TODO we are hardcoding this as well because it is not provided by ACL's
        # Consider passing a default argument for the organization roles
        organization["Roles"]= ["PUBLISHER"]
        # TODO: if there are any roles add that to the role part we will actually pass this
        # TOOD: as an argument
        organizations.append(organization)

   # TODO handle the Administrators
    # print("Provider-id value to be passed", provider_id)
    # provider_group_id = get_provider_group_id(all_groups, provider_id)
    # provider_member_guids = get_provider_admin_member_guids(provider_group_id)
    # adminUsernames = get_admin_usernames(provider_member_guids)

    all_the_groups_specific_provider = get_groups_by_provider(provider_owner_guid)

    member_guids_specific_provider = get_all_provider_guids(all_the_groups_specific_provider)
    # print('members of groups for provider ' + str(provider_id) + " with an owner-id of " + str(provider_owner_guid)
    # + str(member_guids_specific_provider))
    adminUsernames = get_admin_usernames(member_guids_specific_provider)
    # New way to handle admins

    # TODO remove but, for now add myself as an admin to everything to test ingest
    if(len(adminUsernames) < 1):
        adminUsernames.append("defaultAdmin")

  # Create the new record to be ingested by the new CMR provider interface
    data = {}
    data["MetadataSpecification"]= metadata_specification
    data["ProviderId"] = provider_id
    data["DescriptionOfHolding"] = description_of_holdings
    data["Organizations"] = organizations
    data["Administrators"] = adminUsernames
    data["ContactPersons"] = contact_persons
        

         
    # In all cases on PROD it was found that the provider_id and Shortname were always the same
    # TODO: double check that this is the case
    
    # In the new provider interface we are going to make that assumption enforced by the schema
    # data['Organizations']['ShortName'] = provider_id
    # # longname in the new schema and organization_name field in legacy are equivalent
    # data['Organizations']['LongName'] = org_name
    # # TODO: For every value in discovery_urls create a record for organization
    # data['Organizations']['URLValue'] = URLValue = search_urls[0]
     #TODO: figure out consortiums
    json_data = json.dumps(data)

    # Write to the metadata file
    provider_metadata_file = open("./providerMetadata/"+str(provider_id)+"_metadata.json","w")
    provider_metadata_file.write(str(json_data))
    # ingest_provider(json_data)
    provider_id_list.append(json_data)
    f.write(str(json_data))
    provider_migration_count += 1
    # TODO only try to ingest one for now

# f.write(str(provider_id_list))
# get the total count of providers

provider_migration_report.write(('The total number of providers migrated' + str(provider_migration_count)))

# TODO get the provider administrators level group from provider

# TODO: Pass this to the ingest API for the providers

# {'provider': {'contacts': [{'address': {'city': 'Boulder', 'country': 'United States', 'state': 'CO', 'street1': 'National Snow & Ice Data Center', 'street2': 'CIRES, 449 UCB', 'street3': 'University of Colorado', 'us_format': True, 'zip': '80309'}, 'email': 'nsidc@nsidc.org', 'first_name': 'Doug', 'last_name': 'Fowler', 'phones': [{'number': '+1 303.492.6199', 'phone_number_type': 'BUSINESS'}], 'role': 'data manager'}], 'description_of_holdings': "We support research into our world's frozen realms: the snow, ice, glacier, frozen ground, and climate interactions that make up Earth's cryosphere. Scientific data, whether taken in the field or relayed from satellites orbiting Earth, form the foundation for the scientific research that informs the world about our planet and our climate systems.", 'discovery_urls': ['http://nsidc.org/'], 'id': '3A6B6195-AF07-893F-C8AC-1C75FDFDC4D8', 'organization_name': 'National Snow and Ice Data Center', 'provider_id': 'NSIDCV0', 'provider_types': ['CATALOG_REST', 'DATA'], 'rest_only': False}}


# TODO: do consortiums get returned from legacy services