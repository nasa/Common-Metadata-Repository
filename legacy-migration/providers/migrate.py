"""Migrates providers from legacy services REST API into new providers interface"""
import argparse
import json
import os
import xml.etree.ElementTree as ET
import re
import time
import sys
import requests
import logging
import validators

URL_ROOT = "https://cmr.sit.earthdata.nasa.gov"

access_token = os.environ.get('SIT_SYSTEM_TOKEN')
CMR_INGEST_ENDPOINT = "https://cmr.sit.earthdata.nasa.gov/ingest"
CMR_LOCAL_ingest_ENDPOINT = 'http://localhost:3002'

# To test local ingesting into CMR
local_cmr_access_token = os.environ.get('localHeader')
# These are for the Enum values that we must set in the metadata field
# TODO I want more clarity on how to handle these organization and contact roles since there is not an enum accessible substitute in the old format
DEFAULT_ORGANIZATION_ROLE = "SERVICE PROVIDER"
DEFAULT_CONTACT_ROLE = "TECHNICAL CONTACT"
 
# Metadata specification for ingesting providers
# It should not be expected this version or schema directories will change
# within the lifecycle of this migration
metadata_specification = {
            "Name": "provider",
            "Version": "1.0.0",
            "URL": "https://cdn.earthdata.nasa.gov/schemas/provider/v1.0.0"}

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

def ingest_provider(provider_metadata, provider_id):
    """Attempts to ingest metadata into CMR (locally)"""
    response = requests.post(f"{CMR_LOCAL_ingest_ENDPOINT}/providers/",
                        data=provider_metadata,
                        headers={"Authorization": local_cmr_access_token, "User-id": "legacy_migration",
                        "Content-Type": "application/json"},
                        timeout=60
                        )
    if response.status_code >= 300:
        print("Failed to ingest a record for the provider : ", provider_id)
        ingest_error_log = open("./logs/"+str(provider_id)+"_error","w" ,encoding="utf8")
        ingest_error_log.write('Error for provider ' + provider_id + ' ingesting ' + 'due to ' +
        str(response.content))
    else:
        print("Successfully Ingested metadata into CMR ", response.status_code)
    return response.content

def is_admin_group(group):
    """Looks for admin in the name of the group"""
    # if in the name of the text admin comes up anywhere then it is an admin group
    return bool(re.search('admin', group.lower()))

def convert_to_uri_value(url, provider_id):
    """Where applicable convert the url in to URI format. For example:
    www.example.com becomes https://example.com"""
    # startsWith requires a tuple of strings if you want multiple ORed together
    if validators.domain(url):
        # if this is a valid domain ending but, front of URI is missing
        if not url.startswith(('http://', 'https://')):
            url = 'https://' + url
        return url
    else:
        # We will not be able to migrate this provider since we'll want to manually check it's url
        logging.warning('This url could not be coerced into a valid URI format ' + url + ' for provider ' + provider_id)
        return url

def get_admin_usernames(member_guid_arr):
    """Parses the list of member_guids and retrieves the edl usernames though an API call"""
    username_arr = []
    if not member_guid_arr:
        return username_arr
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
            logging.info('This group was admin:' + group.find('name').text)
            member_guids = group.find('member_guids')
            member_guid_list = member_guids.findall('member_guid')
            for member_guid in member_guid_list:
                member_guid_arr.append(member_guid.text)
    return member_guid_arr

def parse_addresses(provider_contact):
    """Parses address between old-style format into new schema"""
    address = provider_contact["address"]
    city = address["city"]
    country = address["country"]
    address_metadata = { 
    'City': city,
    'Country': country
    }
    # print('🚀 us format value ',  address["us_format"])
    if address["us_format"] is True:
        state = address["state"]
        postal_code = address["zip"]
        # set values for new metadata document if available
        address_metadata["StateProvince"] = state
        address_metadata["PostalCode"] = postal_code

    # Look for street *
    provider_street_keys = {key: val for key, val in address.items()
       if key.startswith('street')}
    # If street addresses were in the record add them to the new metadata
    if len(provider_street_keys.values()) > 0:
        street_addresses = []
        for street in provider_street_keys.values():
            street_addresses.append(street)
        address_metadata["StreetAddresses"] = street_addresses
    logging.info('This is the address metadata on this document ' + str(address_metadata))
    return address_metadata

def parse_emails(provider_contact):
    """Parses email fields between old-style format into new schema"""
    email = provider_contact["email"]
    logging.info('This is the email metadata on this document ' + str(email))
    return email

def parse_phones(provider_contact):
    """Parses phone number fields between old-style format into new schema"""
    phone_numbers = []
    phones = provider_contact["phones"]
    for phone in phones:
        # Some do not have the number field but, have a phone
        if phone.get('number'):
            phone_numbers.append(phone['number'])
    logging.info('These are the phone-numbers metadata on this document ' + str(phone_numbers))
    return phone_numbers

