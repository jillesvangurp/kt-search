## Development status

KT Search is currently under heavy development. The legacy client module provides essentially all the functionality of the old client. It utilizes the newly extracted `search-dsl` module. Once all relevant functionality has been implemented, I'll release a 2.0 release (1.x being the legacy client).

The legacy client currently only works with Elasticsearch 7. However, beware that there may be some compatibility breaking changes before we release a stable release. Users currently using the old client should stick with the old version for now until we are ready to release an alpha/beta release. After that, you may use it as a migration path. 

My intention is to keep the legacy client as an option until I have all relevant functionality ported to the new client. The old repository will continue to exist for now. I am not planning to do any further maintenance on that, however. People are welcome to fork that project of course.

The future is going to be about using the pure kotlin multi-platform client. Of course, you may combine that with other clients; including the old RestHighLevel client or even the legacy client.

## Contributing

I'm tracking a few open issues and would appreciate any help of course. But until this stabilizes, reach out to me before doing a lot of work to create a pull request. Check the [here](CONTRIBUTING.md) for mote details.

## About this README

It's generated using the same `kotlin4example` library that I also used for the old client documentation. It's great for including little code snippets that are actually executing as part of the tests and therefore know  correct. Also, I can refactor and have the documentation change as well without breaking.