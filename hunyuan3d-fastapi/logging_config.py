import logging
import sys
from pathlib import Path


def setup_logging(log_level: str = "INFO", log_file: str = None):

    log_format = "%(asctime)s | %(levelname)8s | %(name)s | %(message)s"

    handlers = [logging.StreamHandler(sys.stdout)]

    if log_file:
        Path(log_file).parent.mkdir(exist_ok=True)
        handlers.append(logging.FileHandler(log_file))

    logging.basicConfig(
        level=getattr(logging, log_level.upper()),
        format=log_format,
        handlers=handlers
    )

    logging.getLogger("transformers").setLevel(logging.WARNING)
    logging.getLogger("diffusers").setLevel(logging.WARNING)
    logging.getLogger("torch").setLevel(logging.WARNING)

    return logging.getLogger(__name__)
