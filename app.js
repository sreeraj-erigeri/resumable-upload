var cluster = require('cluster');

if (cluster.isMaster) {

    process.env.TMPDIR = '/apps/ds01/tmp/';
    // Count the machine's CPUs
    var cpuCount = require('os').cpus().length;

    // Create a worker for each CPU
    for (var i = 0; i < cpuCount; i += 1) {
        cluster.fork();
    }
    console.log('Master cluster setting up ' + cpuCount + ' workers...');

    cluster.on('online', function(worker) {
        console.log('Worker ' + worker.process.pid + ' is online');
    });

    cluster.on('exit', function(worker) {

        // Replace the dead worker,
        // we're not sentimental
        console.log('Worker %d died', worker.id);
        cluster.fork();

    });

} else {

    var express = require('express');
    var resumable = require('./resumable-node.js')('./tmp/', './output/');
    var app = express();
    var multipart = require('connect-multiparty');

    var allowCrossDomain = function(req, res, next) {
        res.header('Access-Control-Allow-Origin', '*');
        res.header('Access-Control-Allow-Methods', 'GET,PUT,POST,DELETE');
        res.header('Access-Control-Allow-Headers', 'Content-Type');
        next();
    }

    app.use(allowCrossDomain);
    // Host most stuff in the public folder
    app.use(express.static(__dirname + '/public'));

    app.use(multipart());

    // Handle uploads through Resumable.js
    app.post('/upload', function(req, res) {

        // console.log(req);

        resumable.post(req, function(status, filename, original_filename, identifier) {
            // console.log('POST', status, filename, original_filename, identifier);

            res.send(status, {
                // NOTE: Uncomment this funciton to enable cross-domain request.
                'Access-Control-Allow-Origin': '*'
            });
        });
    });

    // Handle cross-domain requests
    // NOTE: Uncomment this funciton to enable cross-domain request.

    app.options('/upload', function(req, res) {
        res.send(true, {
            'Access-Control-Allow-Origin': '*'
        }, 200);
    });


    // Handle status checks on chunks through Resumable.js
    app.get('/upload', function(req, res) {
        resumable.get(req, function(status, filename, original_filename, identifier) {
            res.send((status == 'found' ? 200 : 404), status);
        });
    });

    console.log(3000);
    app.listen(3000);
}