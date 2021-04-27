const { process, driver } = require('gremlin');

exports.initializeGremlinConnection = () => {
    const traversal = process.AnonymousTraversalSource.traversal;
    const DriverRemoteConnection = driver.DriverRemoteConnection;

    return traversal().withRemote(new DriverRemoteConnection('ws://localhost:8182/gremlin'));
}