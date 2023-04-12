"""Migrates providers from legacy services REST API into new providers interface"""
import argparse
import json
import os
import xml.etree.ElementTree as ET
import re
import time
import string
import sys
import requests
import logging
import validators

# URL_ROOT = "https://cmr.sit.earthdata.nasa.gov"

url_map = { "sit": ".sit", "uat":".uat", "ops":"", "prod": ""}
token_map = { "sit": "SIT_SYSTEM_TOKEN", "uat":"UAT_SYSTEM_TOKEN", "ops":"OPS_SYSTEM_TOKEN", "prod": "PROD_SYSTEM_TOKEN"}

# access_token = os.environ.get('SIT_SYSTEM_TOKEN')

# used to validate the provider metadata if the ingest flag is on
CMR_LOCAL_INGEST_ENDPOINT = 'http://localhost:3002'

# To test local ingesting into CMR use the LOCAL system token
local_cmr_access_token = 'mock-echo-system-token'

# These are for the Enum values that we must set in the metadata field
DEFAULT_ORGANIZATION_ROLE = "PUBLISHER"
DEFAULT_CONTACT_ROLE = "PROVIDER MANAGEMENT"

provider_migration_log_file = open("./logs/provider_migration_log_file.txt","w", encoding="utf8")
 
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
            headers={"Authorization": access_token, "client-id": "legacy_migration", "User-Agent": "legacy_migration"},
            timeout=80)
        providers = response.json()
    except requests.exceptions.ConnectionError:
        print('Failed to retrieve legacy service providers')
        return None
    return providers

