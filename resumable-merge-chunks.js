var fs = require('fs'),
    path = require('path'),
    util = require('util'),
    Stream = require('stream').Stream,
    encryptor = require('file-encryptor');

var temporaryFolder = './tmp';
var outputFolder = './output';
var key = 'My_Super_Secret_Key';
var alg_options = {
    algorithm: 'aes256'
};



var getDecryptFilename = function(filename, chunkNumber) {
    
    return path.join(outputFolder + '/' + filename + '.' + chunkNumber);
}

var getEnryptFilename = function(filename, chunkNumber) {

    return path.join(temporaryFolder + '/' + filename + '.' + chunkNumber);
}

fs.readdir(temporaryFolder, function(err, files) {
    if (err) throw err;
    for (var i in files) {

        if (files[i].lastIndexOf('.1')>0) {
            var file = files[i].substr(0, files[i].lastIndexOf('.1'));
			decryptFile(file);
        }

    }
});

// Pipe chunks directly in to an existsing WritableStream
//   r.write(identifier, response);
//   r.write(identifier, response, {end:false});
//
//   var stream = fs.createWriteStream(filename);
//   r.write(identifier, stream);
//   stream.on('data', function(data){...});
//   stream.on('end', function(){...});
var write = function(filename, writableStream, options) {
    options = options || {};
    options.end = (typeof options['end'] == 'undefined' ? true : options['end']);

    // Iterate over each chunk
    var pipeChunk = function(number) {

        var decryptChunkFilename = getDecryptFilename(filename, number);

        fs.exists(decryptChunkFilename, function(exists) {

            if (exists) {
                // If the chunk with the current number exists,
                // then create a ReadStream from the file
                // and pipe it to the specified writableStream.
                var sourceStream = fs.createReadStream(decryptChunkFilename);
                sourceStream.pipe(writableStream, {
                    end: false
                });
                sourceStream.on('end', function() {
                    // When the chunk is fully streamed,
                    // jump to the next one
                    pipeChunk(number + 1);
                });
                sourceStream.on('error', function(err) {
                    // When the chunk is fully streamed,
                    // jump to the next one
                    console.log('error while writing file' + err);
                });
            } else {
                // When all the chunks have been piped, end the stream
                if (options.end) writableStream.end();
                if (options.onDone) options.onDone(filename);
            }
        });
    }
    pipeChunk(1);
}


var decryptFile = function(filename) {

    // Iterate over each chunk
    var pipeChunk = function(number) {

        var encryptChunkFilename = getEnryptFilename(filename, number);
        var decryptChunkFilename = getDecryptFilename(filename, number);

        fs.exists(encryptChunkFilename, function(exists) {
            if (exists) {
                encryptor.decryptFile(encryptChunkFilename, decryptChunkFilename, key, alg_options, function(err) {

                    if (err) console.log('error in encryptFile' + err);
                    // Do we have all the chunks?

                    fs.unlink(encryptChunkFilename, function(err) {
                        if (err) console.log('error in unlink' + err);
						pipeChunk(number + 1);
                    });
                });
            } else{
				var options = {};
				options.onDone = function(fileNameToBeDeleted){
					clean(fileNameToBeDeleted);
				}
				write(filename, fs.createWriteStream(outputFolder+'/'+filename), options);
			}
        });
    }

    pipeChunk(1);
}

var clean = function(filename) {
       
        // Iterate over each chunk
        var pipeChunkRm = function(number) {

             var decryptChunkFilename = getDecryptFilename(filename, number);

            // console.log('removing pipeChunkRm ', number, 'chunkFilename', chunkFilename);
            fs.exists(decryptChunkFilename, function(exists) {
                if (exists) {

                    // console.log('exist removing ', chunkFilename);
                    fs.unlink(decryptChunkFilename, function(err) {
                        //if (err && options.onError) options.onError(err);
                    });

                    pipeChunkRm(number + 1);

                } 
            });
        }
        pipeChunkRm(1);
    }
