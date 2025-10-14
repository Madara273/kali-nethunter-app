import sys
import platform


def hello(name: str) -> str:
    return f"Hello, {name}! Python {platform.python_version()} on {platform.system()}"


def get_info() -> dict:
    return {
        "python_version": sys.version.split()[0],
        "platform": platform.platform(),
        "implementation": platform.python_implementation(),
    }

