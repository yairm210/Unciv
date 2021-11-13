"""
Python helpers for handling wrapped Unciv objects.

Copied from a dictionary in unciv_lib.api.
"""

import unciv_lib.api

globals().update(unciv_lib.api.Expose)

del unciv_lib
