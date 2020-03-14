# Jmeterplugin-radiussampler

This is a Jmeter plugin used to send Radius requests to the radius server. This comes handy in testing the radius server with jmeter.

The code has been forked from https://github.com/pavanmt/Jmeterplugin-radiussampler and includes some modfications made by me.

The original plugin caused test failures when I tried to sustain a rate of over 250 authentications per second. After about 3 minutes Jmeter would run out of UDP sockets to use in this case. It seems the original code allocates a new socket for every request and also leaves it to the garbage collection to close the sockets later.

I improved the code by creating a single socket for each thread and reuse that socket during the whole test. So running with 100 threads would only use 100 sockets no matter for how long the test runs.

## Installation

Copy the file `RadiusSampler.jar` to the `lib/ext` directory of your Jmeter installation. You can then add the _Radius Protocol Sampler_ to your testplan.
