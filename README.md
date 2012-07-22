scalerd
=======

A scalable image scaler, built on AMQP and Java.

To compile and run:
    cp scalerd.properties.example scalerd.properties
    $EDITOR scalerd.properties
    mvn package && java -jar target/scalerd-1.0-SNAPSHOT.jar 

To try the sample client:
    cd sample_client
    which bundle > /dev/null || sudo gem install bundler
    bundle install
    ./sample.rb file:///home/me/somefile.jpg 'rs|256x256!cr|256,256' | display -
