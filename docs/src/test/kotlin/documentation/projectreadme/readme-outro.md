## Development status

KT Search is currently under heavy development. The legacy client module provides essentially all the functionality of the old client. It utilizes the newly extracted `search-dsl` module.

The legacy client currently only works with Elasticsearch 7. However, beware that there may be some compatibility breaking changes before we release a stable release. Users currently using the old client should stick with the old version for now until we are ready to release an alpha/beta release. 

My intention is to keep the legacy client as an option until I have all relevant functionality ported to the new client. The old repository will continue to exist. I am not planning to do any further maintenance on that, however. People are welcome to fork the project of course.