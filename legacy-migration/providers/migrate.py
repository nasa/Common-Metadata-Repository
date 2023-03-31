"""Migrates providers from legacy services REST API into new providers interface"""
import json
import os
import xml.etree.ElementTree as ET
import re
import time
import sys
import requests

URL_ROOT = "https://cmr.sit.earthdata.nasa.gov"
# LOCAL_CMR = "http://localhost:3002/providers/"

access_token = os.environ.get('SIT_SYSTEM_TOKEN')
CMR_INGEST_ENDPOINT = "https://cmr.sit.earthdata.nasa.gov/ingest"

# To test local ingesting into CMR
local_cmr_access_token = os.environ.get('localHeader')
# These are for the Enum values that we must set in the metadata field
DEFAULT_ORGANIZATION_ROLE = "SERVICE PROVIDER"
DEFAULT_CONTACT_ROLE = "TECHNICAL CONTACT"
# provider_migration_report = open("provider_migration_report.txt", "w")


# TODO: Make this an optional thing
def ingest_provider(provider_metadata, providerId):
    """Attempts to ingest metadata into CMR (locally)"""
    response = requests.post("http://localhost:3002/providers/",
                        data=provider_metadata,
                        headers={"Authorization": local_cmr_access_token, "User-id": "legacy_migration",
                        "Content-Type": "application/json"},
                        timeout=60
                        )
    print("Successfully Ingested metadata into CMR ", response.status_code)
    # TODO trying to figure out which error code to use
    if response.status_code >= 300:
        print("Failed to ingest a record: ", providerId)
        ingest_error_log = open("./logs/"+str(provider_id)+"_error","w")
        ingest_error_log.write('Error for provider ' + provider_id + ' ingesting ' + 'due to ' +
        str(response.content))
    return response.content


def is_admin_group(group):
    """Looks for admin in the name of the group"""
    # if in the name of the text admin comes up anywhere then it is an admin group
    return bool(re.search('admin', group.lower()))


def get_admin_usernames(member_guid_arr):
    """Parses the list of member_guids and retrieves the edl usernames though an API call"""
    username_arr = []
    if (len(member_guid_arr) < 1):
        return username_arr
    #TODO: can we do this in one request instead of iterating over each username
    for user_id in member_guid_arr:
        print("User_id being passed in request", user_id)
        try:
            response = requests.get(f"{URL_ROOT}/legacy-services/rest/users/{user_id}",
            headers={"Authorization": access_token, "User-id": "legacy_migration"},
            timeout=80)
            user_element = ET.fromstring(response.content)
            edl_username = user_element.find('username')
            print("Retrieved edl-username for guid", user_id)
            print('The edl username', edl_username.text)
            username_arr.append(edl_username.text)
        except requests.exceptions.ConnectionError:
            print("Failed to Retrieve edl-username for guid" + str(user_id))
            return None
    return username_arr

def retrieve_legacy_providers():
    """Requests all of the providers on legacy-services and their metadata which we will be
    re-ingesting into the new providers interface"""
    try:
        response = requests.get(f"{URL_ROOT}/legacy-services/rest/providers.json",
            headers={"Authorization": access_token, "User-id": "legacy_migration"},
            timeout=80)
        providers = response.json()
    except requests.exceptions.ConnectionError:
        print('Failed to retrieve legacy service providers')
        return None
    return providers

# TODO this is using the new api where we pass the owner-id
def get_groups_by_provider(owner_id):
    """Requests all groups that belong to a specific provider by passing the owner_id"""
    try:
        response = requests.get(f"{URL_ROOT}/legacy-services/rest/groups?owner_id={owner_id}",
        headers={"Authorization": access_token, "User-id": "legacy_migration"},
        timeout=80)
    except requests.exceptions.ConnectionError:
        print("Failed to retrieve groups for the provider with owner guid " + str(owner_id))
        return None
    return response

def get_all_provider_guids(provider_groups):
    """Parses all of the groups of a provider, retrieves administrator member guids"""
    member_guid_arr = []
    groups_root = ET.fromstring(provider_groups.content)
    for group in groups_root.findall('group'):
        if(is_admin_group(group.find('name').text)):
            # print('🚀 This group was admin: ', group.find('name').text)
            member_guids = group.find('member_guids')
            member_guid_list = member_guids.findall('member_guid')
            for member_guid in member_guid_list:
                # print('🚀 ~ file: migrate.py:180 ~ member_guid:', member_guid.text)
                member_guid_arr.append(member_guid.text)
    return member_guid_arr

