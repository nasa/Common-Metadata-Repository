import sys
import logging

class ProviderFilter(logging.Filter):
    """
    Define a logging Global Filter so that the provider gets into the logs.
    This is important when many tasks run we can filter out the logs specifically for a provider.

    Args:
        The logging.Filter which is the provider for a log message.
    """

    def __init__(self, provider):
        """
        initializer that sets the provider for a set of logs.

        Args:
            the class itself and the provider 

        Returns:
            Nothing

        Exceptions:
            None
        """

        super().__init__()
        self.provider = provider

    def filter(self, record):
        """
        Ensures that the provider filter exits even if it hasn't been set yet.

        Args:
            the class itself and the message 

        Returns:
            True 

        Exceptions:
            None
        """
        # Ensure the attribute exists even if it wasn't set yet
        if not hasattr(record, 'provider'):
            record.provider = self.provider
        return True

# Configure the ROOT logger
def setup_logging(provider=None):
    """
    Sets up the logger with a format and a handler that makes sure the
    provider name exists for this the db es auditing tool.

    Args:
        the provider name

    Returns:
        Sets the global logger 

    Exceptions:
        None
    """

    # Not setting the name because it is the root logger
    root_logger = logging.getLogger()
    root_logger.setLevel(logging.INFO)

    # Create handler filter and formatter. The filter is used to add in the provider to the logs
    # so that each provider's logs can be filtered out in cloudwatch.
    handler = logging.StreamHandler(sys.stdout)
    provider_filter = ProviderFilter(provider)
    handler.addFilter(provider_filter)

    formatter = logging.Formatter('%(provider)s - %(name)s - %(message)s')
    handler.setFormatter(formatter)

    root_logger.addHandler(handler) 