def migrate_contact_persons(contacts):
    """Parses contact person's fields between old-style format into new schema"""
    contact_persons = []
    for contact in contacts:
        contact_person = {}
        contact_person_roles_arr = []
        contact_mechanisms = []
        addresses = []
        phone_contacts = []
        # New provider schema allows for multiple roles per contact old-format did not
        contact_person_roles_arr.append(contact['role'])
        contact_person["Roles"] = [DEFAULT_CONTACT_ROLE]
        contact_person['LastName'] = contact['last_name']
        contact_person['FirstName'] = contact['first_name']

    # The old-style provider record contains only a single address value
        if contact.get("address"):
            address = parse_addresses(contact)
            addresses.append(address)

        if contact.get("email"):
            email_value = parse_emails(contact)
            email_contact = {"Type": "Email", "Value": email_value}
            contact_mechanisms.append(email_contact)

        if contact.get("phones"):
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
        return contact_persons

def migrate_organizations(search_urls, provider_id,org_name):
    """Parses organization's fields between old-style format into new schema"""
    organizations = []
    for url in search_urls:
        organization = {}
        # In all cases on PROD it was found that
        # the provider_id and Shortname were always the same this
        # will be enforced in the new schema
        organization["ShortName"] = provider_id
        organization["LongName"] = org_name
        # Try to massage mostly valid urls into the required schema format
        url = convert_to_uri_value(url, provider_id)
        organization["URLValue"] = url
        # Consider passing a default argument for the organization roles
        organization["Roles"] = [DEFAULT_ORGANIZATION_ROLE]
        organizations.append(organization)
    return organizations

def migrate_administrators(provider_owner_guid, provider_id):
    """Parses admin's fields between old-style format into new schema"""
    all_the_groups_specific_provider = get_groups_by_provider(provider_owner_guid)
    member_guids_specific_provider = get_all_provider_guids(all_the_groups_specific_provider)
    if member_guids_specific_provider is None:
        logging.debug('Could not retrieve the member guids for ' + str(provider_id))
    admin_usernames = get_admin_usernames(member_guids_specific_provider)
    return admin_usernames

def migrate_providers():
    """Migrate the providers"""
    # pull ingest_flag var from the cmd arg
    global ingest_flag

    provider_migration_count = 0
    json_response = retrieve_legacy_providers()

    # Bailout if request for providers failed
    if json_response is None:
        sys.exit("Failed to Retrieve any providers")

    print('Beginning to Migrate' + str(len(json_response)) + ' Providers')

    for provider in json_response:
        provider_id = provider['provider']['provider_id']
        print(f"Migrating {provider_id} providers")
        description_of_holdings = provider['provider']['description_of_holdings']
        
        # Remove any "\" characters in the description of holdings string
        # TODO item of discussion is the length that we will allow for DOH
        # description_of_holdings=description_of_holdings.replace("\\","")
        re.sub('\w', '', description_of_holdings)
        # print('Filtered description of holdings', description_of_holdings)
        search_urls = provider['provider']['discovery_urls']
        org_name = provider['provider']['organization_name']
        contacts = provider['provider']['contacts']
        provider_owner_guid = provider['provider']['id']

        contact_persons = migrate_contact_persons(contacts)
        
        # Handle organizations
        organizations = migrate_organizations(search_urls,provider_id,org_name)

        # Handle the Administrators
        admin_usernames = migrate_administrators(provider_owner_guid,provider_id)

        # TODO A unit of discussion for how to handle cases where admin users is not provided
        if not admin_usernames:
            logging.debug("We had to create an admin user for " + str(provider_id))
            admin_usernames.append("defaultAdmin")

    # Create the new record to be ingested by the new CMR provider interface
        data = {}
        data["MetadataSpecification"]= metadata_specification
        data["ProviderId"] = provider_id
        data["DescriptionOfHolding"] = description_of_holdings
        data["Organizations"] = organizations
        data["Administrators"] = admin_usernames
        data["ContactPersons"] = contact_persons

        # Dump the new metadata schema
        json_data = json.dumps(data,indent=4)

        # Write to the metadata file to a new metadata file for each provider
        provider_metadata_file = open("./providerMetadata/" + str(provider_id)+"_metadata.json","w", encoding="utf8")
        provider_metadata_file.write(str(json_data))

        if INGEST_FLAG:
            ingest_provider(json_data, provider_id)

        #TODO will remove below useful for debugging
        # provider_id_list.append(json_data)
        provider_migration_count += 1

    # Migration Complete
    print(f'The total number of providers migrated {provider_migration_count}')

def main(ingest_flag):
    # ingest_flag = args.ingest
    global INGEST_FLAG
    INGEST_FLAG = ingest_flag
    # Begin migration get start/end times
    start = time.time()
    migrate_providers()
    end = time.time()
    total_time = end - start
    print(f"The total time that the migration took {total_time}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument('-i', '--ingest', action='store_true', help='ingest the provider metadata records')
    args = parser.parse_args()
    ingest_flag = args.ingest
    main(ingest_flag)