def parse_addresses(provider_contact):
    """Parses address between old-style format into new schema"""
    # TODO Addresses are not always in the contacts object
    address = provider_contact["address"]
    city = address["city"]
    country = address["country"]
    address_metadata = { 'City': city,
    'Country': country
    }
    # print('🚀 us format value ',  address["us_format"])
    # TODO we need to handle this boolean string better
    if address["us_format"] is True:
        state = address["state"]
        postal_code = address["zip"]
        # set values for new metadata document if available
        address_metadata["StateProvince"] = state
        address_metadata["PostalCode"] = postal_code
    # AddressMetadata.append()
    # # address = contact["Addresses"]
    #TODO: Look for street *
    # TODO worked on this last
    # street_list = any(key.startswith("street") for key in address)
    provider_street_keys = {key: val for key, val in address.items()
       if key.startswith('street')}
    print('🚀 ~ file: migrate.py:115 ~ street_list:', str(provider_street_keys.values()))

    # If street addresses were in the record add them to the new metadata
    if len(provider_street_keys.values()) > 0:
        street_addresses = []
        for street in provider_street_keys.values():
            street_addresses.append(street)
        address_metadata["StreetAddresses"] = street_addresses

    # address.findall('street*')
    # print('🚀 this is the address metadata on this document ', str(address_metadata))
    return address_metadata

def parse_emails(provider_contact):
    """Parses email fields between old-style format into new schema"""
    email = provider_contact["email"]
    # print('🚀 this is the email metadata on this document ', str(email))
    return email

def parse_phones(provider_contact):
    """Parses phone number fields between old-style format into new schema"""
    phone_numbers = []
    phones = provider_contact["phones"]
    for phone in phones:
        # Some do not have the number field but, have a phone
        if phone.get('number'):
            phone_numbers.append(phone['number'])
    
    # print('🚀 The phone numbers in the document ', str(phone_numbers))
    return phone_numbers

# Main
PROVIDER_MIGRATION_COUNT = 0
start = time.time()
json_response = retrieve_legacy_providers()

# Bailout if request for providers failed
if json_response is None:
    sys.exit("Failed to Retrieve any providers")

# TODO remove me I am for testing
f = open("demofile2.txt", "a")
provider_migration_report = open("provider_migration_report.txt", "a")

full_response = open("fullResponse.txt", "a")

full_response.write(str(json_response))
provider_id_list = []

# Metadata specification for ingesting providers
# Note that since this script is only meant to be run during the legacy services migration/transition
# It should not be expected the the version or schema directories would change
metadata_specification = {
               "Name": "provider",
               "Version": "1.0.0",
               "URL": "https://cdn.earthdata.nasa.gov/schemas/provider/v1.0.0"}

print('Beginning to Migrate' + str(len(json_response)) + ' Providers')