def ingest_provider(provider_metadata, provider_id):
    """Attempts to ingest metadata into CMR (locally)"""
    response = requests.post(f"{CMR_LOCAL_INGEST_ENDPOINT}/providers/",
                        data=provider_metadata,
                        headers={"Authorization": local_cmr_access_token, "client-id": "legacy_migration","User-Agent": "legacy_migration",
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

def parse_description_of_holdings(doh):
    """parses the doh to remove values that we do not need"""
    doh = doh.strip("\n")
    # remove the blank spaces at the end
    doh = doh.strip()
    ascii_chars = set(string.printable)
    # Remove non-ascii characters in some of hte provider doh
    doh = ''.join(filter(lambda x: x in ascii_chars, doh))
    print('this is description_of_holdings ' + doh + '\n')
    return doh

def add_consortiums(provider_id):
    """Return the consortiums for a specific provider"""
    # To retrieve consortiums for legacy providers we must parse the entire list of providers
    response = requests.get(f"{URL_ROOT}/ingest/providers",
            headers={"Authorization": access_token, "client-id": "legacy_migration", "User-Agent": "legacy_migration"},
            timeout=80)

    # parse the CMR json response
    providers = json.loads(response.text)

    for provider in providers:
        if(provider_id == provider.get("provider-id")):
            consortiums = provider.get("consortiums")
            if consortiums:
                logging.info("Provider" + provider_id + " is in these consortiums: " + consortiums )
                # The consortium list is a single string split by space in the legacy format
                consortium_list = consortiums.split()
                return consortium_list
            else:
                return None
    return None
def is_admin_group(group):
    """Looks for admin in the name of the group"""
    # if in the name of the text admin comes up anywhere then it is an admin group
    return bool(re.search('admin', group.lower()))

def convert_to_uri_value(url, provider_id):
    """Where applicable convert the url in to URI format. For example:
    www.example.com becomes https://example.com"""
    # Get rid of the ending whitespace that is in some of these
    url = url.strip()
    if url.endswith('/'):
        logging.warning('url contained / as an ending char')
        # If url ends with '/' strip out the ending ex. http://www.example.edu/
        url = url.strip('/')

    if validators.domain(url):
        # if this is a valid domain ending but, front of URI is missing
        # startsWith requires a tuple of strings if you want multiple ORed together
        if not url.startswith(('http://', 'https://')):
            url = 'https://' + url
        return url
    else:
        # We will not be able to migrate this provider since we'll want to manually check it's url
        logging.warning("We will not be able to migrate this provider it is not a valid URI providerID: " + str(provider_id))
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
            headers={"Authorization": access_token, "client-id": "legacy_migration", "User-Agent": "legacy_migration"},
            timeout=80)
            user_element = ET.fromstring(response.content)
            edl_username = user_element.find('username')
            
            logging.info('Retrieved edl-username for guid: ' + user_id)
            logging.info('The edl username: ' + edl_username.text)
            # So we don't get duplicate usernames if there are multiple 'Admin' groups in the provider
            if edl_username.text not in username_arr:
                username_arr.append(edl_username.text)
        except requests.exceptions.ConnectionError:
            print("Failed to Retrieve edl-username for guid" + str(user_id))
            # return None
    return username_arr

def get_groups_by_provider(owner_id):
    """Requests all groups that belong to a specific provider by passing the owner_id"""
    try:
        response = requests.get(f"{URL_ROOT}/legacy-services/rest/groups?owner_id={owner_id}",
        headers={"Authorization": access_token, "client-id": "legacy_migration", "User-Agent": "legacy_migration"})
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

        if addresses or contact_mechanisms or phone_contacts:
            contact_person['ContactInformation'] = {}

        # Addresses are optional if there were any, add them
        if addresses:
            # contact_person['ContactInformation'] = {}
            # We must wrap the contact person with contract information
            contact_person['ContactInformation']['Addresses'] = addresses

        # If emails or phones were provided add them to the metadata
        if contact_mechanisms:
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
    emptyAdmins = []

    all_the_groups_specific_provider = get_groups_by_provider(provider_owner_guid)
    if not all_the_groups_specific_provider:
        logging.warning('Could not the groups for this provider ' + str(provider_id))
        return emptyAdmins

    member_guids_specific_provider = get_all_provider_guids(all_the_groups_specific_provider)
    if not member_guids_specific_provider:
        logging.warning('Could not retrieve the member guids for ' + str(provider_id))
        return emptyAdmins

    admin_usernames = get_admin_usernames(member_guids_specific_provider)
    if not admin_usernames:
        logging.warning('Could not retrieve the member guids for ' + str(provider_id))
        return emptyAdmins

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

    print('Beginning to Migrate ' + str(len(json_response)) + ' Providers')

    for provider in json_response:
        provider_id = provider['provider']['provider_id']
        print(f"Migrating {provider_id} providers")
        description_of_holdings = provider['provider']['description_of_holdings']

        # prase the description of holding string
        description_of_holdings = parse_description_of_holdings(description_of_holdings)

        search_urls = provider['provider']['discovery_urls']
        org_name = provider['provider']['organization_name']
        contacts = provider['provider']['contacts']
        provider_owner_guid = provider['provider']['id']

        contact_persons = migrate_contact_persons(contacts)
        
        # Handle organizations
        organizations = migrate_organizations(search_urls,provider_id,org_name)

        # Handle the Administrators
        admin_usernames = migrate_administrators(provider_owner_guid,provider_id)

        if not admin_usernames:
            logging.info("We had to create an admin user for " + str(provider_id))
            default_admin_user = str(provider_id) + "_Admin"
            # Write this provider to the log file
            provider_migration_log_file.write("This Provider did not have admins: " + str(provider_id) + "\n")
            admin_usernames.append(default_admin_user)

        # Handle Consortiums that a provider may be apart of
        consortiums = add_consortiums(provider_id)

        # Create the new record to be ingested by the new CMR provider interface
        data = {}
        data["MetadataSpecification"]= metadata_specification
        data["ProviderId"] = provider_id
        data["DescriptionOfHolding"] = description_of_holdings
        data["Organizations"] = organizations
        data["Administrators"] = admin_usernames
        data["ContactPersons"] = contact_persons

        # Add consortiums, an optional field if the provider was in any
        if consortiums:
            data["Consortiums"] = consortiums

        # Dump the new metadata schema
        json_data = json.dumps(data,indent=4)

        # Write to the metadata file to a new metadata file for each provider
        provider_metadata_file = open("./providerMetadata/" + str(provider_id)+ "_metadata.json","w", encoding="utf8")
        provider_metadata_file.write(str(json_data))

        if INGEST_FLAG:
            ingest_provider(json_data, provider_id)
        provider_migration_count += 1

    # Migration Complete
    print(f'The total number of providers migrated {provider_migration_count}')

def main(cmr_env, ingest_flag, log_level):
    logging.basicConfig( level=log_level.upper() )
    # bad env bailout
    if not cmr_env in url_map:
        logging.warning("env must be sit, uat, or ops/prod")
        return None
    # Set global variables
    global INGEST_FLAG
    INGEST_FLAG = ingest_flag
    global URL_ROOT
    URL_ROOT = "https://cmr" + url_map.get(cmr_env) + ".earthdata.nasa.gov"
    global access_token
    access_token = os.environ.get(token_map.get(cmr_env))
    logging.info("This is the name of the token we used " + token_map.get(cmr_env))
    # without a valid system token we must bailout because we won't be authorized to read all providers
    # and we would not be able to validate against a local-cmr
    if not access_token:
        logging.warning("You do not have a value set for your " + token_map.get(cmr_env) +" env var")
        return None
    # Begin migration get start/end times
    start = time.time()
    logging.debug("Running in debug logging mode")
    logging.info("Running migration in the " + cmr_env + " env")
    migrate_providers()
    end = time.time()
    total_time = end - start
    print(f"The total time that the migration took {total_time} seconds")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument('-i', '--ingest', action='store_true', help='boolean to ingest the provider metadata records wont ingest by default if this arg is not included')
    parser.add_argument( '-log', '--logging', default='INFO', help='Provide logging level. Example --loglevel debug, default=warning')
    parser.add_argument( '-env', '--environment', default='sit', help='Specify which CMR env we should migrate from i.e. sit, uat, or ops/prod')
    args = parser.parse_args()
    ingest_flag = args.ingest
    log_level = args.logging
    cmr_env = args.environment
    main(cmr_env, ingest_flag, log_level)
