# Jmeterplugin-radiussampler

This is a Jmeter plugin used to send Radius requests to the radius server. This comes handy in testing the radius server with jmeter.

The code has been forked from https://github.com/pavanmt/Jmeterplugin-radiussampler and includes some modfications made by me.

The original plugin caused failures when I tried to run a test for more than 3 minutes with an authentication rate of over 250 requests/sec. Jmeter would run out of UDP sockets to use in this case. It seems the original code would open a new socket for each single request and also leave it to the garbage collection to close the sockets later.

I improved the code by creating a single socket for each thread and reuse that socket during the whole test. So running with 100 threads would only use 100 sockets no matter for how long the test runs.