for provider in json_response:
    provider_id = provider['provider']['provider_id']
    print('Migrating ' + str(provider_id) + ' provider')
    description_of_holdings = provider['provider']['description_of_holdings']
    # Remove any "\" characters in the description of holdings string
    # TODO This still needs to work to remove characters correctly
    # description_of_holdings=description_of_holdings.replace("\\","")
    re.sub('[^A-Za-z0-9]+', '', description_of_holdings)
    # print('Filtered description of holdings', description_of_holdings)
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
        contact_mechanisms = []
        addresses = []
        phone_contacts = []
        # New provider schema allows for multiple roles per contact old one did not
        contact_person_roles_arr.append(contact['role'])
        # TODO: For now I am hardcoding this because we need to change the schema
        # contact_person['Roles'] = contact_person_roles_arr
        contact_person["Roles"] = [DEFAULT_CONTACT_ROLE]
        contact_person['LastName'] = contact['last_name']
        # TODO adding first name
        contact_person['FirstName'] = contact['first_name']

    # TODO handling address each old style record will only contain one address
        if contact.get("address"):
            address = parse_addresses(contact)
            addresses.append(address)

        if contact.get("email"):
            email_value = parse_emails(contact)
            email_contact = {"Type": "Email", "Value": email_value}
            contact_mechanisms.append(email_contact)

        if contact.get("phones"):
            # TODO be careful here if there is more than one more
            # Todo we may not be able to get clarity for this with the schema
            phone_numbers = parse_phones(contact)
            for phone_number in phone_numbers:
                phone_contact = {"Type": "Telephone", "Value": phone_number}
                # Use the same contact mechanism object as emails
                contact_mechanisms.append(phone_contact)

        if len(addresses) > 0 or len(contact_mechanisms) > 0 or len(phone_contacts) > 0 :
            contact_person['ContactInformation'] = {}

        # Addresses are optional if there were any, add them
        if len(addresses) > 0:
            # contact_person['ContactInformation'] = {}
            # We must wrap the contact person with contract information
            contact_person['ContactInformation']['Addresses'] = addresses

        # If emails or phones were provided add them to the metadata
        if len(contact_mechanisms) > 0:
            contact_person['ContactInformation']['ContactMechanisms'] = contact_mechanisms

        # Add to contact person to contact_persons arr
        contact_persons.append(contact_person)
        #TODO we need to handle if there are more fields provided


    # Handle organizations
    organizations = []
    for url in search_urls:
        organization = {}
        # In all cases on PROD it was found that the provider_id and Shortname were always the same this
        # will be enforced in the new schema
        organization["ShortName"] = provider_id
        organization["LongName"] = org_name
        organization["URLValue"] = url
        # TODO we are hardcoding this as well because it is not provided by ACL's
        # Consider passing a default argument for the organization roles
        organization["Roles"] = [DEFAULT_ORGANIZATION_ROLE]
        # TODO: if there are any roles add that to the role part we will actually pass this
        organizations.append(organization)

   # Handle the Administrators
    all_the_groups_specific_provider = get_groups_by_provider(provider_owner_guid)
    member_guids_specific_provider = get_all_provider_guids(all_the_groups_specific_provider)
    if member_guids_specific_provider is None:
        print('🚀 Could not retrieve the member guids for ', provider_id)
        
    adminUsernames = get_admin_usernames(member_guids_specific_provider)

    # TODO remove but, for now add default as an admin to everyone without admins to test ingest
    if len(adminUsernames) < 1:
        adminUsernames.append("defaultAdmin")

  # Create the new record to be ingested by the new CMR provider interface
    data = {}
    data["MetadataSpecification"]= metadata_specification
    data["ProviderId"] = provider_id
    data["DescriptionOfHolding"] = description_of_holdings
    data["Organizations"] = organizations
    data["Administrators"] = adminUsernames
    data["ContactPersons"] = contact_persons

    # Dump the new metadata schema
    json_data = json.dumps(data)


    # Write to the metadata file to a new metadata file for each provider
    provider_metadata_file = open("./providerMetadata/"+str(provider_id)+"_metadata.json","w")
    provider_metadata_file.write(str(json_data))

    # TODO trying to ingest the record
    ingest_provider(json_data, provider_id)

    provider_id_list.append(json_data)
    # f.write(str(json_data))
    PROVIDER_MIGRATION_COUNT += 1
    # TODO only try to ingest one for now

# f.write(str(provider_id_list))
# get the total count of providers
end = time.time()
# print(f"Total DataQualitySummary data migration time spent: {end - start} seconds, Succeeded: {success_count}, Failed: {failure_count}")
provider_migration_report.write(('The total number of providers migrated ' +
str(PROVIDER_MIGRATION_COUNT) + '\n'))
provider_migration_report.write(('The total time that the migration took ' +
str(end - start)+ '\n'))


# TODO: Pass this to the ingest API for the providers

# {'provider': {'contacts': [{'address': {'city': 'Boulder', 'country': 'United States', 'state': 'CO', 'street1': 'National Snow & Ice Data Center', 'street2': 'CIRES, 449 UCB', 'street3': 'University of Colorado', 'us_format': True, 'zip': '80309'}, 'email': 'nsidc@nsidc.org', 'first_name': 'Doug', 'last_name': 'Fowler', 'phones': [{'number': '+1 303.492.6199', 'phone_number_type': 'BUSINESS'}], 'role': 'data manager'}], 'description_of_holdings': "We support research into our world's frozen realms: the snow, ice, glacier, frozen ground, and climate interactions that make up Earth's cryosphere. Scientific data, whether taken in the field or relayed from satellites orbiting Earth, form the foundation for the scientific research that informs the world about our planet and our climate systems.", 'discovery_urls': ['http://nsidc.org/'], 'id': '3A6B6195-AF07-893F-C8AC-1C75FDFDC4D8', 'organization_name': 'National Snow and Ice Data Center', 'provider_id': 'NSIDCV0', 'provider_types': ['CATALOG_REST', 'DATA'], 'rest_only': False}}


# TODO: do consortiums get returned from legacy services